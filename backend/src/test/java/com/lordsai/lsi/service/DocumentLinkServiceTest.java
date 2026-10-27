package com.lordsai.lsi.service;

import com.lordsai.lsi.support.ApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The private document links put in WhatsApp messages: signed, expiring, not guessable or editable. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentLinkServiceTest {

    @Autowired DocumentLinkService links;
    @Autowired ApiClient api;

    @Test
    void aLinkIsValidForThirtyDaysAndPointsAtItsDocument() {
        String token = links.token(DocumentLinkService.Kind.RECEIPT, "42");
        DocumentLinkService.Verified v = links.verify(token);
        assertThat(v.check()).isEqualTo(DocumentLinkService.Check.VALID);
        assertThat(v.target().kind()).isEqualTo(DocumentLinkService.Kind.RECEIPT);
        assertThat(v.target().id()).isEqualTo(42L);
        assertThat(v.target().expiresOn()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(30));
        // The same document on the same day gets the same link (the message shown == the message logged).
        assertThat(links.token(DocumentLinkService.Kind.RECEIPT, "42")).isEqualTo(token);
        assertThat(links.receiptLink(42L)).endsWith("/receipt.html?t=" + token);
    }

    @Test
    void tamperedOrMadeUpLinksAreRefused() {
        String token = links.token(DocumentLinkService.Kind.RECEIPT, "42");
        // Pointing an existing signature at another receipt.
        String otherReceipt = token.replaceFirst("^r\\.[^.]+\\.", "r.NDM.");   // "43"
        assertThat(links.verify(otherReceipt).check()).isEqualTo(DocumentLinkService.Check.INVALID);
        // Changing a receipt link into an invoice link.
        assertThat(links.verify("i" + token.substring(1)).check()).isEqualTo(DocumentLinkService.Check.INVALID);
        // Extending the expiry date.
        String[] p = token.split("\\.");
        assertThat(links.verify(p[0] + "." + p[1] + "." + (Long.parseLong(p[2]) + 365) + "." + p[3]).check())
                .isEqualTo(DocumentLinkService.Check.INVALID);
        for (String junk : new String[]{"", "abc", "r.MQ.1.x", "a.b.c.d.e"}) {
            assertThat(links.verify(junk).check()).isEqualTo(DocumentLinkService.Check.INVALID);
        }
    }

    @Test
    void anExpiredLinkSaysSoAndOpensNothing() throws Exception {
        String expired = links.token(DocumentLinkService.Kind.RECEIPT, "42", LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1));
        assertThat(links.verify(expired).check()).isEqualTo(DocumentLinkService.Check.EXPIRED);
        api.get(null, "/api/public/documents/" + expired).andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value(SharedDocumentService.EXPIRED));
        api.get(null, "/api/public/documents/not-a-link/info").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(SharedDocumentService.INVALID));
        // Valid link, but the receipt does not exist (deleted payment).
        api.get(null, "/api/public/documents/" + links.token(DocumentLinkService.Kind.RECEIPT, "999999")).andExpect(status().isNotFound());
        // Only office message attachments can be shared, never other stored files.
        api.get(null, "/api/public/documents/" + links.token(DocumentLinkService.Kind.ATTACHMENT, "invoices/secret.pdf")).andExpect(status().isNotFound());
    }
}
