package com.wrlus.vulscan.scan;

import com.wrlus.vulscan.common.BaseScanRequest;
import com.wrlus.vulscan.common.PromptResolver;


public class AppPromptResolver implements PromptResolver {
    
    @Override
    public String resolveSystemPrompt(String promptTemplate, BaseScanRequest request,
                                      String outputFile, String entryPoint, String workflowPath) {
        return promptTemplate.replace("{apk_path}", request.getInputFile())
                .replace("{output_path}", outputFile)
                .replace("{entry_point}", entryPoint)
                .replace("{vulscan_workflow}", workflowPath);
    }
    
    @Override
    public String resolveAuditPrompt(String promptTemplate, BaseScanRequest request,
                                     String vulFile, String vulscanPrompt, String workflowPath, String outputFile) {
        return promptTemplate.replace("{apk_path}", request.getInputFile())
                .replace("{vulscan_prompt}", vulscanPrompt)
                .replace("{vulscan_workflow}", workflowPath)
                .replace("{vul_file}", vulFile)
                .replace("{output_path}", outputFile);
    }
}
