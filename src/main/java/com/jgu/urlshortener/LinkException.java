package com.jgu.urlshortener;

public class LinkException extends RuntimeException {

    private final int statusCode;

    public LinkException(String message) {
        super(message);
        this.statusCode = 400;
    }

    public LinkException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
