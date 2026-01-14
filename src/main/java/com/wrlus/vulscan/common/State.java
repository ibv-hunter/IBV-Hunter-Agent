package com.wrlus.vulscan.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.wrlus.vulscan.scan.AppScanRequest;
import com.wrlus.vulscan.scan.FrameworkScanRequest;

import java.util.*;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = AppScanRequest.class, name = "app"),
    @JsonSubTypes.Type(value = FrameworkScanRequest.class, name = "framework")
})
public class State {
    @JsonProperty("request")
    public BaseScanRequest request;
    
    @JsonProperty("current_workflow")
    public String currentWorkflow;
    
    @JsonProperty("finished_workflow")
    public List<String> finishedWorkflow;
    
    @JsonProperty("finished_entry")
    public List<String> finishedEntry;
    
    @JsonProperty("failed")
    public Map<String, List<String>> failed;
    
    @JsonProperty("entries")
    public Map<String, List<String>> entries;

    public State() {
        this.currentWorkflow = "";
        this.finishedWorkflow = new ArrayList<>();
        this.finishedEntry = new ArrayList<>();
        this.failed = new HashMap<>();
        this.entries = new HashMap<>();
    }

    public State(BaseScanRequest request) {
        this();
        this.request = request;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        State state = (State) obj;
        return Objects.equals(request, state.request) &&
                Objects.equals(currentWorkflow, state.currentWorkflow) &&
                Objects.equals(finishedWorkflow, state.finishedWorkflow) &&
                Objects.equals(finishedEntry, state.finishedEntry) &&
                Objects.equals(failed, state.failed) &&
                Objects.equals(entries, state.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, currentWorkflow, finishedWorkflow, finishedEntry, failed, entries);
    }
}
