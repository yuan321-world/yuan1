package com.englishhero.identity.token;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RefreshTokenCodecTest {
    @Test void randomCanonicalCredentialsAndDigestsNeverPrintSecrets() {
        var codec=new RefreshTokenCodec(TokenTestSupport.auth());
        var a=codec.create(); var b=codec.create();
        assertThat(a.value()).matches("[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}");
        assertThat(a.value()).isNotEqualTo(b.value());
        assertThat(codec.parse(a.value()).sid()).isEqualTo(a.sid());
        assertThat(codec.digest(a.secret())).matches("[a-f0-9]{64}");
        assertThat(a.toString()).doesNotContain(a.secret());
        assertThatThrownBy(()->codec.parse(a.value()+"x")).isInstanceOf(RuntimeException.class);
    }
}
