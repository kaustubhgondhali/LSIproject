package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto;
import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.export.PdfPictures;
import com.lordsai.lsi.export.PdfRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The existing fee receipt as a PDF file (the email attachment) and as a picture of that same PDF
 * (pasted into the student's WhatsApp chat). Built from the stored receipt snapshot
 * ({@link ReceiptDto}) with the same fields, order and number formatting as the on-screen receipt;
 * nothing about the receipt is recomputed.
 */
@Service
public class AcadReceiptPdfService {

    public record ReceiptFile(String filename, byte[] content) {
    }

    /** One line of the receipt's payment history, formatted as printed. */
    public record HistoryRow(String installment, String paymentNo, String date, String mode, String amount, boolean current) {
    }

    /** One discount line of the receipt, formatted as printed. */
    public record DiscountRow(String label, String amount) {
    }

    private static final String TEMPLATE = "receipt/fee-receipt";
    /** Sharp enough to read on a phone, and still within the size WhatsApp sends without shrinking. */
    private static final float PICTURE_DPI = 180f;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final PdfRenderer pdfRenderer;
    private final AcadPaymentService paymentService;

    public AcadReceiptPdfService(PdfRenderer pdfRenderer, AcadPaymentService paymentService) {
        this.pdfRenderer = pdfRenderer;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public ReceiptFile render(AcadReceipt receipt) {
        return render(paymentService.toDto(receipt));
    }

    public ReceiptFile render(ReceiptDto r) {
        Map<String, Object> model = new HashMap<>();
        model.put("r", r);
        LocalDate date = r.paymentDate() != null ? r.paymentDate() : r.issuedAt() == null ? null : r.issuedAt().atZone(INDIA).toLocalDate();
        model.put("dateText", date == null ? "—" : date.format(DMY));
        model.put("installmentText", ordinal(r.installmentNo()) + " Installment");
        model.put("totalFeeText", indian(r.totalFee()));
        boolean hasDiscount = r.discount() != null && r.discount().signum() > 0;
        model.put("hasDiscount", hasDiscount);
        model.put("discountRows", hasDiscount ? discountRows(r) : List.of());
        model.put("netFeeText", indian(r.netFee() != null ? r.netFee() : r.totalFee()));
        model.put("amountPaidText", indian(r.amountPaid()));
        model.put("totalPaidText", indian(r.totalPaid()));
        model.put("balanceText", indian(r.balance()));
        String[] signatory = r.signatory() == null ? new String[]{""} : r.signatory().split(",", 2);
        model.put("signatoryName", signatory[0].trim());
        model.put("signatoryRole", signatory.length > 1 ? signatory[1].trim() : "");
        model.put("history", historyRows(r));
        return new ReceiptFile(fileName(r), pdfRenderer.pdf(TEMPLATE, model));
    }

    /** Printed only when there is an earlier installment to show; the first receipt stays as it was. */
    static List<HistoryRow> historyRows(ReceiptDto r) {
        if (r.history() == null || r.history().size() < 2) {
            return List.of();
        }
        return r.history().stream()
                .map(h -> new HistoryRow(ordinal(h.installmentNo()) + " Installment", h.paymentNo(),
                        h.paymentDate() == null ? "—" : h.paymentDate().format(DMY), h.paymentMode(), indian(h.amount()), h.current()))
                .toList();
    }

    /**
     * A single discount at the 1st installment prints as plain "Discount", as before; otherwise each
     * line names the installment it was given at.
     */
    static List<DiscountRow> discountRows(ReceiptDto r) {
        if (r.discounts() == null || r.discounts().isEmpty()) {
            return List.of(new DiscountRow("Discount", indian(r.discount())));
        }
        boolean plain = r.discounts().size() == 1 && r.discounts().get(0).installmentNo() == 1;
        return r.discounts().stream()
                .map(d -> new DiscountRow(plain ? "Discount" : "Discount (" + ordinal(d.installmentNo()) + " Installment)", indian(d.amount())))
                .toList();
    }

    /** The same signed receipt as a PNG picture: the PDF's page(s), cropped to the receipt itself. */
    @Transactional(readOnly = true)
    public ReceiptFile picture(AcadReceipt receipt) {
        ReceiptDto r = paymentService.toDto(receipt);
        return new ReceiptFile(pictureName(r), PdfPictures.png(render(r).content(), PICTURE_DPI));
    }

    /** Every installment shares the student's receipt number, so the payment number keeps the file name unique. */
    public static String fileName(ReceiptDto r) {
        return "Fee-Receipt-" + safe(r.receiptNo()) + "-" + safe(r.paymentNo()) + ".pdf";
    }

    public static String pictureName(ReceiptDto r) {
        return "Fee-Receipt-" + safe(r.receiptNo()) + "-" + safe(r.paymentNo()) + ".png";
    }

    private static String safe(String s) {
        return s == null ? "NA" : s.replaceAll("[^A-Za-z0-9-]", "-");
    }

    static String ordinal(int n) {
        int m = n % 100;
        String suffix = (m >= 11 && m <= 13) ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    /** Same as the screen's Number(x).toLocaleString("en-IN"): lakh grouping, up to 3 decimals, no trailing zeros. */
    static String indian(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        BigDecimal x = v.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
        boolean negative = x.signum() < 0;
        String plain = x.abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = dot < 0 ? plain : plain.substring(0, dot);
        String fraction = dot < 0 ? "" : plain.substring(dot);
        StringBuilder out = new StringBuilder();
        if (whole.length() <= 3) {
            out.append(whole);
        } else {
            String head = whole.substring(0, whole.length() - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    out.append(',');
                }
                out.append(head.charAt(i));
            }
            out.append(',').append(whole.substring(whole.length() - 3));
        }
        return (negative ? "-" : "") + out + fraction;
    }
}
