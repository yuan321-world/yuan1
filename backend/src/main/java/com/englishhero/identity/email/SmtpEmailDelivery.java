package com.englishhero.identity.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SmtpEmailDelivery implements EmailDelivery {
    private final JavaMailSender sender;
    private final EmailProperties properties;

    public SmtpEmailDelivery(JavaMailSender sender, EmailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override
    public boolean available() {
        if (sender instanceof JavaMailSenderImpl smtp
            && Boolean.parseBoolean(smtp.getJavaMailProperties().getProperty("mail.smtp.auth"))) {
            return hasText(smtp.getUsername()) && hasText(smtp.getPassword());
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public void deliver(String email, String code, EmailPurpose purpose) {
        String subject = switch (purpose) {
            case REGISTER -> "English Hero 邮箱验证码";
            case RESET -> "English Hero 密码重置验证码";
            case BIND -> "English Hero 绑定邮箱验证码";
        };
        String action = switch (purpose) {
            case REGISTER -> "完成注册";
            case RESET -> "重置密码";
            case BIND -> "绑定邮箱";
        };
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(EmailRules.email(properties.getFrom()));
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText("您的验证码是 " + code + "，用于" + action + "。验证码 5 分钟内有效，请勿告知他人。", false);
        } catch (MessagingException e) {
            throw new IllegalStateException("Email delivery failed", e);
        }
        sender.send(message);
    }
}
