package com.wrlus.vulscan.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.wrlus.vulscan.utils.Log;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
public abstract class BaseScanRequest {
    protected String inputFile;
    protected String outputPath;
    protected String manifestFile;
    protected String workflowSelector;
    protected int maxThread;
    protected boolean noResume;
    protected boolean retryFailed;

    public BaseScanRequest() {}

    public BaseScanRequest(ScanArgs args) {
        this.inputFile = args.input;
        this.outputPath = args.output;
        this.manifestFile = args.manifest;
        this.workflowSelector = args.workflow;
        this.maxThread = args.thread;
        this.noResume = args.noResume;
        this.retryFailed = args.retryFailed;
    }

    public BaseScanRequest(BaseScanRequest request) {
        this.inputFile = request.inputFile;
        this.outputPath = request.outputPath;
        this.manifestFile = request.manifestFile;
        this.workflowSelector = request.workflowSelector;
        this.maxThread = request.maxThread;
        this.noResume = request.noResume;
        this.retryFailed = request.retryFailed;
    }
    
    // Getters and Setters
    public String getInputFile() { return inputFile; }

    public void setInputFile(String inputFile) { this.inputFile = inputFile; }
    
    public String getOutputPath() { return outputPath; }

    public String getManifestFile() { return manifestFile; }

    public String getWorkflowSelector() { return workflowSelector; }

    public int getMaxThread() { return maxThread; }

    public boolean isNoResume() { return noResume; }

    public boolean isRetryFailed() { return retryFailed; }
    
    protected static boolean checkWorkflowSelectorParam(String workflowSelector) {
        if (workflowSelector.contains(",") || workflowSelector.contains("|")) {
            Log.e("Error: Multiple workflow parameters cannot be specified at this time. Only a single <type>.<name>, <type>.*, or 'all' parameter is allowed! Your current input is: " + workflowSelector);
            return false;
        }
        if (!workflowSelector.contains(".") && !"all".equals(workflowSelector)) {
            Log.e("Error: No '.' found in the workflow parameter, and it's not 'all'. Currently, only single <type>.<name>, <type>.*, or 'all' parameters are allowed! Your current input is: " + workflowSelector);
            return false;
        }
        return true;
    }
    
    protected static boolean validateAndNormalizeArgs(ScanArgs args) {
        // Support remote input apk file.
        String inputFile = args.input;
        if (Files.exists(Paths.get(inputFile))) {
            inputFile = Paths.get(inputFile).toAbsolutePath().normalize().toString();
        }
        args.input = inputFile;
        
        if (!checkWorkflowSelectorParam(args.workflow)) {
            return false;
        }
        
        String outputPath = Paths.get(args.output).toAbsolutePath().normalize().toString();
        if (!Files.isDirectory(Paths.get(outputPath))) {
            Log.w("Output argument is not a directory, reset to default value: " + System.getProperty("user.dir"));
            outputPath = System.getProperty("user.dir");
        }
        args.output = outputPath;

        args.manifest = Paths.get(args.manifest).toAbsolutePath().normalize().toString();
        
        return true;
    }

    @Override
    public String toString() {
        return "Parameters for this request:\n" +
                "  input_file: " + inputFile + "\n" +
                "  output_path: " + outputPath + "\n" +
                "  manifest_file: " + manifestFile + "\n" +
                "  workflow_selector: " + workflowSelector + "\n" +
                "  max_thread: " + maxThread + "\n" +
                "  no_resume: " + noResume + "\n" +
                "  retry_failed: " + retryFailed;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BaseScanRequest request) {
            return Objects.equals(this.inputFile, request.inputFile) &&
                    Objects.equals(this.outputPath, request.outputPath) &&
                    Objects.equals(this.manifestFile, request.manifestFile) &&
                    Objects.equals(this.workflowSelector, request.workflowSelector);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputFile, outputPath, manifestFile, workflowSelector);
    }
}
