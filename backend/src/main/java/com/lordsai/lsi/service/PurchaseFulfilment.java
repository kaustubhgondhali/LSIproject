package com.lordsai.lsi.service;

import com.lordsai.lsi.email.EmailDelivery;
import com.lordsai.lsi.entity.Invoice;
import com.lordsai.lsi.entity.StudentProfile;
import com.lordsai.lsi.entity.User;

/**
 * Outcome of turning one verified payment into access, for any product type:
 * the (found or created) student, the entitlement that now grants access (enrollment id for a
 * course, ebook entitlement id for an ebook), the invoice, and how the purchase email went.
 */
public record PurchaseFulfilment(User student,
                                 StudentProfile profile,
                                 Long entitlementId,
                                 boolean newAccount,
                                 boolean newEntitlement,
                                 EmailDelivery purchaseEmail,
                                 Invoice invoice) {

    public String studentCode() {
        return profile == null ? null : profile.getStudentId();
    }
}
