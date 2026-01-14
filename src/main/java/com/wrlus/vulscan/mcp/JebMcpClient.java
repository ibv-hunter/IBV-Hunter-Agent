package com.wrlus.vulscan.mcp;

import java.io.*;
import java.util.*;

public class JebMcpClient {
    private static final String ENV_JEB_MCP_SETTING = "JEB_MCP_SETTING";
    private static final String DEFAULT_JEB_MCP_REMOTE = "jeb_mcp";
    private static final String DEFAULT_JEB_MCP_LOCAL = "jeb";
    
    private static JebMcpClient instance;

    private final ClineMcpClient client;
    private boolean isRemote = false;

    private JebMcpClient() {
        client = new ClineMcpClient();
    }

    public static synchronized JebMcpClient getInstance() {
        if (instance == null) {
            instance = new JebMcpClient();
        }
        return instance;
    }
    
    public void setRemote(boolean remote) {
        this.isRemote = remote;
    }
    
    private String getJebMcpSettingName() {
        String settingName = System.getenv(ENV_JEB_MCP_SETTING);
        if (settingName == null || settingName.isEmpty()) {
            settingName = isRemote ? DEFAULT_JEB_MCP_REMOTE : DEFAULT_JEB_MCP_LOCAL;
        }
        return settingName;
    }

    public void open() throws IOException {
        client.setMcpSettingName(getJebMcpSettingName());
        client.open();
    }

    public void close() {
        client.close();
    }

    public List<String> getAllExportedActivities(String filepath) throws McpException {
        Object structuredContent = client.callTool("get_all_exported_activities",
                Map.of("filepath", filepath));
        return ClineMcpClient.parseStringListResult(structuredContent);
    }
    
    public List<String> getAllExportedServices(String filepath) throws McpException {
        Object structuredContent = client.callTool("get_all_exported_services",
                Map.of("filepath", filepath));
        return ClineMcpClient.parseStringListResult(structuredContent);
    }
    
    public List<Map<String, Object>> getMethodCallers(String filepath, String methodSignature) throws McpException {
        Object structuredContent = client.callTool("get_method_callers",
                Map.of("filepath", filepath, "method_signature", methodSignature));
        return ClineMcpClient.parseMapListResult(structuredContent);
    }
    
    public List<String> getMethodOverrides(String filepath, String methodSignature) throws McpException {
        Object structuredContent = client.callTool("get_method_overrides",
                Map.of("filepath", filepath, "method_signature", methodSignature));
        return ClineMcpClient.parseStringListResult(structuredContent);
    }
}
