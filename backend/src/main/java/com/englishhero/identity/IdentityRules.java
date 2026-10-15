package com.englishhero.identity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class IdentityRules {
    private IdentityRules() {}
    public static String username(String value, boolean registration) {
        String name = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]{3,32}")) throw IdentityException.invalid("username", "用户名须为3—32位英文字母、数字或下划线");
        if (registration && (name.startsWith("admin") || Set.of("root", "system").contains(name)))
            throw IdentityException.invalid("username", "以 admin 开头的用户名及系统保留名称不可注册");
        return name;
    }
    public static String password(String value, String field) {
        if (!validText(value) || value.codePointCount(0, value.length()) < 8
            || value.codePointCount(0, value.length()) > 64 || value.getBytes(StandardCharsets.UTF_8).length > 72 || value.isBlank())
            throw IdentityException.invalid(field, "密码须为8—64个字符，UTF-8不超过72字节");
        return value;
    }
    public static String displayName(String value) {
        String name = value == null ? "" : value.strip();
        if (!validText(name) || name.isEmpty() || name.codePointCount(0, name.length()) > 32)
            throw IdentityException.invalid("displayName", "昵称须为1—32个字符");
        return name;
    }
    private static boolean validText(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) return false;
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
}
