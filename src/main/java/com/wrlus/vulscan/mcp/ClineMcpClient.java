package com.wrlus.vulscan.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.wrlus.vulscan.utils.Log;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ClineMcpClient {
    private static final String MCP_SETTINGS = System.getProperty("user.home") +
            "/.cline/data/settings/cline_mcp_settings.json";
    private final ObjectMapper objectMapper;
    private String mcpSettingName;
    private McpSyncClient client;

    public ClineMcpClient() {
        this.objectMapper = new ObjectMapper();
    }

    private Map<String, Object> getSettings() throws IOException {
        Path settingsPath = Paths.get(MCP_SETTINGS);
        if (!Files.exists(settingsPath)) {
            throw new FileNotFoundException("MCP settings file not found at " + MCP_SETTINGS);
        }

        String content = new String(Files.readAllBytes(settingsPath));

        TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
        Map<String, Object> mcpSettings = objectMapper.readValue(content, typeRef);

        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) mcpSettings.get("mcpServers");
        if (mcpServers == null) {
            throw new RuntimeException("'mcpServers' not found in the settings file.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> setting = (Map<String, Object>) mcpServers.get(mcpSettingName);
        if (setting == null) {
            throw new RuntimeException("Setting '" + mcpSettingName + "' not found in 'mcpServers'.");
        }
        return setting;
    }

    public McpClientTransport getMcpTransport() throws IOException {
        Map<String, Object> setting = getSettings();

        McpClientTransport transport = null;
        String type = (String) setting.get("type");

        if (type.equals("stdio")) {
            String command = (String) setting.get("command");

            @SuppressWarnings("unchecked")
            List<Object> args = (List<Object>) setting.get("args");

            @SuppressWarnings("unchecked")
            Map<String, Object> env = (Map<String, Object>) setting.get("env");

            ServerParameters.Builder paramsBuilder = ServerParameters.builder(command);
            for (Object arg : args) {
                paramsBuilder = paramsBuilder.arg((String) arg);
            }
            for (String envKey : env.keySet()) {
                paramsBuilder = paramsBuilder.addEnvVar(envKey, (String) env.get(envKey));
            }

            transport = new StdioClientTransport(paramsBuilder.build(),
                    new JacksonMcpJsonMapper(objectMapper));
        } else if (type.equals("sse")) {
            String url = (String) setting.get("url");
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) setting.get("headers");

            URI uri = URI.create(url);

            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() != -1) {
                baseUrl += ":" + uri.getPort();
            }
            String sseEndpoint = uri.getRawPath();

            transport = HttpClientSseClientTransport
                    .builder(baseUrl)
                    .sseEndpoint(sseEndpoint)
                    .httpRequestCustomizer((builder, method, endpoint,
                                            body, context) -> {
                        for (String headerKey : headers.keySet()) {
                            builder = builder.header(headerKey, (String) headers.get(headerKey));
                        }
                    }).build();
        } else {
            Log.e("Unsupported MCP client type: " + type);
        }
        return transport;
    }

    public void open() throws IOException {
        if (mcpSettingName == null) {
            throw new IllegalArgumentException("mcpSettingName cannot be null.");
        }

        McpClientTransport transport = getMcpTransport();

        // Create a sync client with custom configuration
        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(1800))
                .loggingConsumer(loggingMessageNotification -> {
                    // ignore logging.

                })
                .capabilities(McpSchema.ClientCapabilities.builder().build()).build();

        // Initialize connection
        client.initialize();
    }

    public Object callTool(String function, Map<String, Object> params) throws McpException {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(function, params));
        Object structuredContent = result.structuredContent();
        List<McpSchema.Content> contents = result.content();

        if (result.isError()) {
            String message = "Unknown error message from MCP.";
            Object structuredError = getStructuredContentItem(structuredContent, "error");

            if (structuredError != null) {
                message = (String) structuredError;
            } else if (!contents.isEmpty() && contents.getFirst().type().equals("text")) {
                McpSchema.TextContent textContent = (McpSchema.TextContent) contents.getFirst();
                message = textContent.text();
            }
            throw new McpException(message);
        }
        return structuredContent;
    }

    public static Object getStructuredContentItem(Object structuredContent, String key) {
        if (structuredContent instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedStructuredContent = (Map<String, Object>) structuredContent;
            return typedStructuredContent.get(key);
        }
        return null;
    }

    public static Object parseResult(Object structuredContent) {
        return getStructuredContentItem(structuredContent, "result");
    }

    @SuppressWarnings("unchecked")
    public static List<String> parseStringListResult(Object structuredContent) throws McpException {
        Object result = parseResult(structuredContent);
        if (result instanceof List) {
            return (List<String>) result;
        } else {
            throw new McpException("Cannot parse result from MCP server.");
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseMapListResult(Object structuredContent) throws McpException {
        Object result = parseResult(structuredContent);
        if (result instanceof List) {
            return (List<Map<String, Object>>) result;
        } else {
            throw new McpException("Cannot parse result from MCP server.");
        }
    }

    public static String parseStringResult(Object structuredContent) throws McpException {
        Object result = parseResult(structuredContent);
        if (result instanceof String) {
            return (String) result;
        } else {
            throw new McpException("Cannot parse result from MCP server.");
        }
    }

    public void close() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    public void setMcpSettingName(String mcpSettingName) {
        this.mcpSettingName = mcpSettingName;
    }
}
