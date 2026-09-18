package com.lordsai.lsi.email;

import com.lordsai.lsi.entity.enums.CommunicationType;

import java.util.Map;

/**
 * Everything needed to send one purchase-related transactional email. The {@code kind} selects
 * the template (course purchase / ebook purchase / new purchase on an existing account / invoice
 * resend); {@code model} carries the dynamic values; {@code attachment} is the invoice PDF.
 */
public record PurchaseEmail(CommunicationType kind, String to, String subject, Map<String, Object> model,
                            EmailAttachment attachment) {

    public String template() {
        return switch (kind) {
            case COURSE_PURCHASE -> "email/course-purchase-confirmation";
            case EBOOK_PURCHASE -> "email/ebook-purchase-confirmation";
            case EXISTING_STUDENT_PURCHASE -> "email/existing-student-new-purchase";
            case INVOICE -> "email/invoice-email";
            case ADMIN_INDIVIDUAL, ADMIN_BULK, EXAM_NOTIFICATION -> "email/communication";
        };
    }
}
