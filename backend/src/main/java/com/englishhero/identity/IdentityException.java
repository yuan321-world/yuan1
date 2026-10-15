package com.englishhero.identity;

import java.util.Map;

public class IdentityException extends RuntimeException {
    public final int status;
    public final String code;
    public final Map<String, String> fieldErrors;
    public IdentityException(int status, String code, String message) { this(status, code, message, Map.of()); }
    public IdentityException(int status, String code, String message, Map<String, String> fields) {
        super(message); this.status = status; this.code = code; this.fieldErrors = fields;
    }
    public static IdentityException invalid(String field, String message) {
        return new IdentityException(400, "VALIDATION_ERROR", "请检查填写内容", Map.of(field, message));
    }
}
