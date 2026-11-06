package com.englishhero.identity.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailDeliveryTest {
    @Test
    void authenticatedSmtpIsUnavailableUntilBothCredentialsAreConfigured() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.getJavaMailProperties().setProperty("mail.smtp.auth", "true");
        SmtpEmailDelivery delivery = new SmtpEmailDelivery(sender, new EmailProperties());

        assertThat(delivery.available()).isFalse();

        sender.setUsername("user@example.com");
        assertThat(delivery.available()).isFalse();

        sender.setPassword("mail-app-password");
        assertThat(delivery.available()).isTrue();
    }
}
