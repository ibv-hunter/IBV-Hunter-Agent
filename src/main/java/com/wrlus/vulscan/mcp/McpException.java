package com.wrlus.vulscan.mcp;

public class McpException extends Exception {
    public McpException() {
        super();
    }

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }

    public McpException(Throwable cause) {
        super(cause);
    }
}
