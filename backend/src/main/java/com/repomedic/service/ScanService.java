package com.repomedic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomedic.dto.Views;
import com.repomedic.entity.*;
import com.repomedic.repository.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

@Service
public class ScanService {
    private final ScanRecordRepository scans; private final DiagnosticRepository diagnostics; private final RepositoryService repositories;
    private final ProcessExecutionService processes; private final ObjectMapper mapper; private final Executor scanExecutor;
    public ScanService(ScanRecordRepository scans, DiagnosticRepository diagnostics, RepositoryService repositories, ProcessExecutionService processes, ObjectMapper mapper, @Qualifier("scanExecutor") Executor scanExecutor) {
        this.scans=scans; this.diagnostics=diagnostics; this.repositories=repositories; this.processes=processes; this.mapper=mapper; this.scanExecutor=scanExecutor;
    }
    public Views.ScanView start(Long repositoryId, boolean runBuild, boolean runLint) {
        RepositoryRecord repo = repositories.getEntity(repositoryId);
        ScanRecord scan = new ScanRecord(); scan.setRepository(repo); scan.setStatus("QUEUED"); scan.setRunBuild(runBuild); scan.setRunLint(runLint);
        scan = scans.save(scan); Long id = scan.getId();
        scanExecutor.execute(() -> execute(id));
        return view(scan);
    }
    @Transactional
    public void execute(Long id) {
        ScanRecord scan = scans.findById(id).orElse(null); if (scan == null) return;
        scan.setStatus("RUNNING"); scan.setStartedAt(LocalDateTime.now()); scans.save(scan);
        Path path = Path.of(scan.getRepository().getLocalPath()); List<Diagnostic> found = new ArrayList<>();
        try {
            if (!Files.isDirectory(path) || !Files.isRegularFile(path.resolve("package.json"))) throw new IllegalStateException("Repository path or package.json is no longer available.");
            String packageJson = Files.readString(path.resolve("package.json"));
            JsonNode packageNode = mapper.readTree(packageJson);
            JsonNode scripts = packageNode.path("scripts");
            if (!scripts.isObject()) add(found, scan, "CONFIGURATION_ERROR", "ERROR", "package.json", null, "The package manifest has no valid scripts object.", packageJson);
            if (scan.isRunBuild()) runScript(path, scan, found, scripts, "build", "BUILD_ERROR");
            if (scan.isRunLint()) runScript(path, scan, found, scripts, "lint", "LINT_ERROR");
            if (found.isEmpty()) add(found, scan, "NO_DIAGNOSTICS", "INFO", null, null, "No diagnostics were produced by the selected checks. This is not a guarantee that the repository is bug-free.", null);
            diagnostics.saveAll(found);
            scan.setStatus("COMPLETED"); scan.setSummary("Scan completed with " + found.stream().filter(d -> !"INFO".equals(d.getSeverity())).count() + " finding(s). Checks selected: " + selectedChecks(scan) + ".");
        } catch (Exception e) {
            add(found, scan, "SCAN_ERROR", "ERROR", null, null, safeMessage(e), null); diagnostics.saveAll(found);
            scan.setStatus("FAILED"); scan.setSummary("Scan failed: " + safeMessage(e));
        } finally { scan.setCompletedAt(LocalDateTime.now()); scans.save(scan); }
    }
    private void runScript(Path path, ScanRecord scan, List<Diagnostic> found, JsonNode scripts, String name, String type) {
        if (!scripts.has(name)) { add(found, scan, "CHECK_NOT_CONFIGURED", "INFO", "package.json", null, "Skipped " + name + ": no '" + name + "' script is configured.", null); return; }
        // Explicit opt-in is required because package scripts execute repository-controlled code.
        ProcessExecutionService.Result result = processes.run(List.of(isWindows() ? "npm.cmd" : "npm", "run", name), path, Map.of());
        if (result.timedOut() || result.exitCode() != 0) {
            SourceLocation location = parseLocation(result.output());
            add(found, scan, type, "ERROR", location == null ? null : location.path(), location == null ? null : location.line(), name + " check " + (result.timedOut() ? "timed out" : "failed with exit code " + result.exitCode()) + ".", result.output());
        } else add(found, scan, "CHECK_PASSED", "INFO", null, null, name + " check passed.", result.output());
    }
    private record SourceLocation(String path, Integer line) {}
    private SourceLocation parseLocation(String output) {
        if (output == null) return null;
        java.util.regex.Pattern[] patterns = new java.util.regex.Pattern[] {
            java.util.regex.Pattern.compile("(?:^|[\\s(])((?:[A-Za-z]:)?[\\w./\\\\-]+\\.(?:tsx?|jsx?))\\((\\d+),(\\d+)\\):"),
            java.util.regex.Pattern.compile("(?:^|[\\s(])((?:[A-Za-z]:)?[\\w./\\\\-]+\\.(?:tsx?|jsx?)):(\\d+):(\\d+)")
        };
        for (java.util.regex.Pattern pattern : patterns) {
            java.util.regex.Matcher matcher = pattern.matcher(output);
            if (matcher.find()) {
                String file = matcher.group(1).replace('\\', '/');
                int line; try { line = Integer.parseInt(matcher.group(2)); } catch (NumberFormatException e) { line = 1; }
                while (file.startsWith("./")) file = file.substring(2);
                return new SourceLocation(file, line);
            }
        }
        return null;
    }
    private boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
    private String selectedChecks(ScanRecord s) { List<String> a=new ArrayList<>(); if(s.isRunBuild())a.add("build"); if(s.isRunLint())a.add("lint"); return a.isEmpty()?"manifest inspection only":String.join(", ",a); }
    private void add(List<Diagnostic> list, ScanRecord scan, String type, String severity, String file, Integer line, String message, String evidence) {
        Diagnostic d=new Diagnostic(); d.setScan(scan); d.setErrorType(type); d.setSeverity(severity); d.setFilePath(file); d.setLineNumber(line); d.setMessage(message); d.setEvidence(truncate(evidence, 12000)); list.add(d);
    }
    private String truncate(String s, int max) { if(s==null)return null; return s.length()>max?s.substring(0,max)+"\n[truncated]":s; }
    private String safeMessage(Exception e) { return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    @Transactional(readOnly=true) public Views.ScanView get(Long id) { return view(scans.findById(id).orElseThrow(() -> new IllegalArgumentException("Scan not found: " + id))); }
    @Transactional(readOnly=true) public List<Views.ScanView> recent() { return scans.findTop50ByOrderByCreatedAtDesc().stream().map(this::view).toList(); }
    @Transactional(readOnly=true) public List<Views.ScanView> forRepository(Long id) { return scans.findByRepositoryIdOrderByCreatedAtDesc(id).stream().map(this::view).toList(); }
    @Transactional(readOnly=true) public List<Views.DiagnosticView> diagnosticViews(Long id) { if(!scans.existsById(id)) throw new IllegalArgumentException("Scan not found: " + id); return diagnostics.findByScanIdOrderByIdAsc(id).stream().map(this::viewDiagnostic).toList(); }
    public ScanRecord getEntity(Long id) { return scans.findById(id).orElseThrow(() -> new IllegalArgumentException("Scan not found: " + id)); }
    public Diagnostic getDiagnostic(Long id) { return diagnostics.findById(id).orElseThrow(() -> new IllegalArgumentException("Diagnostic not found: " + id)); }
    public Views.DiagnosticView viewDiagnostic(Diagnostic d) { return new Views.DiagnosticView(d.getId(), d.getScan().getId(), d.getErrorType(), d.getSeverity(), d.getFilePath(), d.getLineNumber(), d.getMessage(), d.getEvidence()); }
    public Views.ScanView view(ScanRecord s) { return new Views.ScanView(s.getId(), s.getRepository().getId(), s.getRepository().getName(), s.getStatus(), s.getSummary(), s.isRunBuild(), s.isRunLint(), s.getStartedAt(), s.getCompletedAt(), s.getCreatedAt(), diagnostics.findByScanIdOrderByIdAsc(s.getId()).size()); }
}
