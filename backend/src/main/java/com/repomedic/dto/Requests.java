package com.repomedic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class Requests {
    private Requests() {}
    public record RegisterRepositoryRequest(@NotBlank String path) {}
    public record StartScanRequest(@NotNull Long repositoryId, boolean runBuild, boolean runLint) {}
    public record GenerateRepairRequest(@NotNull Long diagnosticId) {}
}
