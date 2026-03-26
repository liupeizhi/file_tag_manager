package com.filemanager.exception;

public class WebDavException extends RuntimeException {
    public WebDavException(String message) {
        super(message);
    }
    
    public WebDavException(String message, Throwable cause) {
        super(message, cause);
    }
}