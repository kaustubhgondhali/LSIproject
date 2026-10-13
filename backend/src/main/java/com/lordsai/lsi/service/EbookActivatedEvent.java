package com.lordsai.lsi.service;

import com.lordsai.lsi.entity.User;

/**
 * Published when an ebook goes on sale: uploaded by the Main Admin (it is created Active) or
 * switched back to Active. Listeners that change data run after the ebook is saved, in their own
 * transaction (see the Automation Admin's AcadNewEbookGrant), so they can never undo the upload.
 */
public record EbookActivatedEvent(Long ebookId, User actor, String ip) {
}
