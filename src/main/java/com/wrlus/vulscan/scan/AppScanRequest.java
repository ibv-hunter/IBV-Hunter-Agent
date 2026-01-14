package com.wrlus.vulscan.scan;

import com.wrlus.vulscan.common.BaseScanRequest;

public class AppScanRequest extends BaseScanRequest {
    private boolean remote;

    public AppScanRequest() {
        super();
    }
    public AppScanRequest(AppScanArgs args) {
        super(args);
        this.remote = args.remote;
    }

    public AppScanRequest(AppScanRequest request) {
        super(request);
        this.remote = request.remote;
    }
    
    public boolean isRemote() { return remote; }

    @Override
    public String toString() {
        String baseRequestString = super.toString();
        return baseRequestString + "  remote: " + remote + "\n";
    }

    public static AppScanRequest parseFromArguments(String[] args) {
        AppScanArgs scanArgs = new AppScanArgs();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i":
                case "--input":
                    if (i + 1 < args.length) {
                        scanArgs.input = args[++i];
                    }
                    break;
                case "-o":
                case "--output":
                    if (i + 1 < args.length) {
                        scanArgs.output = args[++i];
                    }
                    break;
                case "-m":
                case "--manifest":
                    if (i + 1 < args.length) {
                        scanArgs.manifest = args[++i];
                    }
                    break;
                case "-w":
                case "--workflow":
                    if (i + 1 < args.length) {
                        scanArgs.workflow = args[++i];
                    }
                    break;
                case "--thread":
                    if (i + 1 < args.length) {
                        try {
                            scanArgs.thread = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Error: The --thread parameter requires a number.");
                            return null;
                        }
                    } else {
                        System.err.println("Error: The --thread parameter requires a number.");
                        return null;
                    }
                    break;
                case "--no-resume":
                    scanArgs.noResume = true;
                    break;
                case "--retry-failed":
                    scanArgs.retryFailed = true;
                    break;
                case "--remote":
                    scanArgs.remote = true;
                    break;
            }
        }

        if (scanArgs.input == null || scanArgs.input.trim().isEmpty()) {
            System.err.println("Error: The -i/--input parameter must be specified.");
            return null;
        }
        if (scanArgs.output == null || scanArgs.output.trim().isEmpty()) {
            System.err.println("Error: The -o/--output parameter must be specified.");
            return null;
        }
        if (scanArgs.manifest == null || scanArgs.manifest.trim().isEmpty()) {
            System.err.println("Error: The -m/--manifest parameter must be specified.");
            return null;
        }
        
        if (!BaseScanRequest.validateAndNormalizeArgs(scanArgs)) {
            return null;
        }
        
        return new AppScanRequest(scanArgs);
    }
}
