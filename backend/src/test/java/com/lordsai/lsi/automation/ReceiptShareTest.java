package com.lordsai.lsi.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.lordsai.lsi.automation.entity.AcadCommunicationLog;
import com.lordsai.lsi.automation.entity.AcadStudent;
import com.lordsai.lsi.automation.repository.AcadCommunicationLogRepository;
import com.lordsai.lsi.automation.repository.AcadStudentRepository;
import com.lordsai.lsi.automation.service.AcadReceiptShareService;
import com.lordsai.lsi.communication.MetaCloudWhatsAppProvider;
import com.lordsai.lsi.email.EmailAttachment;
import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.email.EmailService;
import com.lordsai.lsi.email.MailSenderResolver;
import com.lordsai.lsi.entity.enums.CommunicationChannel;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import com.lordsai.lsi.entity.enums.CommunicationType;
import com.lordsai.lsi.exception.ApiException;
import com.lordsai.lsi.support.ApiClient;
import com.lordsai.lsi.support.TestAadhaar;
import com.lordsai.lsi.support.TestUsers;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automation Admin > Email & WhatsApp: one click shares the SELECTED payment receipt with that
 * student. Email: sent by the server with the receipt PDF attached. WhatsApp: WhatsApp's own
 * "click to chat" — no WhatsApp Business API — the click copies a picture of the signed receipt and
 * opens the student's chat on the office computer with one short line typed; the office pastes the
 * picture (Ctrl + V) and presses Send. Each channel reports its own honest outcome; WhatsApp is
 * recorded as OPENED, never as SENT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReceiptShareTest {

    /** A4 at the picture's 180 dpi: the receipt picture is cropped well inside this page. */
    private static final int A4_WIDTH = 1488, A4_HEIGHT = 2105;

    @Autowired ApiClient api;
    @Autowired TestUsers users;
    @Autowired AcadCommunicationLogRepository acadLogs;
    @Autowired AcadStudentRepository students;
    @Autowired AcadReceiptShareService shareService;
    @Autowired EntityManager em;

    /** Only the external edge is stubbed: the SMTP hand-off. The WhatsApp API must never be touched. */
    @MockitoSpyBean EmailService emailService;
    @MockitoSpyBean MetaCloudWhatsAppProvider meta;
    @MockitoSpyBean MailSenderResolver mailResolver;

    private String office;
    private long priya, rahul;
    private JsonNode firstPayment, secondPayment, rahulPayment;

    @BeforeEach
    void setUp() throws Exception {
        office = api.login("automation", "Automation@123");
        JsonNode l = api.data(api.get(office, "/api/automation/lookups"));
        long batch = l.path("batches").get(0).path("id").asLong();
        long course = l.path("courses").get(0).path("id").asLong();
        priya = createStudent("Priya Gondhali", "9876500001", "priya@example.com", batch, course);
        rahul = createStudent("Rahul Patil", "9876500002", "rahul@example.com", batch, course);
        firstPayment = pay(priya, 1, "2026-09-20", 5000);
        secondPayment = pay(priya, 2, "2026-09-25", 3000);
        rahulPayment = pay(rahul, 1, "2026-09-21", 4000);
    }

    @AfterEach
    void whatsAppApiIsNeverUsed() {
        verify(meta, never()).sendText(any(), any(), any());
        verify(meta, never()).sendDocument(any(), any(), any(), any(), any(), any());
    }

    private long createStudent(String name, String mobile, String email, long batch, long course) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", name); m.put("batchId", batch); m.put("courseId", course);
        m.put("admissionDate", "2026-09-01"); m.put("mobile", mobile); m.put("email", email); m.put("address", "Uran, Navi Mumbai");
        m.put("aadhaarNo", TestAadhaar.next());
        return api.data(api.post(office, "/api/automation/students", m)).path("id").asLong();
    }

    private JsonNode pay(long studentId, int installment, String date, int amount) throws Exception {
        return api.data(api.post(office, "/api/automation/payments", Map.of("studentId", studentId, "installmentNo", installment,
                "paymentDate", date, "amount", amount, "paymentMode", "CASH")).andExpect(status().isOk()));
    }

    private void emailChannelConfigured() {
        doReturn("ADMIN").when(mailResolver).currentSource();
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private JsonNode share(long receiptId) throws Exception {
        return api.data(api.post(office, "/api/automation/communications/receipts/" + receiptId + "/share", null).andExpect(status().isOk()));
    }

    private BufferedImage picture(long receiptId, String expectedFileName) throws Exception {
        byte[] png = api.get(office, "/api/automation/communications/receipts/" + receiptId + "/image").andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"" + expectedFileName + "\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).as("a readable PNG").isNotNull();
        return image;
    }

    private static String fileStem(JsonNode payment) {
        return "Fee-Receipt-" + payment.path("receiptNo").asText().replace("/", "-") + "-" + payment.path("paymentNo").asText().replace("/", "-");
    }

    /** Every fee receipt (PDF, and the Receipts screen's Email button) carries Vaibhav S. Pawar's signature and the academy stamp. */
    @Test
    void receiptPdfAndReceiptEmailCarryTheProprietorsSignatureAndTheStamp() throws Exception {
        long id = firstPayment.path("receiptId").asLong();
        byte[] pdf = api.get(office, "/api/automation/communications/receipts/" + id + "/pdf").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(com.lordsai.lsi.support.PdfImages.count(pdf)).isEqualTo(3);   // academy logo + stamp + signature
        assertThat(pdfText(pdf)).contains("Vaibhav S. Pawar", "Proprietor");

        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendFeeReceipt(anyString(), any(), any());
        api.post(office, "/api/automation/receipts/" + id + "/email", null).andExpect(status().isOk());
        ArgumentCaptor<EmailAttachment> attached = ArgumentCaptor.forClass(EmailAttachment.class);
        verify(emailService).sendFeeReceipt(eq("priya@example.com"), any(), attached.capture());
        assertThat(attached.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(com.lordsai.lsi.support.PdfImages.count(attached.getValue().content())).isEqualTo(3);
    }

    /**
     * Each receipt lists every installment paid up to and including its own, with amount and date:
     * the 1st receipt has nothing earlier, the 3rd shows the 1st, 2nd and 3rd — and a later
     * installment never appears on an earlier receipt.
     */
    @Test
    void eachReceiptListsThePaymentsUpToItsOwnInstallment() throws Exception {
        JsonNode third = pay(priya, 3, "2026-09-28", 2000);
        long firstId = firstPayment.path("receiptId").asLong(), secondId = secondPayment.path("receiptId").asLong();
        long thirdId = third.path("receiptId").asLong();

        JsonNode h1 = api.data(api.get(office, "/api/automation/receipts/" + firstId)).path("history");
        assertThat(h1).hasSize(1);
        assertThat(h1.get(0).path("current").asBoolean()).isTrue();

        JsonNode h3 = api.data(api.get(office, "/api/automation/receipts/" + thirdId)).path("history");
        assertThat(h3).hasSize(3);
        assertThat(h3.get(0).path("installmentNo").asInt()).isEqualTo(1);
        assertThat(h3.get(0).path("paymentDate").asText()).isEqualTo("2026-09-20");
        assertThat(h3.get(0).path("amount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(h3.get(0).path("current").asBoolean()).isFalse();
        assertThat(h3.get(1).path("paymentNo").asText()).isEqualTo(secondPayment.path("paymentNo").asText());
        assertThat(h3.get(1).path("amount").decimalValue()).isEqualByComparingTo("3000");
        assertThat(h3.get(2).path("amount").decimalValue()).isEqualByComparingTo("2000");
        assertThat(h3.get(2).path("current").asBoolean()).isTrue();

        // The 2nd receipt shows the 1st and 2nd only, even though the 3rd is paid by now.
        assertThat(api.data(api.get(office, "/api/automation/receipts/" + secondId)).path("history")).hasSize(2);

        // On the PDF (email attachment and WhatsApp picture): a Payment History table from the 2nd receipt on.
        String first = pdfText(api.get(office, "/api/automation/communications/receipts/" + firstId + "/pdf").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(first).doesNotContain("Payment History");
        String thirdPdf = pdfText(api.get(office, "/api/automation/communications/receipts/" + thirdId + "/pdf").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(thirdPdf).contains("Payment History", "1st Installment", "20/09/2026", "5,000",
                "2nd Installment", "25/09/2026", "3,000", "3rd Installment", "28/09/2026", "2,000", "(this receipt)");
        String historyPart = thirdPdf.substring(thirdPdf.indexOf("Payment History"));   // ("Amount Paid (this receipt)" is above it)
        assertThat(historyPart.indexOf("(this receipt)")).isGreaterThan(historyPart.indexOf("3rd Installment"));
        assertThat(historyPart.indexOf("(this receipt)")).isGreaterThan(historyPart.indexOf("2nd Installment"));
        String secondPdf = pdfText(api.get(office, "/api/automation/communications/receipts/" + secondId + "/pdf").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(secondPdf).contains("Payment History", "20/09/2026").doesNotContain("28/09/2026");
    }

    // ---- the one-click flow ------------------------------------------------------------------------

    @Test
    void oneClickEmailsTheSelectedReceiptAndOpensWhatsAppForItsPicture() throws Exception {
        emailChannelConfigured();
        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());

        // Priya has two receipts (same receipt number, different payments); the admin picks the SECOND.
        long selected = secondPayment.path("receiptId").asLong();
        assertThat(selected).isNotEqualTo(firstPayment.path("receiptId").asLong());

        // Before the click: the WhatsApp line the click will open, shown to the admin — one short
        // line, no link: the receipt itself goes as the picture pasted with it.
        JsonNode preview = api.data(api.get(office, "/api/automation/communications/receipts/" + selected + "/whatsapp"));
        assertThat(preview.path("available").asBoolean()).isTrue();
        assertThat(preview.path("phone").asText()).isEqualTo("919876500001");
        String text = preview.path("text").asText();
        assertThat(text).isEqualTo("Dear Priya Gondhali, here is your *fee payment receipt* " + secondPayment.path("receiptNo").asText()
                + ". Thank you for your payment.");
        assertThat(text).doesNotContain("http", "receipt.html", "\n");

        // ...and the picture the click copies: the selected receipt, cropped to the receipt (not a blank A4 sheet).
        BufferedImage pic = picture(selected, fileStem(secondPayment) + ".png");
        assertThat(pic.getWidth()).isBetween(1000, A4_WIDTH);
        assertThat(pic.getHeight()).isBetween(400, A4_HEIGHT - 300);

        JsonNode r = share(selected);
        assertThat(r.path("receiptId").asLong()).isEqualTo(selected);
        assertThat(r.path("studentName").asText()).isEqualTo("Priya Gondhali");
        assertThat(r.path("whatsapp").path("status").asText()).isEqualTo("OPENED");
        assertThat(r.path("whatsapp").path("recipient").asText()).isEqualTo("9876500001");
        assertThat(r.path("whatsapp").path("message").asText()).isEqualTo("WhatsApp opened on 9876500001 — press Ctrl + V to paste the receipt picture, then Send.");
        assertThat(r.path("whatsappChat").path("phone").asText()).isEqualTo("919876500001");
        assertThat(r.path("whatsappChat").path("text").asText()).isEqualTo(text);   // exactly what the click opened
        assertThat(r.path("email").path("status").asText()).isEqualTo("SENT");
        assertThat(r.path("email").path("recipient").asText()).isEqualTo("priya@example.com");
        assertThat(r.path("allSent").asBoolean()).isTrue();
        assertThat(r.path("summary").asText()).isEqualTo("WhatsApp: Opened (press Send) · Email: Sent");

        // Email: unchanged — the receipt PDF attached, to Priya's registered address.
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EmailAttachment> attachment = ArgumentCaptor.forClass(EmailAttachment.class);
        verify(emailService).sendMessage(eq("priya@example.com"), eq("Priya Gondhali"), eq("Payment Receipt - Priya Gondhali"), body.capture(), attachment.capture());
        assertThat(body.getValue()).startsWith("Please find your payment receipt attached.").contains("LORD SAI Investment & Share Market Academy");
        assertThat(attachment.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(attachment.getValue().filename()).isEqualTo(fileStem(secondPayment) + ".pdf");
        assertThat(pdfText(attachment.getValue().content()))
                .contains("FEE RECEIPT", "Priya Gondhali", secondPayment.path("paymentNo").asText(), "2nd Installment", "3,000");

        // Recorded in the automation log: email SENT, WhatsApp OPENED (the office presses Send — never claimed as SENT).
        List<AcadCommunicationLog> rows = acadLogs.findAll().stream().filter(x -> x.getMessageType() == CommunicationType.FEE_RECEIPT).toList();
        assertThat(rows).hasSize(2).allSatisfy(x -> {
            assertThat(x.getReceipt().getId()).isEqualTo(selected);
            assertThat(x.getStudent().getId()).isEqualTo(priya);
        });
        assertThat(rows).filteredOn(x -> x.getChannel() == CommunicationChannel.EMAIL).singleElement()
                .satisfies(x -> assertThat(x.getStatus()).isEqualTo(CommunicationStatus.SENT));
        assertThat(rows).filteredOn(x -> x.getChannel() == CommunicationChannel.WHATSAPP).singleElement()
                .satisfies(x -> {
                    assertThat(x.getStatus()).isEqualTo(CommunicationStatus.OPENED);
                    assertThat(x.getProvider()).isEqualTo("WHATSAPP_CHAT");
                    assertThat(x.getBody()).isEqualTo(text);
                    assertThat(x.getAttachmentName()).isEqualTo(fileStem(secondPayment) + ".png");   // the picture went with it
                });
        assertThat(rows).filteredOn(x -> x.getChannel() == CommunicationChannel.EMAIL).singleElement()
                .satisfies(x -> assertThat(x.getAttachmentName()).isEqualTo(fileStem(secondPayment) + ".pdf"));
        api.get(office, "/api/automation/communications/history?studentId=" + priya).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        // Nothing went to the other student.
        verify(emailService, never()).sendMessage(eq("rahul@example.com"), anyString(), anyString(), anyString(), any());
    }

    @Test
    void eachChannelReportsItsOwnOutcome() throws Exception {
        emailChannelConfigured();
        long receipt = firstPayment.path("receiptId").asLong();

        // Email is rejected by the mail server; WhatsApp still opens.
        doReturn(EmailDelivery.failed("SMTP 550: mailbox unavailable")).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
        JsonNode r = share(receipt);
        assertThat(r.path("whatsapp").path("status").asText()).isEqualTo("OPENED");
        assertThat(r.path("email").path("status").asText()).isEqualTo("FAILED");
        assertThat(r.path("email").path("message").asText()).contains("SMTP 550");
        assertThat(r.path("allSent").asBoolean()).isFalse();
        assertThat(r.path("summary").asText()).isEqualTo("WhatsApp: Opened (press Send) · Email: Failed");
    }

    @Test
    void whatsAppNeedsNoSetupWhileEmailWithoutSmtpIsReportedNotConfigured() throws Exception {
        // Test profile: no mail channel, and no WhatsApp Business configuration at all.
        JsonNode r = share(firstPayment.path("receiptId").asLong());
        assertThat(r.path("whatsapp").path("status").asText()).isEqualTo("OPENED");
        assertThat(r.path("email").path("status").asText()).isEqualTo("NOT_CONFIGURED");
        assertThat(r.path("allSent").asBoolean()).isFalse();
        assertThat(r.path("summary").asText()).isEqualTo("WhatsApp: Opened (press Send) · Email: Not configured");
        assertThat(acadLogs.findAll()).filteredOn(x -> x.getMessageType() == CommunicationType.FEE_RECEIPT)
                .hasSize(2).extracting(AcadCommunicationLog::getStatus)
                .containsExactlyInAnyOrder(CommunicationStatus.FAILED, CommunicationStatus.OPENED);
        api.get(office, "/api/automation/communications/status").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.whatsappConfigured").value(true));
    }

    @Test
    void missingEmailOrMobileIsReportedPerChannelWithoutUsingAnyoneElsesContact() throws Exception {
        emailChannelConfigured();
        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());

        AcadStudent p = students.findById(priya).orElseThrow();
        p.setEmail(null);
        students.save(p);
        JsonNode r = share(firstPayment.path("receiptId").asLong());
        assertThat(r.path("email").path("status").asText()).isEqualTo("MISSING_RECIPIENT");
        assertThat(r.path("email").path("message").asText()).isEqualTo("Email cannot be sent — email address is missing.");
        assertThat(r.path("whatsapp").path("status").asText()).isEqualTo("OPENED");
        assertThat(r.path("whatsappChat").path("phone").asText()).isEqualTo("919876500001");
        verify(emailService, never()).sendMessage(anyString(), anyString(), anyString(), anyString(), any());

        AcadStudent rp = students.findById(rahul).orElseThrow();
        rp.setMobile(null);
        students.save(rp);
        long rahulReceipt = rahulPayment.path("receiptId").asLong();
        JsonNode pv = api.data(api.get(office, "/api/automation/communications/receipts/" + rahulReceipt + "/whatsapp"));
        assertThat(pv.path("available").asBoolean()).isFalse();
        assertThat(pv.path("reason").asText()).isEqualTo("WhatsApp cannot be sent — mobile number is missing.");
        r = share(rahulReceipt);
        assertThat(r.path("whatsapp").path("status").asText()).isEqualTo("MISSING_RECIPIENT");
        assertThat(r.path("whatsapp").path("message").asText()).isEqualTo("WhatsApp cannot be sent — mobile number is missing.");
        assertThat(r.path("whatsappChat").isNull()).isTrue();
        assertThat(r.path("email").path("status").asText()).isEqualTo("SENT");
        verify(emailService).sendMessage(eq("rahul@example.com"), eq("Rahul Patil"), anyString(), anyString(), any());
        // No WhatsApp row was recorded for Rahul, and Priya's number was never used for him.
        assertThat(acadLogs.findAll()).filteredOn(x -> x.getChannel() == CommunicationChannel.WHATSAPP)
                .allMatch(x -> x.getRecipient().equals("9876500001"));
    }

    @Test
    void historyReopensWhatsAppForTheReceiptPictureAndDeletedPaymentsStayConsistent() throws Exception {
        emailChannelConfigured();
        long receipt = firstPayment.path("receiptId").asLong();
        doReturn(EmailDelivery.failed("SMTP timeout")).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
        JsonNode shared = share(receipt);
        long emailLog = shared.path("email").path("logId").asLong();
        long whatsAppLog = shared.path("whatsapp").path("logId").asLong();

        // Email retry, as before.
        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
        api.post(office, "/api/automation/communications/history/" + emailLog + "/retry", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));
        ArgumentCaptor<EmailAttachment> att = ArgumentCaptor.forClass(EmailAttachment.class);
        verify(emailService, times(2)).sendMessage(eq("priya@example.com"), anyString(), anyString(), anyString(), att.capture());
        assertThat(att.getAllValues()).allSatisfy(a -> assertThat(a.filename()).contains(firstPayment.path("paymentNo").asText().replace("/", "-")));

        // WhatsApp: "Open again" from the history prepares the same receipt line again, and names the
        // receipt so the screen copies its picture again.
        api.get(office, "/api/automation/communications/history?studentId=" + priya).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + whatsAppLog + ")].receiptId").value(org.hamcrest.Matchers.contains((int) receipt)));
        JsonNode reopened = api.data(api.post(office, "/api/automation/communications/history/" + whatsAppLog + "/retry", null)
                .andExpect(status().isOk()));
        assertThat(reopened.path("status").asText()).isEqualTo("OPENED");
        assertThat(reopened.path("attempts").asInt()).isEqualTo(2);
        assertThat(reopened.path("messageType").asText()).isEqualTo("FEE_RECEIPT");
        assertThat(reopened.path("receiptId").asLong()).isEqualTo(receipt);
        assertThat(reopened.path("attachmentName").asText()).isEqualTo(fileStem(firstPayment) + ".png");
        assertThat(reopened.path("whatsappChat").path("phone").asText()).isEqualTo("919876500001");
        assertThat(reopened.path("whatsappChat").path("text").asText())
                .isEqualTo("Dear Priya Gondhali, here is your *fee payment receipt* " + firstPayment.path("receiptNo").asText() + ". Thank you for your payment.");
        picture(receipt, fileStem(firstPayment) + ".png");

        // Deleting the payment voids the receipt exactly as before; the history row stays, unlinked,
        // and its picture can no longer be made.
        em.flush();
        em.clear();
        api.delete(office, "/api/automation/payments/" + firstPayment.path("id").asLong()).andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(acadLogs.findById(whatsAppLog).orElseThrow().getReceipt()).isNull();
        api.post(office, "/api/automation/communications/history/" + whatsAppLog + "/retry", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.receiptId").doesNotExist())
                .andExpect(jsonPath("$.data.errorReason").value("The receipt no longer exists (its payment was deleted)."));
        api.get(office, "/api/automation/communications/receipts/" + receipt + "/image").andExpect(status().isNotFound());
        api.post(office, "/api/automation/communications/receipts/" + receipt + "/share", null).andExpect(status().isNotFound());
    }

    /** Rows saved before the picture (receipt message + PDF link) get the short line and the picture when opened again. */
    @Test
    void anOlderReceiptRowWithALinkIsReopenedWithThePicture() throws Exception {
        long receipt = firstPayment.path("receiptId").asLong();
        long whatsAppLog = share(receipt).path("whatsapp").path("logId").asLong();
        AcadCommunicationLog row = acadLogs.findById(whatsAppLog).orElseThrow();
        row.setBody("Dear Priya Gondhali,\nThank you for your payment. Here is your *fee payment receipt*: ...\nhttp://localhost:5500/receipt.html?t=r.MQ.1.x");
        row.setAttachmentName(fileStem(firstPayment) + ".pdf");
        acadLogs.saveAndFlush(row);

        JsonNode reopened = api.data(api.post(office, "/api/automation/communications/history/" + whatsAppLog + "/retry", null)
                .andExpect(status().isOk()));
        assertThat(reopened.path("whatsappChat").path("text").asText()).doesNotContain("http").startsWith("Dear Priya Gondhali, here is your");
        assertThat(reopened.path("body").asText()).isEqualTo(reopened.path("whatsappChat").path("text").asText());
        assertThat(reopened.path("attachmentName").asText()).isEqualTo(fileStem(firstPayment) + ".png");
        assertThat(reopened.path("receiptId").asLong()).isEqualTo(receipt);
    }

    @Test
    void aSecondClickWhileTheFirstIsSendingIsRefused() throws Exception {
        emailChannelConfigured();
        long receipt = firstPayment.path("receiptId").asLong();
        AtomicReference<Throwable> nested = new AtomicReference<>();
        doAnswer(inv -> {
            // Simulates the second click arriving while this receipt is still being sent.
            try { shareService.share(receipt, null, null); } catch (Throwable t) { nested.set(t); }
            return EmailDelivery.sent("OFFICE-SMTP");
        }).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
        share(receipt);
        assertThat(nested.get()).isInstanceOf(ApiException.class).hasMessageContaining("already being sent");
        // Afterwards the admin can intentionally send it again.
        doReturn(EmailDelivery.sent("OFFICE-SMTP")).when(emailService).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
        assertThat(share(receipt).path("email").path("status").asText()).isEqualTo("SENT");
    }

    @Test
    void receiptSharingIsAutomationAdminOnly() throws Exception {
        long receipt = firstPayment.path("receiptId").asLong();
        api.get(office, "/api/automation/communications/receipts/" + receipt + "/pdf").andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        api.get(office, "/api/automation/communications/receipts/" + receipt + "/image").andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        api.post(office, "/api/automation/communications/receipts/999999/share", null).andExpect(status().isNotFound());
        api.get(office, "/api/automation/communications/receipts/999999/image").andExpect(status().isNotFound());

        users.student("pupil@test.local");
        String student = api.login("pupil@test.local", TestUsers.PASSWORD);
        api.post(student, "/api/automation/communications/receipts/" + receipt + "/share", null).andExpect(status().isForbidden());
        api.get(student, "/api/automation/communications/receipts/" + receipt + "/pdf").andExpect(status().isForbidden());
        api.get(student, "/api/automation/communications/receipts/" + receipt + "/image").andExpect(status().isForbidden());
        api.get(student, "/api/automation/communications/receipts/" + receipt + "/whatsapp").andExpect(status().isForbidden());
        api.post(null, "/api/automation/communications/receipts/" + receipt + "/share", null).andExpect(status().isUnauthorized());
        api.get(null, "/api/automation/communications/receipts/" + receipt + "/pdf").andExpect(status().isUnauthorized());
        api.get(null, "/api/automation/communications/receipts/" + receipt + "/image").andExpect(status().isUnauthorized());
        api.get(null, "/api/automation/communications/receipts/" + receipt + "/whatsapp").andExpect(status().isUnauthorized());
        verify(emailService, never()).sendMessage(anyString(), anyString(), anyString(), anyString(), any());
    }
}
