package com.repomedic.controller;

import com.repomedic.dto.Requests;
import com.repomedic.dto.Views;
import com.repomedic.service.OllamaService;
import com.repomedic.service.RepairService;
import com.repomedic.service.ScanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api")
public class RepairController {
    private final RepairService repairs; private final ScanService scans; private final OllamaService ai;
    public RepairController(RepairService repairs, ScanService scans, OllamaService ai) { this.repairs=repairs; this.scans=scans; this.ai=ai; }
    @PostMapping("/diagnostics/{diagnosticId}/explain") public Map<String,String> explain(@PathVariable Long diagnosticId) {
        var d=scans.getDiagnostic(diagnosticId); return Map.of("explanation",ai.diagnose(d.getScan(),d),"model",ai.modelName());
    }
    @PostMapping("/repairs") public Views.RepairView generate(@Valid @RequestBody Requests.GenerateRepairRequest request) { return repairs.generate(request.diagnosticId()); }
    @GetMapping("/repairs/{id}") public Views.RepairView get(@PathVariable Long id) { return repairs.get(id); }
    @GetMapping("/scans/{scanId}/repairs") public List<Views.RepairView> byScan(@PathVariable Long scanId) { return repairs.forScan(scanId); }
    @PostMapping("/repairs/{id}/validate") public Views.RepairView validate(@PathVariable Long id) { return repairs.validate(id); }
    @PostMapping("/repairs/{id}/apply") public Views.RepairView apply(@PathVariable Long id) { return repairs.apply(id); }
    @GetMapping("/repairs/{id}/validations") public List<Views.ValidationView> validationHistory(@PathVariable Long id) { return repairs.validationHistory(id); }
}
