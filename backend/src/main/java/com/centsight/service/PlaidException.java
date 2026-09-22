package com.centsight.service;

/** A Plaid API error, carrying the error_code so callers can decide what is fatal. */
public class PlaidException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public PlaidException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() { return errorCode; }
    public int getHttpStatus() { return httpStatus; }
}
