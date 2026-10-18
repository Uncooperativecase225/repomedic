package com.repomedic.controller;

import com.repomedic.dto.Requests;
import com.repomedic.dto.Views;
import com.repomedic.service.ScanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/scans")
public class ScanController {
    private final ScanService service;
    public ScanController(ScanService service) { this.service=service; }
    @GetMapping public List<Views.ScanView> recent() { return service.recent(); }
    @PostMapping public Views.ScanView start(@Valid @RequestBody Requests.StartScanRequest request) { return service.start(request.repositoryId(), request.runBuild(), request.runLint()); }
    @GetMapping("/{id}") public Views.ScanView get(@PathVariable Long id) { return service.get(id); }
    @GetMapping("/{id}/diagnostics") public List<Views.DiagnosticView> diagnostics(@PathVariable Long id) { return service.diagnosticViews(id); }
    @GetMapping("/repository/{repositoryId}") public List<Views.ScanView> byRepository(@PathVariable Long repositoryId) { return service.forRepository(repositoryId); }
}
