package com.wrlus.vulscan;

import com.wrlus.vulscan.mcp.JadxMcpClient;
import com.wrlus.vulscan.common.BaseScanner;
import com.wrlus.vulscan.mcp.McpException;
import com.wrlus.vulscan.scan.FrameworkPromptResolver;
import com.wrlus.vulscan.scan.FrameworkScanRequest;
import com.wrlus.vulscan.utils.Log;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FrameworkScanMain extends BaseScanner {
    
    private final JadxMcpClient jadxMcpClient;

    public FrameworkScanMain(FrameworkScanRequest request) {
        super(request, new FrameworkPromptResolver());
        jadxMcpClient = JadxMcpClient.getInstance();
    }

    private boolean inServiceScope(String service_aidl, String service_scope) {
        if (service_scope == null) {
            return true;
        }

        File serviceScopeFile = new File(service_scope);
        if (!serviceScopeFile.exists() || !serviceScopeFile.isFile()) {
            return true;
        }

        try {
            List<String> lines = Files.readAllLines(Paths.get(service_scope));
            for (String line : lines) {
                String cleanedLine = line.trim();
                if (cleanedLine.contains(service_aidl)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            Log.w("Failed reading service scope file " + service_scope + ": " + e.getMessage());
            return true;
        }
    }

    private List<String> searchAccessibleAidl(String instanceId, String service_scope) throws McpException {
        List<String> accessibleAidl = new ArrayList<>();

        List<String> aidlClasses = jadxMcpClient.searchAidlClasses(instanceId);

        for (String aidlClass : aidlClasses) {
            if (inServiceScope(aidlClass, service_scope)) {
                try {
                    String implClass = jadxMcpClient.getAidlImplClass(instanceId, aidlClass);
                    List<String> aidlMethods = jadxMcpClient.getAidlMethods(instanceId, aidlClass);
                    List<String> aidlImplMethods = aidlMethods.stream()
                            .map(method -> method.replace(aidlClass, implClass))
                            .toList();

                    Log.i("Found AIDL class impl: " + implClass + ". Added " + aidlImplMethods.size() + " AIDL methods.");
                    accessibleAidl.addAll(aidlImplMethods);
                } catch (McpException | RuntimeException e) {
                    continue;
                }
            }
        }

        if (accessibleAidl.isEmpty()) {
            Log.e("No accessible AIDL found, maybe use wrong service scope file or Android framework binaries?");
        }

        return accessibleAidl;
    }

    private String loadToJadx(String input_file) throws McpException {
        jadxMcpClient.unloadAll();
        if (input_file.endsWith(".apk") || input_file.endsWith(".jar") || input_file.endsWith(".dex")) {
            return jadxMcpClient.load(input_file);
        } else {
            return jadxMcpClient.loadDir(input_file);
        }
    }

    @Override
    protected List<String> executeFindEntryFunction(String entryType, Object entryItem) throws IOException, McpException {
        List<String> result;
        jadxMcpClient.open();
        if ("search_aidl_classes".equals(entryType)) {
            String instanceId = loadToJadx(request.getInputFile());
            result = searchAccessibleAidl(instanceId, ((FrameworkScanRequest) request).getServiceScope());
        } else {
            Log.e("Invalid entry type parameter, received: " + entryType);
            result = new ArrayList<>();
        }
        jadxMcpClient.close();
        return result;
    }

    public static void main(String[] args) {
        FrameworkScanRequest request = FrameworkScanRequest.parseFromArguments(args);
        if (request != null) {
            FrameworkScanMain main = new FrameworkScanMain(request);
            main.scan();
        }
    }
}
