package com.englishhero.identity;

import com.englishhero.identity.email.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityEmailServiceTest {
    final IdentityMapper mapper = mock(IdentityMapper.class);
    final org.springframework.security.crypto.password.PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    // Use real password validation/encoding; mock only IO boundaries.
    final IdentityService identities = new IdentityService(mapper, encoder);
    final EmailVerificationService codes = mock(EmailVerificationService.class);
    final PersistentRateLimiter limits = mock(PersistentRateLimiter.class);
    final IdentityEmailService service = new IdentityEmailService(identities, mapper, encoder, codes, limits);
    @BeforeEach void executeOnlyVerifiedCallbacks() {
        doAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(6)).get()).when(codes)
            .executeVerified(any(), anyString(), nullable(Long.class), nullable(Integer.class), anyString(), anyString(), any());
    }

    @Test void resetDoesNotPermitAnUnverifiedMailboxOrModifyRoles() {
        when(mapper.byUsername("learner")).thenReturn(new IdentityUser(1, "learner", "Learner", "unused", true, 0));
        assertThatThrownBy(() -> service.reset("learner", "learner@example.com", "challenge", "123456", "new-password"))
            .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.status).isEqualTo(400));
        verify(mapper, never()).password(anyLong(), anyInt(), anyString());
        verify(mapper, never()).role(anyLong(), anyString());
    }

    @Test void bindRequiresCurrentPasswordBeforeIssuingMail() {
        var principal = new SessionPrincipal(1, "learner", 0, List.of("USER"));
        when(mapper.byId(1)).thenReturn(new IdentityUser(1, "learner", "Learner", encoder.encode("valid-password"), true, 0));
        assertThatThrownBy(() -> service.issueBinding(principal, "learner@example.com", "wrong-password", "127.0.0.1"))
            .isInstanceOfSatisfying(IdentityException.class, e -> assertThat(e.status).isEqualTo(401));
        verifyNoInteractions(codes);
    }

    @Test void missingAndUnverifiedResetAccountsHaveSameIssueShape() {
        when(mapper.byUsername("learner")).thenReturn(new IdentityUser(1, "learner", "Learner", "unused", true, 0));
        service.issuePublic("RESET", "learner@example.com", "learner", "127.0.0.1");
        service.issuePublic("RESET", "learner@example.com", "missing", "127.0.0.1");
        verify(codes, times(2)).issue(EmailPurpose.RESET, "learner@example.com", null, null, false, "127.0.0.1");
    }
}
