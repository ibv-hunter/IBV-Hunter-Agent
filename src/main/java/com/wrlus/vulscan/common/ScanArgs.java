package com.wrlus.vulscan.common;

public abstract class ScanArgs {
    public String input;
    public String output;
    public String manifest;
    public String workflow;
    public int thread;
    public boolean noResume;
    public boolean retryFailed;

    public static final String DEFAULT_SCAN_WORKFLOW = "all";
    
    public ScanArgs() {
        this.workflow = DEFAULT_SCAN_WORKFLOW;
        this.thread = 1;
        this.noResume = false;
        this.retryFailed = false;
    }
}
