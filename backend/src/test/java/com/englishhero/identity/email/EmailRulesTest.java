package com.englishhero.identity.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishhero.identity.IdentityException;
import org.junit.jupiter.api.Test;

class EmailRulesTest {
    @Test
    void normalizesSurroundingWhitespaceAndCase() {
        assertThat(EmailRules.email("  Learner.Name+tag@Example.COM  "))
            .isEqualTo("learner.name+tag@example.com");
    }

    @Test
    void rejectsMalformedOrHeaderControlledAddresses() {
        for (String email : new String[] {
            null, "", "not-an-address", "a@@example.com", ".a@example.com",
            "a..b@example.com", "a.@example.com", "a@-example.com",
            "a@example-.com", "a@example..com", "a@example.com\r\nBcc:x@y.com",
            "用户@example.com"
        }) {
            assertThatThrownBy(() -> EmailRules.email(email))
                .as("email input %s", email)
                .isInstanceOf(IdentityException.class);
        }
    }

    @Test
    void enforcesMailboxAndAddressLengthLimits() {
        String longLocal = "a".repeat(65) + "@example.com";
        String longAddress = "a@" + "b".repeat(63) + "." + "c".repeat(63) + "."
            + "d".repeat(63) + "." + "e".repeat(63);

        assertThatThrownBy(() -> EmailRules.email(longLocal)).isInstanceOf(IdentityException.class);
        assertThat(longAddress.length()).isGreaterThan(254);
        assertThatThrownBy(() -> EmailRules.email(longAddress)).isInstanceOf(IdentityException.class);
    }
}
