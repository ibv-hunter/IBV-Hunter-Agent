package com.wrlus.vulscan.common;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wrlus.vulscan.utils.Log;

public class StateManager {
    private static final String STATE_FILE_SUFFIX = "_state.json";
    
    private final State state;
    private final BaseScanRequest request;
    private final Path savedPath;
    private final ObjectMapper objectMapper;
    
    public StateManager(BaseScanRequest request) {
        this.state = new State(request);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.request = request;
        
        String outputPath = request.getOutputPath();
        String inputFile = request.getInputFile();
        this.savedPath = Paths.get(outputPath, Paths.get(inputFile).getFileName().toString() + STATE_FILE_SUFFIX);
    }

    public void restore() {
        if (Files.exists(savedPath)) {
            try (Reader reader = Files.newBufferedReader(savedPath)) {
                State loadedState = objectMapper.readValue(reader, State.class);

                if (checkRestore(loadedState)) {
                    restoreUncheck(loadedState);
                    Log.i("Successfully restored breakpoint resume scanning data from state file " + savedPath + ".");
                }
            } catch (IOException e) {
                Log.w("Failed to read saved state file " + savedPath + ", error: " + e.getMessage() + ".");
            }
        }
    }
    
    private void restoreUncheck(State loadedState) {
        this.state.currentWorkflow = loadedState.currentWorkflow;
        this.state.finishedWorkflow = new ArrayList<>(loadedState.finishedWorkflow);
        this.state.finishedEntry = new ArrayList<>(loadedState.finishedEntry);
        this.state.failed = new HashMap<>(loadedState.failed);
        this.state.entries = new HashMap<>(loadedState.entries);
    }
    
    public void save() {
        try {
            String json = objectMapper.writeValueAsString(this.state);
            Files.writeString(savedPath, json);
            Log.i("Saving state file to: " + savedPath);
        } catch (IOException e) {
            Log.e("Failed to save state file: " + e.getMessage());
        }
    }
    
    public void setCurrentWorkflow(String workflow) {
        this.state.currentWorkflow = workflow;
    }
    
    public boolean checkCurrentWorkflow(String workflow) {
        return this.state.currentWorkflow.equals(workflow);
    }
    
    public void addFinishedWorkflow(String workflow) {
        this.state.finishedWorkflow.add(workflow);
        this.state.failed.remove(workflow);
    }
    
    public boolean checkWorkflowFinished(String workflow) {
        return this.state.finishedWorkflow.contains(workflow);
    }
    
    public boolean checkWorkflowFailed(String workflow) {
        return this.state.failed.containsKey(workflow);
    }
    
    public void addFinishedEntry(String entry) {
        this.state.finishedEntry.add(entry);

        String workflow = this.state.currentWorkflow;
        if (this.state.failed.containsKey(workflow)) {
            this.state.failed.get(workflow).remove(entry);
        }
    }
    
    public int getFinishedEntryCount() {
        return this.state.finishedEntry.size();
    }
    
    public boolean checkEntryFinished(String entry) {
        return this.state.finishedEntry.contains(entry);
    }
    
    public boolean checkEntryFailed(String entry) {
        String workflow = this.state.currentWorkflow;
        if (this.state.failed.containsKey(workflow)) {
            return this.state.failed.get(workflow).contains(entry);
        }
        return false;
    }
    
    public void clearFinishedEntry() {
        this.state.finishedEntry.clear();
    }
    
    public void addFailedEntry(String entry) {
        String workflow = this.state.currentWorkflow;
        if (this.state.failed.containsKey(workflow)) {
            this.state.failed.get(workflow).add(entry);
        } else {
            List<String> failedEntries = new ArrayList<>();
            failedEntries.add(entry);
            this.state.failed.put(workflow, failedEntries);
        }
    }
    
    public void addEntries(String workflow, List<String> entries) {
        this.state.entries.put(workflow, entries);
    }
    
    public List<String> getSavedEntries(String workflow) {
        if (this.state.entries.containsKey(workflow)) {
            return this.state.entries.get(workflow);
        }
        return new ArrayList<>();
    }
    
    public void removeSavedFile() {
        if (this.state.failed.isEmpty()) {
            if (Files.exists(savedPath)) {
                try {
                    Files.delete(savedPath);
                } catch (IOException e) {
                    Log.w("Failed to delete status file: " + e.getMessage());
                }
            }
        }
    }
    
    public boolean checkRestore(State inputState) {
        return request.equals(inputState.request);
    }
}
