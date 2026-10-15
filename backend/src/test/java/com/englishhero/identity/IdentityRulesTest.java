package com.englishhero.identity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class IdentityRulesTest {
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
