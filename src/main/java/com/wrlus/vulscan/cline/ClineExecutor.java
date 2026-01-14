package com.wrlus.vulscan.cline;

import com.wrlus.vulscan.utils.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ClineExecutor {
    private static final String CLINE_EXEC =
            Paths.get(System.getProperty("user.dir"), "cline-exec.js").toString();
    private static final int CLINE_EXEC_INTERVAL = 1; // 秒
    private static final int CLINE_NO_OUTPUT_TIMEOUT = 120; // 秒
    
    private static volatile long lastClineExecTime = 0;
    
    private final String taskPrompt;
    private final String taskId;
    private volatile Process process;

    private final AtomicLong lastOutputTime = new AtomicLong(0);
    private final AtomicBoolean timeoutEvent = new AtomicBoolean(false);

    public ClineExecutor(String taskPrompt, String taskId) {
        this.taskPrompt = taskPrompt;
        this.taskId = taskId;
    }
    
    private static synchronized void controlExecutionInterval() throws InterruptedException {
        long currentTime = System.currentTimeMillis() / 1000; // 转换为秒
        long timeSinceLastExec = currentTime - lastClineExecTime;
        if (timeSinceLastExec < CLINE_EXEC_INTERVAL) {
            long waitTime = CLINE_EXEC_INTERVAL - timeSinceLastExec;
            Thread.sleep(waitTime * 1000); // 转换为毫秒
        }
        lastClineExecTime = System.currentTimeMillis() / 1000;
    }

    private static boolean needRecord(String line) {
        return !line.contains("[user_feedback]") && !line.contains("[mcp_server_response]");
    }
    
    private static void printOutput(String taskId, String line) {
        System.out.println("[Task " + taskId + "] " + line);
    }
    
    private static boolean checkCompletion(String line) {
        return line.contains("[completion_result]") || line.contains("[completion_result_say]");
    }
    
    public int start() {
        int result = 1;
        Map<String, String> environ = new HashMap<>(System.getenv());
        environ.put("TMPDIR", System.getProperty("java.io.tmpdir"));
        environ.put("CLINE_WORKSPACE_DIR", Paths.get(CLINE_EXEC).getParent().toString());
        
        try {
            controlExecutionInterval();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return result;
        }
        
        List<String> command = Arrays.asList("node", CLINE_EXEC, "task", taskPrompt);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(environ);
        pb.redirectErrorStream(true);

        try {
            process = pb.start();
        } catch (IOException e) {
            Log.e("Failed to start the cline process: " + e.getMessage());
            return result;
        }

        lastOutputTime.set(System.currentTimeMillis());

        MonitorThread monitorThread = new MonitorThread();
        monitorThread.setDaemon(true);
        monitorThread.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                lastOutputTime.set(System.currentTimeMillis());
                
                line = line.trim();
                if (needRecord(line)) {
                    printOutput(taskId, line);
                }
                
                if (checkCompletion(line)) {
                    result = 0;
                }
            }
        } catch (IOException e) {
            Log.e("An error occurred while reading the cline output: " + e.getMessage());
        } finally {
            timeoutEvent.set(true);

            if (monitorThread.isAlive()) {
                monitorThread.interrupt();
            }

            if (process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (monitorThread.isAlive()) {
                try {
                    monitorThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            try {
                reader.close();
            } catch (IOException e) {
                Log.e("An error occurred while closing the output stream: " + e.getMessage());
            }
        }

        if (Thread.interrupted()) {
            Log.w("⚠️ The cleanup process may be interrupted.");
        }

        return result;
    }

    private class MonitorThread extends Thread {

        @Override
        public void run() {
            while (!timeoutEvent.get() && !Thread.currentThread().isInterrupted()) {
                long currentLastOutputTime = lastOutputTime.get();

                if (process == null) {
                    continue;
                }

                if (System.currentTimeMillis() - currentLastOutputTime > CLINE_NO_OUTPUT_TIMEOUT * 1000L) {
                    Log.w("Task " + taskId + " has had no output for more than " + CLINE_NO_OUTPUT_TIMEOUT + " seconds, terminating the process.");
                    timeoutEvent.set(true);

                    if (process.isAlive()) {
                        process.destroyForcibly();

                        try {
                            process.waitFor(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    break;
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
