package com.repomedic.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ProcessExecutionService {
    @Value("${repomedic.process-timeout-seconds:120}") private long timeoutSeconds;
    @Value("${repomedic.max-output-chars:30000}") private int maxOutputChars;
    public record Result(int exitCode, String output, boolean timedOut) {}

    public Result run(List<String> command, Path workingDirectory, Map<String, String> extraEnv) {
        try {
            List<String> safeCommand = new ArrayList<>(command);
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") && !safeCommand.isEmpty() && safeCommand.get(0).toLowerCase(Locale.ROOT).endsWith(".cmd")) {
                List<String> wrapped = new ArrayList<>(List.of("cmd.exe", "/d", "/s", "/c"));
                wrapped.addAll(safeCommand);
                safeCommand = wrapped;
            }
            ProcessBuilder builder = new ProcessBuilder(safeCommand);
            builder.directory(workingDirectory.toFile()); builder.redirectErrorStream(true);
            builder.environment().put("CI", "true"); builder.environment().putAll(extraEnv == null ? Map.of() : extraEnv);
            Process process = builder.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = process.getInputStream()) {
                    byte[] buffer = new byte[4096]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        synchronized (output) { if (output.size() < maxOutputChars) output.write(buffer, 0, Math.min(n, maxOutputChars - output.size())); }
                    }
                } catch (IOException ignored) {}
            }, "repomedic-process-output");
            reader.setDaemon(true); reader.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
            reader.join(2000);
            String text; synchronized (output) { text = output.toString(StandardCharsets.UTF_8); }
            if (!finished) text += "\n[RepoMedic] Process timed out after " + timeoutSeconds + " seconds.";
            return new Result(finished ? process.exitValue() : -1, text, !finished);
        } catch (Exception e) {
            return new Result(-1, "Could not start process: " + e.getClass().getSimpleName() + ": " + e.getMessage(), false);
        }
    }
}
