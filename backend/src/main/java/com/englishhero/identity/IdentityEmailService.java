package com.englishhero.identity;

import com.englishhero.identity.email.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Coordinates email proofs with existing account rules; never accepts client roles or resource IDs. */
@Service
public class IdentityEmailService {
    private final IdentityService identities;
    private final IdentityMapper users;
    private final PasswordEncoder encoder;
    private final EmailVerificationService codes;
    private final PersistentRateLimiter limits;
    public IdentityEmailService(IdentityService identities, IdentityMapper users, PasswordEncoder encoder,
            EmailVerificationService codes, PersistentRateLimiter limits) {
        this.identities = identities; this.users = users; this.encoder = encoder; this.codes = codes; this.limits = limits;
    }

    public EmailVerificationService.ChallengeDto issuePublic(String purpose, String email, String username, String ip) {
        String mailbox = EmailRules.email(email);
        if ("REGISTER".equals(purpose))
            return codes.issue(EmailPurpose.REGISTER, mailbox, null, null, users.byEmail(mailbox) == null, ip);
        if (!"RESET".equals(purpose)) throw IdentityException.invalid("purpose", "验证码用途不正确");
        var user = users.byUsername(IdentityRules.username(username, false));
        boolean eligible = eligible(user, mailbox);
        return codes.issue(EmailPurpose.RESET, mailbox, eligible ? user.id() : null,
            eligible ? user.securityVersion() : null, eligible, ip);
    }

    public UserDto register(String username, String password, String displayName, String email, String challengeId, String code) {
        String name = IdentityRules.username(username, true);
        IdentityRules.password(password, "password");
        if (displayName != null) IdentityRules.displayName(displayName);
        String mailbox = EmailRules.email(email);
        if (users.byUsername(name) != null) throw new IdentityException(409, "USERNAME_TAKEN", "用户名已被使用", java.util.Map.of("username", "用户名已被使用"));
        requireAvailable(mailbox);
        return codes.executeVerified(EmailPurpose.REGISTER, mailbox, null, null, challengeId, code, () -> {
            var created = identities.register(name, password, displayName);
            var user = users.byUsername(created.username());
            bind(user, mailbox);
            return identities.dto(users.byId(user.id()));
        });
    }

    public EmailVerificationService.ChallengeDto issueBinding(SessionPrincipal principal, String email, String currentPassword, String ip) {
        String mailbox = EmailRules.email(email);
        var user = authenticateBinding(principal, currentPassword, ip);
        requireAvailable(mailbox);
        return codes.issue(EmailPurpose.BIND, mailbox, user.id(), user.securityVersion(), true, ip);
    }

    public EmailVerificationService.DeliveryStatus bindingStatus(SessionPrincipal principal, String challengeId) {
        var user = identities.current(principal);
        return codes.bindingStatus(challengeId, user.id(), user.securityVersion());
    }

    public UserDto bind(SessionPrincipal principal, String email, String currentPassword, String challengeId, String code, String ip) {
        String mailbox = EmailRules.email(email);
        var user = authenticateBinding(principal, currentPassword, ip);
        requireAvailable(mailbox);
        return codes.executeVerified(EmailPurpose.BIND, mailbox, user.id(), user.securityVersion(), challengeId, code, () -> {
            bind(user, mailbox);
            return identities.dto(users.byId(user.id()));
        });
    }

    public void reset(String username, String email, String challengeId, String code, String newPassword) {
        String name = IdentityRules.username(username, false), mailbox = EmailRules.email(email);
        IdentityRules.password(newPassword, "newPassword");
        var user = users.byUsername(name);
        // A valid proof still has to match a currently enabled account and its current security version.
        codes.executeVerified(EmailPurpose.RESET, mailbox, eligible(user, mailbox) ? user.id() : null,
            eligible(user, mailbox) ? user.securityVersion() : null, challengeId, code, () -> {
                if (!eligible(user, mailbox)) throw invalidProof();
                if (users.resetPassword(user.id(), user.securityVersion(), mailbox, encoder.encode(newPassword)) != 1)
                    throw invalidProof();
                return null;
            });
    }

    private IdentityUser authenticateBinding(SessionPrincipal principal, String password, String ip) {
        var user = identities.current(principal);
        limits.loginAttempt("bind:" + user.username(), ip);
        IdentityRules.password(password, "currentPassword");
        if (!encoder.matches(password, user.passwordHash())) throw new IdentityException(401, "BAD_CREDENTIALS", "当前密码不正确", java.util.Map.of("currentPassword", "当前密码不正确"));
        if (user.email() != null) throw new IdentityException(409, "EMAIL_ALREADY_BOUND", "当前账号已绑定邮箱");
        return user;
    }
    private void bind(IdentityUser user, String mailbox) {
        try {
            if (users.bindEmail(user.id(), user.securityVersion(), mailbox) != 1)
                throw new IdentityException(409, "ACCOUNT_CHANGED", "账号已发生变化，请刷新后重试");
        } catch (DuplicateKeyException e) { throw new IdentityException(409, "EMAIL_TAKEN", "邮箱已被使用", java.util.Map.of("email", "邮箱已被使用")); }
    }
    private void requireAvailable(String mailbox) {
        if (users.byEmail(mailbox) != null) throw new IdentityException(409, "EMAIL_TAKEN", "邮箱已被使用", java.util.Map.of("email", "邮箱已被使用"));
    }
    private boolean eligible(IdentityUser user, String mailbox) {
        return user != null && user.enabled() && user.emailVerifiedAt() != null && mailbox.equals(user.email());
    }
    private IdentityException invalidProof() { return IdentityException.invalid("code", "验证码无效或已过期，请重新获取"); }
}
