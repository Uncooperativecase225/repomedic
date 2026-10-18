package com.repomedic.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OllamaServicePathValidationTest {
    @Test void acceptsNormalSourcePath() { assertDoesNotThrow(() -> OllamaService.validateRelativePath("src/components/Profile.tsx")); }
    @Test void rejectsParentTraversal() { assertThrows(IllegalArgumentException.class, () -> OllamaService.validateRelativePath("../../.ssh/authorized_keys")); }
    @Test void rejectsAbsolutePath() { assertThrows(IllegalArgumentException.class, () -> OllamaService.validateRelativePath("/etc/passwd")); }
    @Test void rejectsPackageManifest() { assertThrows(IllegalArgumentException.class, () -> OllamaService.validateRelativePath("nested/package.json")); }
    @Test void rejectsGitMetadata() { assertThrows(IllegalArgumentException.class, () -> OllamaService.validateRelativePath(".git/config")); }
    @Test void rejectsEnvironmentFiles() { assertThrows(IllegalArgumentException.class, () -> OllamaService.validateRelativePath("config/.env.production")); }
}
