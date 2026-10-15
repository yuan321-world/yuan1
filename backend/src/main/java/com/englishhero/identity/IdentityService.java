package com.englishhero.identity;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final IdentityMapper mapper;
    private final PasswordEncoder encoder;
    public IdentityService(IdentityMapper mapper, PasswordEncoder encoder) { this.mapper = mapper; this.encoder = encoder; }
    @Transactional public UserDto register(String username, String password, String displayName) {
        String name = IdentityRules.username(username, true);
        return create(name, IdentityRules.password(password, "password"), displayName == null ? name : IdentityRules.displayName(displayName), false);
    }
    @Transactional public UserDto bootstrap(String username, String password, String displayName) {
        String name = IdentityRules.username(username, false);
        if (mapper.byUsername(name) != null) throw new IdentityException(409, "USERNAME_TAKEN", "已有同名账号，不覆盖或提升权限");
        return create(name, IdentityRules.password(password, "password"), IdentityRules.displayName(displayName), true);
    }
    private UserDto create(String name, String password, String displayName, boolean admin) {
        try { mapper.insert(name, displayName, encoder.encode(password)); }
        catch (DuplicateKeyException e) { throw new IdentityException(409, "USERNAME_TAKEN", "用户名已被使用"); }
        var user = mapper.byUsername(name);
        mapper.role(user.id(), "USER");
        if (admin) mapper.role(user.id(), "ADMIN");
        return dto(user);
    }
    public UserDto dto(IdentityUser user) { return new UserDto(Long.toString(user.id()), user.username(), user.displayName(), mapper.roles(user.id())); }
    public IdentityUser current(SessionPrincipal principal) {
        var user = mapper.byId(principal.userId());
        if (user == null || !user.enabled() || user.securityVersion() != principal.securityVersion())
            throw new IdentityException(401, "UNAUTHENTICATED", "登录已失效，请重新登录");
        return user;
    }
    @Transactional public UserDto profile(SessionPrincipal principal, String displayName) {
        current(principal);
        if (mapper.profile(principal.userId(), principal.securityVersion(), IdentityRules.displayName(displayName)) != 1) throw changed();
        return dto(current(principal));
    }
    @Transactional public void password(SessionPrincipal principal, String oldPassword, String newPassword) {
        var user = current(principal);
        IdentityRules.password(oldPassword, "currentPassword");
        IdentityRules.password(newPassword, "newPassword");
        if (!encoder.matches(oldPassword, user.passwordHash())) throw new IdentityException(401, "BAD_CREDENTIALS", "当前密码不正确");
        if (mapper.password(principal.userId(), principal.securityVersion(), encoder.encode(newPassword)) != 1) throw changed();
    }
    private IdentityException changed() { return new IdentityException(409, "ACCOUNT_CHANGED", "账号已发生变化，请重新登录"); }
}
