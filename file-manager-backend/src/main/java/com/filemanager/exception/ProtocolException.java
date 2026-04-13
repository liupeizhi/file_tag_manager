package com.filemanager.exception;

public class ProtocolException extends RuntimeException {
    private String protocol;
    private String operation;
    
    public ProtocolException(String protocol, String operation, String message) {
        super(protocol + " " + operation + " 失败: " + message);
        this.protocol = protocol;
        this.operation = operation;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public String getOperation() {
        return operation;
    }
}