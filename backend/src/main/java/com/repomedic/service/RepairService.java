package com.repomedic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomedic.dto.Views;
import com.repomedic.entity.*;
import com.repomedic.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class RepairService {
    private final RepairRepository repairs; private final ScanService scans; private final RepositoryService repositories; private final OllamaService ai;
    private final ProcessExecutionService processes; private final ValidationResultRepository validations; private final ObjectMapper mapper;
    public RepairService(RepairRepository repairs, ScanService scans, RepositoryService repositories, OllamaService ai, ProcessExecutionService processes, ValidationResultRepository validations, ObjectMapper mapper) {
        this.repairs=repairs; this.scans=scans; this.repositories=repositories; this.ai=ai; this.processes=processes; this.validations=validations; this.mapper=mapper;
    }
    @Transactional
    public Views.RepairView generate(Long diagnosticId) {
        Diagnostic d=scans.getDiagnostic(diagnosticId); ScanRecord scan=d.getScan(); Path root=Path.of(scan.getRepository().getLocalPath()).toAbsolutePath().normalize();
        String head=git(root,"rev-parse","HEAD").trim();
        Views.RepairProposal proposal=ai.propose(scan,d,root,head);
        Map<String,Object> stored=new LinkedHashMap<>(); stored.put("baseCommit",head); stored.put("files",proposal.files()); stored.put("cautions",proposal.cautions());
        Repair repair=new Repair(); repair.setScan(scan); repair.setDiagnostic(d); repair.setStatus(proposal.files().isEmpty()?"NEEDS_INPUT":"PROPOSED"); repair.setExplanation(proposal.explanation()); repair.setPatchJson(write(stored)); repair.setModelName(ai.modelName());
        return view(repairs.save(repair));
    }
    @Transactional(readOnly=true) public Views.RepairView get(Long id) { return view(repairs.findById(id).orElseThrow(() -> new IllegalArgumentException("Repair not found: " + id))); }
    @Transactional(readOnly=true) public List<Views.RepairView> forScan(Long id) { return repairs.findByScanIdOrderByCreatedAtDesc(id).stream().map(this::view).toList(); }
    @Transactional
    public Views.RepairView validate(Long id) {
        Repair repair=repairs.findById(id).orElseThrow(() -> new IllegalArgumentException("Repair not found: " + id));
        if(!"PROPOSED".equals(repair.getStatus()) && !"VALIDATION_FAILED".equals(repair.getStatus())) throw new IllegalStateException("Only proposed repairs can be validated.");
        ScanRecord scan=repair.getScan(); Path root=Path.of(scan.getRepository().getLocalPath()).toAbsolutePath().normalize();
        Map<String,Object> payload=readMap(repair.getPatchJson()); String base=(String)payload.get("baseCommit");
        String current=git(root,"rev-parse","HEAD").trim(); if(!Objects.equals(base,current)) throw new IllegalStateException("Repository HEAD changed since the repair was generated. Generate a new repair against the current version.");
        String status="FAILED", summary="", command="worktree validation"; Path worktree=null;
        try {
            String dirty=git(root,"status","--porcelain"); if(!dirty.isBlank()) throw new IllegalStateException("Repository has uncommitted changes. Commit or stash them before validating a repair.");
            Path parent=root.getParent().resolve(".repomedic-worktrees"); Files.createDirectories(parent); worktree=parent.resolve("repair-"+id+"-"+UUID.randomUUID().toString().substring(0,8));
            ProcessExecutionService.Result add=processes.run(List.of("git","worktree","add","--detach",worktree.toString(),base),root,Map.of());
            if(add.exitCode()!=0) throw new IllegalStateException("Could not create isolated worktree: "+add.output());
            List<Views.PatchFile> files=mapper.convertValue(payload.get("files"),new TypeReference<List<Views.PatchFile>>(){});
            for(Views.PatchFile f:files) { OllamaService.validateRelativePath(f.path()); Path target=safeResolve(worktree,f.path()); if(f.content()==null) throw new IllegalArgumentException("Patch content is empty or invalid for "+f.path()); Files.createDirectories(target.getParent()); Files.writeString(target,f.content(),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING); }
            Path nodeModules=root.resolve("node_modules"); Path workModules=worktree.resolve("node_modules");
            if(Files.isDirectory(nodeModules)) { try { Files.createSymbolicLink(workModules,nodeModules); } catch(Exception ignored) { /* validation will report missing dependencies if unavailable */ } }
            List<String> checks=new ArrayList<>(); Path pkg=worktree.resolve("package.json");
            com.fasterxml.jackson.databind.JsonNode packageNode=mapper.readTree(Files.readString(pkg));
            com.fasterxml.jackson.databind.JsonNode scripts=packageNode.path("scripts");
            if(scripts.has("build")) checks.add("build");
            if(scripts.has("lint")) checks.add("lint");
            if(checks.isEmpty()) checks.add("build");
            List<String> outcomes=new ArrayList<>(); boolean all=true;
            for(String check:checks) {
                ProcessExecutionService.Result result=processes.run(List.of(isWindows()?"npm.cmd":"npm","run",check),worktree,Map.of());
                ValidationResult vr=new ValidationResult(); vr.setRepair(repair); vr.setCommandName("npm run "+check); vr.setExitCode(result.exitCode()); vr.setStatus(result.timedOut()?"TIMED_OUT":result.exitCode()==0?"PASSED":"FAILED"); vr.setOutputSummary(limit(result.output(),10000)); validations.save(vr);
                outcomes.add("npm run "+check+": "+vr.getStatus()+" (exit "+result.exitCode()+")"); if(!"PASSED".equals(vr.getStatus())) all=false;
            }
            status=all?"VALIDATED":"VALIDATION_FAILED"; summary=String.join("; ",outcomes); repair.setStatus(status); repair.setValidationSummary(summary);
        } catch(Exception e) { status="VALIDATION_FAILED"; summary=e.getMessage()==null?"Validation could not be completed.":e.getMessage(); repair.setStatus(status); repair.setValidationSummary(limit(summary,2000)); }
        finally { if(worktree!=null && Files.exists(worktree)) { processes.run(List.of("git","worktree","remove","--force",worktree.toString()),root,Map.of()); try { deleteTree(worktree); } catch(Exception ignored) {} } }
        return view(repairs.save(repair));
    }
    @Transactional
    public Views.RepairView apply(Long id) {
        Repair repair=repairs.findById(id).orElseThrow(() -> new IllegalArgumentException("Repair not found: " + id));
        if(!"VALIDATED".equals(repair.getStatus())) throw new IllegalStateException("Only a repair that passed validation can be applied.");
        Path root=Path.of(repair.getScan().getRepository().getLocalPath()).toAbsolutePath().normalize(); Map<String,Object> payload=readMap(repair.getPatchJson());
        String base=(String)payload.get("baseCommit"); if(!Objects.equals(base,git(root,"rev-parse","HEAD").trim())) throw new IllegalStateException("Repository HEAD changed. Re-scan and generate a new repair.");
        if(!git(root,"status","--porcelain").isBlank()) throw new IllegalStateException("Repository has uncommitted changes. Commit or stash them before applying a repair.");
        List<Views.PatchFile> files=mapper.convertValue(payload.get("files"),new TypeReference<List<Views.PatchFile>>(){});
        Map<Path,byte[]> backups=new LinkedHashMap<>(); List<Path> created=new ArrayList<>();
        try {
            for(Views.PatchFile f:files) {
                OllamaService.validateRelativePath(f.path()); Path target=safeResolve(root,f.path());
                if(f.content()==null) throw new IllegalArgumentException("Patch content is empty or invalid for "+f.path());
                if(Files.exists(target)) backups.put(target,Files.readAllBytes(target)); else created.add(target);
            }
            for(Views.PatchFile f:files) { Path target=root.resolve(f.path()).normalize(); Files.createDirectories(target.getParent()); Files.writeString(target,f.content(),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING); }
            repair.setStatus("APPLIED"); repair.setValidationSummary((repair.getValidationSummary()==null?"":repair.getValidationSummary()+" ")+"Applied to working tree by user approval.");
        } catch(Exception e) {
            for(var entry:backups.entrySet()) try { Files.write(entry.getKey(),entry.getValue()); } catch(IOException ignored) {}
            for(Path p:created) try { Files.deleteIfExists(p); } catch(IOException ignored) {}
            throw new IllegalStateException("Could not apply patch; attempted to restore previous file contents. " + e.getMessage());
        }
        return view(repairs.save(repair));
    }
    public List<Views.ValidationView> validationHistory(Long id) { if(!repairs.existsById(id)) throw new IllegalArgumentException("Repair not found: "+id); return validations.findByRepairIdOrderByExecutedAtDesc(id).stream().map(v->new Views.ValidationView(v.getId(),v.getRepair().getId(),v.getCommandName(),v.getStatus(),v.getExitCode(),v.getOutputSummary(),v.getExecutedAt())).toList(); }
    private Path safeResolve(Path root, String relative) throws IOException {
        Path canonicalRoot = root.toRealPath();
        Path target = canonicalRoot.resolve(relative).normalize();
        if (!target.startsWith(canonicalRoot)) throw new IllegalArgumentException("Patch path escapes the repository.");
        Path cursor = canonicalRoot;
        Path rel = canonicalRoot.relativize(target);
        for (Path part : rel) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new IllegalArgumentException("Patch paths cannot traverse symbolic links.");
        }
        return target;
    }
    private String git(Path root,String... args) { List<String> command=new ArrayList<>(); command.add("git"); command.addAll(Arrays.asList(args)); ProcessExecutionService.Result r=processes.run(command,root,Map.of()); if(r.exitCode()!=0) throw new IllegalStateException("Git command failed: "+r.output()); return r.output(); }
    private Map<String,Object> readMap(String json) { try{return mapper.readValue(json,new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("Stored repair proposal is invalid.");} }
    private String write(Object o) { try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException("Could not store repair proposal.");} }
    private String limit(String s,int n){if(s==null)return "";return s.length()>n?s.substring(0,n)+"\n[truncated]":s;}
    private boolean isWindows(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");}
    private void deleteTree(Path root) throws IOException { if(!Files.exists(root))return; try(var stream=Files.walk(root)){stream.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});} }
    private Views.RepairView view(Repair r){return new Views.RepairView(r.getId(),r.getScan().getId(),r.getDiagnostic()==null?null:r.getDiagnostic().getId(),r.getStatus(),r.getExplanation(),r.getPatchJson(),r.getModelName(),r.getValidationSummary(),r.getCreatedAt());}
}
