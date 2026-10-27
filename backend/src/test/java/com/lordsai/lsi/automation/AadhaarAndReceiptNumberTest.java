package com.lordsai.lsi.automation;

import com.lordsai.lsi.automation.service.Aadhaar;
import com.lordsai.lsi.automation.service.AcadPaymentService;
import com.lordsai.lsi.support.TestAadhaar;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The Aadhaar check on the Add Student form, and the receipt number that follows the Student ID. */
class AadhaarAndReceiptNumberTest {

    @Test
    void aValidAadhaarNumberPassesAndEveryTypingMistakeIsCaught() {
        String valid = TestAadhaar.next();
        assertThat(Aadhaar.isValid(valid)).isTrue();
        for (int i = 0; i < 12; i++) {
            for (char d = '0'; d <= '9'; d++) {
                if (d != valid.charAt(i)) {
                    String typo = valid.substring(0, i) + d + valid.substring(i + 1);
                    assertThat(Aadhaar.isValid(typo)).as("one wrong digit: " + typo).isFalse();
                }
            }
        }
        for (int i = 0; i < 11; i++) {
            if (valid.charAt(i) != valid.charAt(i + 1)) {
                String swapped = valid.substring(0, i) + valid.charAt(i + 1) + valid.charAt(i) + valid.substring(i + 2);
                assertThat(Aadhaar.isValid(swapped)).as("two digits swapped: " + swapped).isFalse();
            }
        }
        assertThat(Aadhaar.isValid("0" + valid.substring(1))).isFalse();
        assertThat(Aadhaar.isValid(null)).isFalse();
        assertThat(Aadhaar.normalize(" " + valid.substring(0, 4) + "-" + valid.substring(4, 8) + " " + valid.substring(8) + " ")).isEqualTo(valid);
        assertThat(Aadhaar.normalize("   ")).isNull();
        assertThat(Aadhaar.mask(valid)).isEqualTo("XXXX XXXX " + valid.substring(8));
    }

    @Test
    void theReceiptNumberIsTheStudentIdNumber() {
        assertThat(AcadPaymentService.receiptNoFor("LSA/2026/0025")).isEqualTo("LSR/2026/0025");
        assertThat(AcadPaymentService.receiptNoFor("lsa/2026/0100 ")).isEqualTo("LSR/2026/0100");
        // An office-chosen series keeps its whole ID, so two students never share a receipt number.
        assertThat(AcadPaymentService.receiptNoFor("B3-0007")).isEqualTo("LSR/B3-0007");
    }
}
