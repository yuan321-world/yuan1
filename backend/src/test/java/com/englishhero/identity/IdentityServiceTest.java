package com.englishhero.identity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class IdentityServiceTest {
    @Test void bootstrapNeverPromotesExistingUserOrReplacesPassword() {
        var mapper = mock(IdentityMapper.class);
        when(mapper.byUsername("learner")).thenReturn(new IdentityUser(1, "learner", "Learner", "unread", true, 0));
        var service = new IdentityService(mapper, PasswordEncoderFactories.createDelegatingPasswordEncoder());
        assertThatThrownBy(() -> service.bootstrap("learner", "abcdefgh", "Admin")).isInstanceOf(IdentityException.class);
        verify(mapper, never()).insert(any(), any(), any()); verify(mapper, never()).role(anyLong(), any());
    }
    @Test void staleConditionalPasswordWriteIsConflict() {
        var mapper = mock(IdentityMapper.class); var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        when(mapper.byId(1)).thenReturn(new IdentityUser(1, "learner", "Learner", encoder.encode("abcdefgh"), true, 0));
        var service = new IdentityService(mapper, encoder);
        assertThatThrownBy(() -> service.password(new SessionPrincipal(1, "learner", 0, List.of("USER")), "abcdefgh", "ijklmnop"))
            .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.status).isEqualTo(409));
    }
}
