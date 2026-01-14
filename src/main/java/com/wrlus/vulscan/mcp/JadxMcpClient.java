package com.wrlus.vulscan.mcp;

import java.io.*;
import java.util.*;

public class JadxMcpClient {
    private static final String ENV_JADX_MCP_SETTING = "JADX_MCP_SETTING";
    private static final String DEFAULT_JADX_MCP_LOCAL = "jadx";
    
    private static JadxMcpClient instance;
    private final ClineMcpClient client;

    private JadxMcpClient() {
        client = new ClineMcpClient();
    }

    public static synchronized JadxMcpClient getInstance() {
        if (instance == null) {
            instance = new JadxMcpClient();
        }
        return instance;
    }

    private String getJadxMcpSettingName() {
        String settingName = System.getenv(ENV_JADX_MCP_SETTING);
        if (settingName == null || settingName.isEmpty()) {
            settingName = DEFAULT_JADX_MCP_LOCAL;
        }
        return settingName;
    }

    public void open() throws IOException {
        client.setMcpSettingName(getJadxMcpSettingName());
        client.open();
    }

    public void close() {
        client.close();
    }
    
    public String load(String filePath) throws McpException {
        Object structuredContent = client.callTool("load",
                Map.of("filePath", filePath));
        return ClineMcpClient.parseStringResult(structuredContent);
    }
    
    public String loadDir(String dirPath) throws McpException {
        Object structuredContent = client.callTool("load_dir",
                Map.of("dirPath", dirPath));
        return ClineMcpClient.parseStringResult(structuredContent);
    }
    
    public void unload(String instanceId) throws McpException {
        client.callTool("unload",
                Map.of("instanceId", instanceId));
    }
    
    public void unloadAll() throws McpException {
        client.callTool("unload_all", Map.of());
    }
    
    public List<String> searchAidlClasses(String instanceId) throws McpException {
        Object structuredContent = client.callTool("search_aidl_classes",
                Map.of("instanceId", instanceId));
        return ClineMcpClient.parseStringListResult(structuredContent);
    }
    
    public List<String> getAidlMethods(String instanceId, String className) throws McpException {
        Object structuredContent = client.callTool("get_aidl_methods",
                Map.of("instanceId", instanceId, "className", className));
        return ClineMcpClient.parseStringListResult(structuredContent);
    }
    
    public String getAidlImplClass(String instanceId, String className) throws McpException {
        Object structuredContent = client.callTool("get_aidl_impl_class",
                Map.of("instanceId", instanceId, "className", className));
        return ClineMcpClient.parseStringResult(structuredContent);
    }
}
