package com.wrlus.vulscan.common;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrlus.vulscan.utils.Log;

public class PromptManager {
    private String systemPrompt;
    private String auditPrompt;
    private List<Object> rules;
    private final Map<String, Object> workflows;
    private final String manifestFile;

    public PromptManager(String manifestFile) {
        systemPrompt = "This is a sample system prompt. If you see this, please report a bug and exit.";
        auditPrompt = "This is a sample system prompt. If you see this, please report a bug and exit.";
        rules = new ArrayList<>();
        workflows = new HashMap<>();
        this.manifestFile = manifestFile;
    }
    
    public int load() {
        if (Files.exists(Paths.get(manifestFile))) {
            try (Reader reader = Files.newBufferedReader(Paths.get(manifestFile))) {

                ObjectMapper objectMapper = new ObjectMapper();
                TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
                Map<String, Object> manifestStruct = objectMapper.readValue(reader, typeRef);

                if (manifestStruct.containsKey("system_prompt") && manifestStruct.get("system_prompt") instanceof String) {
                    systemPrompt = (String) manifestStruct.get("system_prompt");
                } else {
                    Log.e("The system_prompt field is missing in the workflow manifest file, or the field type is invalid!");
                    return -1;
                }
                
                if (manifestStruct.containsKey("audit_prompt") && manifestStruct.get("audit_prompt") instanceof String) {
                    auditPrompt = (String) manifestStruct.get("audit_prompt");
                } else {
                    Log.e("The 'audit_prompt' field is missing in the workflow manifest file, or the field type is invalid!");
                    return -1;
                }
                
                if (manifestStruct.containsKey("rules") && manifestStruct.get("rules") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> rulesList = (List<Object>) manifestStruct.get("rules");
                    rules = rulesList;
                } else {
                    Log.w("The 'rules' field is missing in the workflow manifest file. Please consider specifying some rules for the model!");
                }
                
                if (manifestStruct.containsKey("workflows") && manifestStruct.get("workflows") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> workflowsMap = (Map<String, Object>) manifestStruct.get("workflows");
                    workflows.putAll(workflowsMap);
                } else {
                    Log.e("The 'workflows' field is missing in the workflow manifest file, or the field type is invalid!");
                    return -1;
                }
                
                return 0;
            } catch (IOException e) {
                Log.e("Failed to read workflow manifest file: " + e.getMessage());
                return -1;
            }
        } else {
            Log.e("The specified workflow manifest file does not exist! Attempting to read path: " + manifestFile);
            return -1;
        }
    }
    
    public List<Object> getRules() {
        return rules;
    }
    
    public Map<String, Object> getWorkflows() {
        return workflows;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getAuditPrompt() {
        return auditPrompt;
    }
}
