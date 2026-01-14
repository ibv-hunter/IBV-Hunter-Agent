package com.wrlus.vulscan.common;

public interface PromptResolver {

    String resolveSystemPrompt(String promptTemplate, BaseScanRequest request,
                               String outputFile, String entryPoint, String workflowPath);

    String resolveAuditPrompt(String promptTemplate, BaseScanRequest request,
                              String vulFile, String vulscanPrompt, String workflowPath, String outputFile);
}
