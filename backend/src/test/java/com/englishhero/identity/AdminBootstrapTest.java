package com.englishhero.identity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AdminBootstrapTest {
    @Test void successfulBootstrapIsOneShotAndDoesNotLeaveServerRunning() {
        var service = mock(IdentityService.class); var context = mock(ConfigurableApplicationContext.class);
        new AdminBootstrap(service, context, "admin", "abcdefgh", "Admin").run(new DefaultApplicationArguments());
        var order = inOrder(service, context);
        order.verify(service).bootstrap("admin", "abcdefgh", "Admin"); order.verify(context).close();
    }
    @Test void duplicateCannotBeReportedAsBootstrapSuccess() {
        var service = mock(IdentityService.class); var context = mock(ConfigurableApplicationContext.class);
        when(service.bootstrap(any(), any(), any())).thenThrow(new IdentityException(409, "USERNAME_TAKEN", "已有同名账号"));
        assertThatThrownBy(() -> new AdminBootstrap(service, context, "admin", "abcdefgh", "Admin").run(new DefaultApplicationArguments())).isInstanceOf(IdentityException.class);
        verify(context, never()).close();
    }
}
