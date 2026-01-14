package com.wrlus.vulscan.common;

import com.wrlus.vulscan.AppScanMain;
import com.wrlus.vulscan.cline.ClineExecutor;
import com.wrlus.vulscan.mcp.McpException;
import com.wrlus.vulscan.scan.AppScanRequest;
import com.wrlus.vulscan.utils.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;

public abstract class BaseScanner {
    protected static final String OUTPUT_FILE_EXT = ".txt";
    protected static final int FUTURE_COMPLETED_TIMEOUT = 300;
    protected static final int MAX_FUTURE_COMPLETED_TIMEOUT_TIME = 5;

    protected final BaseScanRequest request;
    protected PromptResolver promptResolver;

    protected PromptManager promptManager = null;
    protected StateManager stateManager = null;
    
    public BaseScanner(BaseScanRequest request, PromptResolver promptResolver) {
        this.request = request;
        this.promptResolver = promptResolver;
    }
    
    protected int auditSubtask(String vulFile, String vulscanPrompt, 
                              String workflowPath, String outputFile, String taskId) {

        String auditPrompt = promptResolver.resolveAuditPrompt(promptManager.getAuditPrompt(),
                request, vulFile, vulscanPrompt, workflowPath, outputFile);

        StringBuilder finalPrompt = new StringBuilder(auditPrompt);
        for (Object rule : promptManager.getRules()) {
            finalPrompt.append(rule.toString());
        }
        auditPrompt = finalPrompt.toString().replace("\n", "");
        
        ClineExecutor clineExecutor = new ClineExecutor(auditPrompt, taskId);
        return clineExecutor.start();
    }
    
    protected int vulScanTask(String outputFilePrefix,
                              String entryPoint, String workflowPath) {
        String taskId = UUID.randomUUID().toString().substring(0, 6);
        Log.i("⚙️ Executing: " + entryPoint + ", task id: " + taskId);
        
        String outputFile = outputFilePrefix + "_" + taskId + "_Report" + OUTPUT_FILE_EXT;
        
        String taskPrompt = promptResolver.resolveSystemPrompt(promptManager.getSystemPrompt(),
                request, outputFile, entryPoint, workflowPath);
        
        StringBuilder finalTaskPrompt = new StringBuilder(taskPrompt);
        finalTaskPrompt.append(" Rules: ");
        for (Object rule : promptManager.getRules()) {
            finalTaskPrompt.append(rule.toString());
        }
        taskPrompt = finalTaskPrompt.toString().replace("\n", "");
        
        ClineExecutor clineExecutor = new ClineExecutor(taskPrompt, taskId);
        int result = clineExecutor.start();
        
        if (result == 0 && Files.exists(Paths.get(outputFile))) {
            String auditOutputFile = outputFilePrefix + "_" + taskId + "_AuditReport" + OUTPUT_FILE_EXT;
            
            Log.i("💡 AI successfully detected vulnerabilities or risk points, Task ID: " + taskId + ", the auditing process will begin shortly.");
            int auditResult = auditSubtask(outputFile, taskPrompt, workflowPath, auditOutputFile, taskId);
            if (auditResult == 0) {
                Log.i("💡 AI successfully completed the audit task, Task ID: " + taskId + ".");
            } else {
                Log.w("⚠️ Audit task execution error, Task ID: " + taskId + ".");
            }
        }
        
        return result;
    }
    
    protected boolean shouldSkipEntry(String entryPoint, boolean retryFailed) {
        if (stateManager.checkEntryFinished(entryPoint)) {
            // Skip finished entry.
            Log.i("⏭️ Skipping (already completed): " + entryPoint);
            return true;
        } else if (retryFailed && !stateManager.checkEntryFailed(entryPoint)) {
            Log.i("⏭️ Skipping (non-failure item): " + entryPoint);
            return true;
        }
        return false;
    }
    
    protected void handleEntryResult(String entryPoint, int result) {
        if (result == 0) {
            stateManager.addFinishedEntry(entryPoint);
            Log.i("✅ Completed: " + entryPoint + ", Number of completed entries: " + stateManager.getFinishedEntryCount() + ".");
        } else {
            stateManager.addFailedEntry(entryPoint);
            Log.w("❌ Failure: " + entryPoint + ", exited without seeing completion_result.");
        }
    }
    
    protected int scanWorkflow(String outputFilePrefix,
                               String workflowPath, List<String> entryResults,
                               boolean retryFailed) {
        int finalResult = 0;
        ExecutorService executor = Executors.newFixedThreadPool(request.getMaxThread());
        CompletionService<Integer> completionService = new ExecutorCompletionService<>(executor);

        // Submit tasks
        int submittedTasks = 0;
        Map<Future<Integer>, String> futureToEntryPointMap = new HashMap<>();

        for (String entryPoint : entryResults) {
            if (shouldSkipEntry(entryPoint, retryFailed)) {
                continue;
            }

            Future<Integer> future = completionService.submit(
                    () -> vulScanTask(outputFilePrefix, entryPoint, workflowPath));
            futureToEntryPointMap.put(future, entryPoint);
            submittedTasks++;
        }

        // Process completed tasks
        int completedTasks = 0;
        int consecutiveTimeouts = 0;
        
        while (completedTasks < submittedTasks) {
            try {
                Future<Integer> future = completionService.poll(
                        FUTURE_COMPLETED_TIMEOUT, TimeUnit.SECONDS);

                if (future != null) {
                    completedTasks++;
                    consecutiveTimeouts = 0;

                    String entryPoint = futureToEntryPointMap.get(future);
                    try {
                        int result = future.get();
                        handleEntryResult(entryPoint, result);
                        if (result != 0) {
                            finalResult = result;
                        }
                    } catch (ExecutionException e) {
                        Log.e("❌ Failure: " + entryPoint + ", Task execution failed: " + e.getCause().getMessage());
                        stateManager.addFailedEntry(entryPoint);
                        finalResult = 1;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedException("The completed task was interrupted while retrieving the result");
                    }

                    futureToEntryPointMap.remove(future);
                    stateManager.save();

                } else {
                    consecutiveTimeouts++;
                    Log.w("⚠️ Warning: No new tasks completed within " + FUTURE_COMPLETED_TIMEOUT + " seconds. This message has appeared " + 
                        consecutiveTimeouts + " times.");

                    if (consecutiveTimeouts > MAX_FUTURE_COMPLETED_TIMEOUT_TIME) {
                        Log.w("❌ Error: The maximum limit for warnings about no new tasks being completed has been exceeded. Canceling remaining tasks and logging the failure.");

                        for (Map.Entry<Future<Integer>, String> entry : futureToEntryPointMap.entrySet()) {
                            if (!entry.getKey().isDone()) {
                                entry.getKey().cancel(true);
                                stateManager.addFailedEntry(entry.getValue());
                            }
                        }

                        stateManager.save();
                        finalResult = 1;
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w("⚠️ Task execution was interrupted.");
                finalResult = 1;
                break;
            }
        }

        executor.shutdownNow();
        
        return finalResult;
    }
    
    protected int parseManifestAndScanWorkflow() {
        String inputFile = request.getInputFile();
        String outputPath = request.getOutputPath();
        String manifestFile = request.getManifestFile();
        String workflowSelector = request.getWorkflowSelector();
        boolean retryFailed = request.isRetryFailed();
        
        // manifest.json must be in the root of the prompt dir.
        String workflowBase = Paths.get(manifestFile).getParent().toString();
        String fileName = Paths.get(inputFile).getFileName().toString();
        
        String selectedCategory, selectedItem;
        if ("all".equals(workflowSelector)) {
            selectedCategory = "*";
            selectedItem = "*";
        } else {
            String[] parts = workflowSelector.split("\\.", 2);
            selectedCategory = parts[0];
            selectedItem = parts[1];
        }
        
        int finalResult = 0;
        boolean isMismatch = true;
        
        Map<String, Object> manifestWorkflows = promptManager.getWorkflows();
        
        for (String categoryName : manifestWorkflows.keySet()) {
            // selected category matched or is `*`
            if ("*".equals(selectedCategory) || categoryName.equals(selectedCategory)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> categoryWorkflows = (Map<String, Object>) manifestWorkflows.get(categoryName);
                
                for (String itemName : categoryWorkflows.keySet()) {
                    // selected item matched or is `*`
                    if ("*".equals(selectedItem) || itemName.equals(selectedItem)) {
                        isMismatch = false;
                        
                        String workflowSelectorName = categoryName + "." + itemName;
                        if (stateManager.checkWorkflowFinished(workflowSelectorName)) {
                            Log.i("⏭️ Skipping (already completed): " + workflowSelectorName);
                            continue;
                        } else if (retryFailed && !stateManager.checkWorkflowFailed(workflowSelectorName)) {
                            Log.i("⏭️ Skipping (non-failure item): " + workflowSelectorName);
                            continue;
                        } else {
                            // Save current workflow to state.
                            stateManager.setCurrentWorkflow(workflowSelectorName);
                            stateManager.save();
                        }
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> workflowItem = (Map<String, Object>) categoryWorkflows.get(itemName);
                        String entryType = (String) workflowItem.get("entry_type");
                        Object entryItem = workflowItem.get("entry_item");
                        
                        String realWorkflowPath = Paths.get(workflowBase, categoryName, itemName + ".md").toAbsolutePath().normalize().toString();
                        if (!Files.exists(Paths.get(realWorkflowPath))) {
                            Log.e("The workflow file specified by parameter " + workflowSelectorName + " does not exist! Attempting to read path: " + realWorkflowPath);
                            continue;
                        }
                        
                        String realOutputFilePrefix = Paths.get(outputPath, fileName.replace(".apk", "") +
                                "_" + workflowSelectorName).toString();
                        
                        List<String> entryResults = stateManager.getSavedEntries(workflowSelectorName);
                        int entryCount = entryResults.size();
                        if (entryCount == 0) {
                            try {
                                entryResults = executeFindEntryFunction(entryType, entryItem);
                                entryCount = entryResults.size();
                                Log.i("Found " + entryCount + " entries.");
                                if (entryCount > 0) {
                                    stateManager.addEntries(workflowSelectorName, entryResults);
                                    stateManager.save();
                                }
                            } catch (McpException e) {
                                Log.w("⚠️ Entry point not found, skipping: " + workflowSelectorName + ", error message: " + e.getMessage() + ".");
                                continue;
                            } catch (IOException e) {
                                Log.w("❌ Unable to connect to MCP service, skipping: " + workflowSelectorName + ", error message: " + e.getMessage() + ".");
                                finalResult = -1;
                                break;
                            } catch (RuntimeException e) {
                                Log.w("❌ MCP service returned result parsing error, skipping: " + workflowSelectorName + ", error message: " + e.getMessage() + ".");
                                finalResult = -1;
                                continue;
                            }
                        } else {
                            Log.i("Recovered " + entryCount + " entries from the cache.");
                        }
                        
                        int result = scanWorkflow(realOutputFilePrefix, realWorkflowPath,
                                entryResults, retryFailed);
                        
                        if (result == 0) {
                            stateManager.addFinishedWorkflow(workflowSelectorName);
                            Log.i("✅ Completed: " + workflowSelectorName + ".");
                        } else {
                            Log.w("⚠️ Some items failed: " + workflowSelectorName + ", please refer to the 'failed' field in the saved state.");
                            finalResult = result;
                        }

                        stateManager.setCurrentWorkflow("");
                        stateManager.clearFinishedEntry();
                        stateManager.save();
                    }
                }
            }
        }
        
        if (isMismatch) {
            Log.w("Parameter " + workflowSelector + " did not select any workflow.");
        }
        
        return finalResult;
    }
    
    public int scan() {
        int result = -1;

        promptManager = new PromptManager(request.getManifestFile());
        if (promptManager.load() != 0) {
            Log.e("Error loading the manifest file! File name: " + request.getManifestFile());
            return result;
        }

        String inputFile = request.getInputFile();
        String workflowSelector = request.getWorkflowSelector();
        boolean noResume = request.isNoResume();

        stateManager = new StateManager(request);
        if (!noResume) {
            stateManager.restore();
        }

        Log.i("Starting scan task, scan item: " + workflowSelector + ", input file: `" + inputFile + "`.");
        long startTime = System.currentTimeMillis();

        result = parseManifestAndScanWorkflow();

        long endTime = System.currentTimeMillis();
        Log.i("This run took: " + (endTime - startTime) / 1000.0 + " second(s).");

        stateManager.removeSavedFile();

        return result;
    }


    private static Set<String> loadSet(String path, boolean noResume) throws IOException {
        if (Files.exists(Paths.get(path)) && !noResume) {
            Set<String> result = new HashSet<>();
            List<String> lines = Files.readAllLines(Paths.get(path));
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    result.add(Paths.get(trimmed).toAbsolutePath().normalize().toString());
                }
            }
            return result;
        }
        return new HashSet<>();
    }

    public static void scanMultiApks(AppScanRequest request) throws IOException {
        boolean remote = request.isRemote();
        boolean noResume = request.isNoResume();
        if (remote) {
            Log.e("❌ Failure: Scanning folders is temporarily not supported when using remote jebmcp; only single APK files can be scanned.");
            return;
        }
        String inputFile = request.getInputFile();

        String doneFile = Paths.get(inputFile, "done.txt").toAbsolutePath().normalize().toString();
        String failedFile = Paths.get(inputFile, "failed.txt").toAbsolutePath().normalize().toString();
        String benchmarkFile = Paths.get(inputFile, "benchmark.txt").toAbsolutePath().normalize().toString();

        Set<String> doneSet = loadSet(doneFile, noResume);
        Set<String> failedSet = loadSet(failedFile, noResume);

        Log.i("Starting to scan folder: " + inputFile);

        List<String> apkList = new ArrayList<>();
        File inputDir = new File(inputFile);
        File[] files = inputDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".apk")) {
                    String fileName = file.getName().toLowerCase();
                    if (!fileName.contains("overlay")) {
                        apkList.add(file.getAbsolutePath());
                    }
                }
            }
        }

        apkList.sort(String::compareToIgnoreCase);

        for (String full_path : apkList) {
            String normFull = Paths.get(full_path).toAbsolutePath().normalize().toString();

            if (doneSet.contains(normFull)) {
                Log.i("⏭️ Skipping (already completed): " + Paths.get(full_path).getFileName().toString());
                continue;
            }

            String apkName = Paths.get(full_path).getFileName().toString();

            Log.i("🚀 Starting: " + apkName);
            long startTime = System.currentTimeMillis();

            try {
                boolean sawDone = false;

                AppScanRequest subRequest = new AppScanRequest(request);
                subRequest.setInputFile(full_path);

                AppScanMain main = new AppScanMain(subRequest);
                int retCode = main.scan();

                if (retCode == 0) {
                    sawDone = true;
                }

                if (sawDone) {
                    Files.write(Paths.get(doneFile), (full_path + "\n").getBytes(),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    doneSet.add(normFull);
                    Log.i("✅ Completed: " + apkName);
                } else {
                    Files.write(Paths.get(failedFile), (full_path + "\n").getBytes(),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    failedSet.add(normFull);
                    Log.w("⚠️ Some items failed: " + apkName + ", please refer to the 'failed' field in the saved status.");
                }
            } catch (Exception e) {
                Log.e("❌ Execution error: " + e.getMessage());
                Files.write(Paths.get(failedFile), (full_path + "\n").getBytes(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                failedSet.add(normFull);
            }

            long endTime = System.currentTimeMillis();
            String benchmarkLine = full_path + ": " + (endTime - startTime) / 1000.0 + " second(s)\n";
            Files.write(Paths.get(benchmarkFile), benchmarkLine.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            Log.i("--------------------------------------------------------------------------------");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    protected abstract List<String> executeFindEntryFunction(String entryType, Object entryItem)
            throws IOException, McpException;
}
