package com.repomedic.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class Views {
    private Views() {}
    public record RepositoryView(Long id, String name, String localPath, String projectType, LocalDateTime createdAt) {}
    public record DiagnosticView(Long id, Long scanId, String errorType, String severity, String filePath, Integer lineNumber, String message, String evidence) {}
    public record ScanView(Long id, Long repositoryId, String repositoryName, String status, String summary, boolean runBuild, boolean runLint, LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime createdAt, long diagnosticsCount) {}
    public record RepairView(Long id, Long scanId, Long diagnosticId, String status, String explanation, String patchJson, String modelName, String validationSummary, LocalDateTime createdAt) {}
    public record ValidationView(Long id, Long repairId, String commandName, String status, Integer exitCode, String outputSummary, LocalDateTime executedAt) {}
    public record HealthView(boolean backend, boolean database, boolean ollamaReachable, String model, String message) {}
    public record PatchFile(String path, String content, String reason) {}
    public record RepairProposal(String explanation, List<PatchFile> files, String cautions) {}
}
