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
}
