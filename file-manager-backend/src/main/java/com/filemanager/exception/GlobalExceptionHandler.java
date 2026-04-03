package com.filemanager.exception;

import com.filemanager.dto.ApiResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(WebDavException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleWebDavException(WebDavException e) {
        return ApiResponse.error(e.getMessage());
    }
    
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e) {
    }
    
    @ExceptionHandler(SocketTimeoutException.class)
    public void handleSocketTimeoutException(SocketTimeoutException e) {
    }
    
    @ExceptionHandler(SocketException.class)
    public void handleSocketException(SocketException e) {
    }
    
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException e) {
    }
    
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public void handleHttpMessageNotWritableException(HttpMessageNotWritableException e) {
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        if (e.getCause() instanceof ClientAbortException || 
            e.getCause() instanceof SocketTimeoutException ||
            e.getCause() instanceof SocketException) {
            return null;
        }
        e.printStackTrace();
        return ApiResponse.error("系统错误: " + e.getMessage());
    }
}