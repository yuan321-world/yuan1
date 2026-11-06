package com.englishhero.identity.email;

import com.englishhero.identity.IdentityException;
import java.util.Locale;

public final class EmailRules {
    private EmailRules() {}

    public static String email(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (normalized.length() > 254 || at < 1 || at != normalized.lastIndexOf('@') || at > 64
                || at == normalized.length() - 1) {
            throw invalid();
        }
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at + 1);
        if (local.startsWith(".") || local.endsWith(".") || local.contains("..")
                || !local.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+") || domain.length() > 253) {
            throw invalid();
        }
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) throw invalid();
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                throw invalid();
            }
        }
        String tld = labels[labels.length - 1];
        if (!tld.matches("[a-z]{2,63}")) throw invalid();
        return normalized;
    }

    private static IdentityException invalid() {
        return IdentityException.invalid("email", "邮箱格式不正确");
    }
}
