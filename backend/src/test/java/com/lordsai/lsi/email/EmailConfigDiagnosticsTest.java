package com.lordsai.lsi.email;

import com.lordsai.lsi.dto.admin.EmailSettingsDtos.DnsCheckResult;
import com.lordsai.lsi.dto.admin.EmailSettingsDtos.DomainDnsStatus;
import com.lordsai.lsi.email.google.GoogleOAuthProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Email Settings diagnostics report what is really configured: exactly which Google OAuth
 * variables are missing, and a DKIM status that is never a false "FAIL".
 */
class EmailConfigDiagnosticsTest {

    private static final String CALLBACK = "http://localhost:8080/api/public/email/google/callback";

    // ---- Google OAuth configuration status --------------------------------------------------------

    @Test
    void missingGoogleVariablesAreNamedButNeverTheirValues() {
        GoogleOAuthProperties none = new GoogleOAuthProperties("", null, CALLBACK);
        assertThat(none.isConfigured()).isFalse();
        assertThat(none.missingVariables()).containsExactly("GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET");
        assertThat(none.notConfiguredMessage())
                .contains("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are not set")
                .contains("backend/.env")
                .contains("restart the backend");

        // A value still copied from .env.example is not a credential.
        GoogleOAuthProperties placeholder = new GoogleOAuthProperties("replace_with_google_oauth_client_id", "GOCSPX-real-looking-secret", CALLBACK);
        assertThat(placeholder.isConfigured()).isFalse();
        assertThat(placeholder.missingVariables()).containsExactly("GOOGLE_CLIENT_ID");
        assertThat(placeholder.notConfiguredMessage()).contains("GOOGLE_CLIENT_ID is not set")
                .doesNotContain("GOCSPX-real-looking-secret");

        GoogleOAuthProperties complete = new GoogleOAuthProperties("123-abc.apps.googleusercontent.com", "GOCSPX-secret", CALLBACK);
        assertThat(complete.isConfigured()).isTrue();
        assertThat(complete.missingVariables()).isEmpty();
    }

    // ---- DKIM -------------------------------------------------------------------------------------

    private static DnsCheckResult dkim(DomainDnsStatus s) {
        return s.checks().stream().filter(c -> "DKIM".equals(c.recordType())).findFirst().orElseThrow();
    }

    private static DnsDiagnosticsService withMx(String... mxHosts) {
        MxResolver mx = mock(MxResolver.class);
        when(mx.lookup(anyString())).thenReturn(List.of(mxHosts));
        return new DnsDiagnosticsService(mx);
    }

    @Test
    void aGmailSenderHasProviderManagedDkimNotAFailure() {
        // Sending domain left blank -> the checker uses the sender's own domain, gmail.com.
        DomainDnsStatus s = withMx("gmail-smtp-in.l.google.com").checkDomain("lordsai.academy@gmail.com", "");
        assertThat(s.domain()).isEqualTo("gmail.com");
        DnsCheckResult d = dkim(s);
        assertThat(d.status()).isEqualTo("PROVIDER_MANAGED");
        assertThat(d.message()).contains("no selector or DNS record for you to set up");
        assertThat(s.checks().stream().filter(c -> "MX".equals(c.recordType())).findFirst().orElseThrow().status()).isEqualTo("PASS");
    }

    @Test
    void aCustomDomainWithoutSelectorIsNotCheckedWithProviderGuidance() {
        // Google Workspace MX; ".invalid" never resolves, so no DKIM key is (wrongly) "found".
        DnsCheckResult workspace = dkim(withMx("aspmx.l.google.com").checkDomain("academy.example.invalid", ""));
        assertThat(workspace.status()).isEqualTo("NOT_CHECKED");
        assertThat(workspace.message()).contains("cannot be guessed").contains("Google Admin console")
                .doesNotContain("A DKIM key IS already published");

        DnsCheckResult unknownHost = dkim(withMx().checkDomain("academy.example.invalid", ""));
        assertThat(unknownHost.status()).isEqualTo("NOT_CHECKED");
        assertThat(unknownHost.message()).doesNotContain("Google Workspace");
    }

    @Test
    void anEnteredSelectorIsLookedUpAndAMissingRecordIsAFailure() {
        DomainDnsStatus s = withMx("aspmx.l.google.com").checkDomain("academy.example.invalid", "google");
        DnsCheckResult d = dkim(s);
        assertThat(d.queryHost()).isEqualTo("google._domainkey.academy.example.invalid");
        assertThat(d.status()).isEqualTo("FAIL");
        assertThat(s.allPassed()).isFalse();
    }
}
