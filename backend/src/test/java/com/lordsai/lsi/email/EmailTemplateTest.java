package com.lordsai.lsi.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders every email template so a broken fragment fails the build, not a live purchase. */
@SpringBootTest
@ActiveProfiles("test")
class EmailTemplateTest {

    @Autowired SpringTemplateEngine templateEngine;

    private Context ctx(Map<String, Object> extra) {
        Context c = new Context();
        c.setVariable("portalUrl", "http://localhost:5500/student-login.html");
        c.setVariable("siteUrl", "http://localhost:5500");
        c.setVariable("supportEmail", "help@lordsai.local");
        c.setVariable("supportPhone", "+91 99202 54354");
        c.setVariable("name", "Rahul Sharma");
        c.setVariable("email", "rahul@example.com");
        extra.forEach(c::setVariable);
        return c;
    }

    @Test
    void enrollmentTemplateWithSetupLink() {
        String html = templateEngine.process("email/enrollment", ctx(Map.of(
                "studentId", "LSI-2026-00042", "courseName", "Share Market Education & Training",
                "orderRef", "LSI-ORD-1", "amount", "₹9999.00", "setupLink", "http://x/set-password.html?token=abc")));
        assertThat(html).contains("LSI-2026-00042", "Set my password", "token=abc", "help@lordsai.local", "LORD SAI");
        assertThat(html).doesNotContain("Open Student Portal");
    }

    @Test
    void enrollmentTemplateForExistingAccount() {
        Context c = ctx(Map.of("studentId", "LSI-2026-00001", "courseName", "C", "orderRef", "O", "amount", "₹1"));
        c.setVariable("setupLink", null);
        String html = templateEngine.process("email/enrollment", c);
        assertThat(html).contains("Open Student Portal").doesNotContain("Set my password");
    }

    @Test
    void otherTemplatesRender() {
        assertThat(templateEngine.process("email/welcome", ctx(Map.of("setupLink", "http://x", "role", "Teacher"))))
                .contains("Teacher", "http://x");
        assertThat(templateEngine.process("email/password-reset", ctx(Map.of("resetLink", "http://reset"))))
                .contains("http://reset", "30 minutes");
        assertThat(templateEngine.process("email/payment-confirmation",
                ctx(Map.of("courseName", "C", "orderRef", "LSI-ORD-9", "amount", "₹9999.00"))))
                .contains("LSI-ORD-9");
    }

    @Test
    void enquiryTemplateRenders() {
        String html = templateEngine.process("email/enquiry", ctx(Map.of("visitorName", "Rahul Patil", "visitorEmail", "rahul@example.com",
                "visitorPhone", "9920254354", "subject", "Technical Analysis", "message", "Line one\nLine two", "site", "Share Market Academy",
                "receivedAt", "16 Sep 2026, 03:00 PM IST")));
        assertThat(html).contains("Rahul Patil", "mailto:rahul@example.com", "tel:9920254354", "Technical Analysis", "Line one", "Share Market Academy");
    }

    @Test
    void emailSettingsAndCredentialTemplatesRender() {
        assertThat(templateEngine.process("email/test", ctx(Map.of("requestedBy", "admin@x", "source", "ADMIN"))))
                .contains("test email from the LSI VITC administration system", "working correctly", "LSI VITC Administration", "LORD SAI");
        assertThat(templateEngine.process("email/credentials", ctx(Map.of(
                "userId", "LSI-2026-00007", "password", "Tmp9xQ2kLm4p", "loginUrl", "http://x/student-login.html"))))
                .contains("Rahul Sharma", "LSI-2026-00007", "Tmp9xQ2kLm4p", "http://x/student-login.html",
                        "change your password after your first login", "LSI VITC Administration");
        assertThat(templateEngine.process("email/password-changed", ctx(Map.of("loginUrl", "http://x/student-login.html"))))
                .contains("password was successfully changed", "contact the administrator immediately")
                .doesNotContain("Passw0rd");
    }

    @Test
    void purchaseTemplatesRenderWithInvoiceAndAccessCondition() {
        Map<String, Object> m = new java.util.HashMap<>(Map.of("studentId", "LSI-2026-00042", "productName", "Price Action Workbook",
                "productType", "Ebook", "amount", "₹ 999.00", "paymentStatus", "PAID", "paymentDate", "17 Sep 2026, 10:32 AM IST",
                "transactionId", "pay_ABC", "invoiceNumber", "LSI-INV-2026-000007", "orderRef", "LSI-ORD-1"));
        m.put("setupLink", "http://x/set-password.html?token=abc");
        m.put("accessMessage", "Your purchased ebook is available only inside your authenticated Lord Sai Student Portal account.");
        String ebook = templateEngine.process("email/ebook-purchase-confirmation", ctx(m));
        assertThat(ebook).contains("Ebook Purchase Confirmed", "LSI-2026-00042", "Price Action Workbook", "LSI-INV-2026-000007",
                "pay_ABC", "token=abc", "Direct access to the protected ebook file is not permitted",
                "Your Student Portal credentials are only for accessing your Lord Sai Student Portal account", "#0B5FA5");
        assertThat(ebook).doesNotContain("Open Student Portal</a>");

        m.put("setupLink", null);
        m.put("productType", "Course");
        String course = templateEngine.process("email/course-purchase-confirmation", ctx(m));
        assertThat(course).contains("Course Purchase Confirmed", "Open Student Portal", "Direct access to protected course content is not permitted")
                .doesNotContain("Set my password");

        m.put("temporaryPassword", "X7mP@92kL");
        String newPurchase = templateEngine.process("email/course-purchase-confirmation", ctx(m));
        assertThat(newPurchase).contains("Your Student Portal login details", "Password:", "X7mP@92kL")
                .doesNotContain("Set my password &amp; log in", "Login Limit", "one device");

        m.put("temporaryPassword", null);
        String existing = templateEngine.process("email/existing-student-new-purchase", ctx(m));
        assertThat(existing).contains("New Purchase Added To Your Existing Student Account",
                "You already have a Lord Sai Student Portal account, so no new account has been created", "LSI-INV-2026-000007")
                .doesNotContain("Password:", "Set my password");

        String invoice = templateEngine.process("email/invoice-email", ctx(m));
        assertThat(invoice).contains("LSI-INV-2026-000007", "Price Action Workbook").doesNotContain("password");

        String comm = templateEngine.process("email/communication", ctx(Map.of("subject", "Holiday notice", "message", "Line 1\nLine 2 <b>")));
        assertThat(comm).contains("Holiday notice", "Line 1", "Line 2 &lt;b&gt;");
    }
}
