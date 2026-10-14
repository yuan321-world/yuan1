package com.englishhero.web;

public record ApiErrorResponse(String code, String message, String traceId) {}
