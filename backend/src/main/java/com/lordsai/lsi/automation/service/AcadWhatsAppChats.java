package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.dto.AutomationDtos.ReceiptDto;
import com.lordsai.lsi.automation.entity.AcadCommunicationLog;
import com.lordsai.lsi.automation.entity.AcadReceipt;
import com.lordsai.lsi.communication.WhatsAppChat;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.service.DocumentLinkService;
import org.springframework.stereotype.Component;

/**
 * The ready-made WhatsApp messages of the Automation Admin. WhatsApp's "click to chat" can carry
 * text only: a payment receipt travels as a picture that the Send click copies and the office
 * pastes into the chat (Ctrl + V) under one short typed line; an office message's attachment
 * travels as a private download link (DocumentLinkService). Nothing here sends anything: the
 * office opens the chat and presses Send.
 */
@Component
public class AcadWhatsAppChats {

    static final String SIGN_OFF = "Regards,\nLORD SAI Investment & Share Market Academy";

    private final AcadPaymentService paymentService;
    private final DocumentLinkService links;

    public AcadWhatsAppChats(AcadPaymentService paymentService, DocumentLinkService links) {
        this.paymentService = paymentService;
        this.links = links;
    }

    /** The payment receipt chat for the receipt's own student, or null when that student has no usable mobile number. */
    public WhatsAppChat receipt(AcadReceipt receipt) {
        return WhatsAppChat.of(receipt.getStudent().getMobile(), receiptText(receipt));
    }

    /**
     * The one short line typed in the chat; the receipt itself (every detail, with the signature) is
     * the picture pasted with it. Says plainly that it is the receipt of the payment.
     */
    public String receiptText(AcadReceipt receipt) {
        ReceiptDto r = paymentService.toDto(receipt);
        return "Dear " + receipt.getStudent().getFullName() + ", here is your *fee payment receipt*"
                + (r.receiptNo() == null || r.receiptNo().isBlank() ? "" : " " + r.receiptNo())
                + ". Thank you for your payment.";
    }

    /** An office message (individual / bulk): subject, message and, when there is one, the attachment link. */
    public WhatsAppChat message(AcadCommunicationLog row) {
        StringBuilder t = new StringBuilder();
        if (row.getSubject() != null && !row.getSubject().isBlank()) {
            t.append("*").append(row.getSubject().trim()).append("*\n\n");
        }
        t.append(row.getBody() == null ? "" : row.getBody());
        if (row.getAttachmentPath() != null) {
            t.append("\n\nAttachment").append(row.getAttachmentName() == null ? "" : " (" + row.getAttachmentName() + ")").append(":\n")
                    .append(links.attachmentLink(row.getAttachmentPath()))
                    .append("\n(This link is valid for ").append(DocumentLinkService.LINK_DAYS).append(" days.)");
        }
        t.append("\n\n— Lord Sai Share Market Classes");
        return WhatsAppChat.of(row.getRecipient(), t.toString());
    }

    /**
     * The chat for a WhatsApp log row. Receipt rows are rebuilt from the receipt (the screen copies
     * its picture again); null when the receipt no longer exists or the number cannot be used.
     */
    public WhatsAppChat forRow(AcadCommunicationLog row) {
        if (row.getMessageType() == CommunicationType.FEE_RECEIPT) {
            return row.getReceipt() == null ? null : WhatsAppChat.of(row.getRecipient(), receiptText(row.getReceipt()));
        }
        if (row.getMessageType() == CommunicationType.FEE_REMINDER) {
            return WhatsAppChat.of(row.getRecipient(), row.getBody());   // the reminder exactly as it was first opened
        }
        return message(row);
    }
}
