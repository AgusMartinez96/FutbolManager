package com.futbolmanager.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String detail;
    private final Long retryAfterSeconds;

    private ApiException(HttpStatus status, String detail, Long retryAfterSeconds) {
        super(detail);
        this.status = status;
        this.detail = detail;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, detail, null);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, detail, null);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, detail, null);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, detail, null);
    }

    public static ApiException unprocessable(String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, detail, null);
    }

    public static ApiException tooManyRequests(String detail, long seconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, detail, seconds);
    }

    public HttpStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
