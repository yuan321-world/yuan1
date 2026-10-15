package com.englishhero.identity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class IdentityRulesTest {
    @Test void registrationReservesWholeAdminPrefixAfterNormalizationButLoginAllowsIt() {
        for (String name : java.util.List.of("admin", "admin123", "AdminLearner", "  ADMIN_qa  ", "administrator")) {
            assertThatThrownBy(() -> IdentityRules.username(name, true)).isInstanceOf(IdentityException.class);
            assertThat(IdentityRules.username(name, false)).isEqualTo(name.strip().toLowerCase(java.util.Locale.ROOT));
        }
        assertThat(IdentityRules.username("myadmin", true)).isEqualTo("myadmin");
    }
    @Test void missingRememberIsOrdinaryLogin() {
        var login = JsonMapper.builder().build().readValue("{\"username\":\"learner\",\"password\":\"abcdefgh\"}", IdentityController.Login.class);
        assertThat(Boolean.TRUE.equals(login.rememberMe())).isFalse();
    }
    @Test void passwordRejectsBcryptTruncationAndMalformedUnicode() {
        assertThatThrownBy(() -> IdentityRules.password("中".repeat(25), "password")).isInstanceOf(IdentityException.class);
        assertThatThrownBy(() -> IdentityRules.password("abcdefgh\uD800", "password")).isInstanceOf(IdentityException.class);
        assertThat(IdentityRules.password("中".repeat(24), "password")).hasSize(24);
    }
}
