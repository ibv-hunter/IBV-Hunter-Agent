package com.wrlus.vulscan.scan;

import com.wrlus.vulscan.common.BaseScanRequest;

public class FrameworkScanRequest extends BaseScanRequest {
    private String serviceScope;

    public FrameworkScanRequest() {
        super();
    }

    public FrameworkScanRequest(FrameworkScanArgs args) {
        super(args);
        this.serviceScope = args.serviceScope;
    }
    
    public String getServiceScope() {
        return serviceScope;
    }
    
    @Override
    public String toString() {
        String baseRequestString = super.toString();
        return baseRequestString + "  service_scope: " + serviceScope + "\n";
    }
    

    public static FrameworkScanRequest parseFromArguments(String[] args) {
        FrameworkScanArgs scanArgs = new FrameworkScanArgs();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i":
                case "--input":
                    if (i + 1 < args.length) {
                        scanArgs.input = args[++i];
                    } else {
                        System.err.println("Error: The -i/--input parameter requires a value.");
                        return null;
                    }
                    break;
                case "-o":
                case "--output":
                    if (i + 1 < args.length) {
                        scanArgs.output = args[++i];
                    } else {
                        System.err.println("Error: The -o/--output parameter requires a value.");
                        return null;
                    }
                    break;
                case "-m":
                case "--manifest":
                    if (i + 1 < args.length) {
                        scanArgs.manifest = args[++i];
                    } else {
                        System.err.println("Error: The -m/--manifest parameter requires a value.");
                        return null;
                    }
                    break;
                case "-w":
                case "--workflow":
                    if (i + 1 < args.length) {
                        scanArgs.workflow = args[++i];
                    } else {
                        System.err.println("Error: The -w/--workflow parameter requires a value.");
                        return null;
                    }
                    break;
                case "-s":
                case "--service-scope":
                    if (i + 1 < args.length) {
                        scanArgs.serviceScope = args[++i];
                    } else {
                        System.err.println("Error: The -s/--service-scope parameter requires a value.");
                        return null;
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
                default:
                    System.err.println("Unknown parameter: " + args[i]);
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
        
        return new FrameworkScanRequest(scanArgs);
    }
}
