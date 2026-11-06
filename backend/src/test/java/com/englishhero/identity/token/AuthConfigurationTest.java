package com.englishhero.identity.token;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthConfigurationTest {
    @Test void rejectsMissingKeysOrUnsafeRedisNamespace() {
        var p = new AuthProperties();
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        p.setNamespace("other-project");
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
}
