package com.wrlus.vulscan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrlus.vulscan.mcp.McpException;
import com.wrlus.vulscan.utils.Log;
import com.wrlus.vulscan.mcp.JebMcpClient;
import com.wrlus.vulscan.common.BaseScanner;
import com.wrlus.vulscan.scan.AppScanRequest;
import com.wrlus.vulscan.scan.AppPromptResolver;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AppScanMain extends BaseScanner {

    private final JebMcpClient jebClient;
    
    public AppScanMain(AppScanRequest request) {
        super(request, new AppPromptResolver());
        jebClient = JebMcpClient.getInstance();
        jebClient.setRemote(request.isRemote());
    }

    @Override
    public int scan() {
        String inputFile = request.getInputFile();

        if (!inputFile.endsWith(".apk")) {
            Log.e("The input file must have the .apk extension: " + inputFile);
            return -1;
        }

        return super.scan();
    }
    
    @Override
    protected List<String> executeFindEntryFunction(String entryType, Object entryItem) throws IOException, McpException {
        String inputFile = request.getInputFile();
        List<String> result;

        jebClient.open();
        switch (entryType) {
            case "activity_exported" ->
                    result = jebClient.getAllExportedActivities(inputFile);
            case "service_exported" ->
                    result = jebClient.getAllExportedServices(inputFile);
            case "get_method_callers" -> {
                result = new ArrayList<>();
                if (entryItem instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> entryItemList = (List<String>) entryItem;
                    ObjectMapper objectMapper = new ObjectMapper();

                    for (String item : entryItemList) {
                        List<Map<String, Object>> callers = jebClient.getMethodCallers(inputFile, item);
                        List<String> parsedCallers = new ArrayList<>();
                        for (Map<String, Object> originalCaller : callers) {
                            parsedCallers.add(objectMapper.writeValueAsString(originalCaller));
                        }
                        result.addAll(parsedCallers);
                    }
                }

                if (result.isEmpty()) {
                    Log.w("No result returned after search with get_method_callers, search methods: " +
                            (entryItem != null ? entryItem.toString() : "null"));
                }
            }
            case "get_method_overrides" -> {
                result = new ArrayList<>();
                if (entryItem instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> entryItemList = (List<String>) entryItem;
                    for (String item : entryItemList) {
                        List<String> overrides = jebClient.getMethodOverrides(inputFile, item);
                        result.addAll(overrides);
                    }
                }

                if (result.isEmpty()) {
                    Log.w("No result returned after search with get_method_overrides, search methods: " +
                            (entryItem != null ? entryItem.toString() : "null"));
                }
            }
            case null, default -> {
                Log.e("Invalid entry type parameter, received: " + entryType);
                result = new ArrayList<>();
            }
        }

        jebClient.close();
        return result;
    }

    public static void main(String[] args) {
        AppScanRequest request = AppScanRequest.parseFromArguments(args);

        if (request != null) {
            if (Files.isDirectory(Paths.get(request.getInputFile()))) {
                try {
                    BaseScanner.scanMultiApks(request);
                } catch (IOException e) {
                    Log.e("Execution exception: " + e.getMessage());
                }
            } else {
                AppScanMain main = new AppScanMain(request);
                main.scan();
            }
        }
    }
}
