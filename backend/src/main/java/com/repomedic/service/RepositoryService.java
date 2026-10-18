package com.repomedic.service;

import com.repomedic.entity.RepositoryRecord;
import com.repomedic.repository.RepositoryRecordRepository;
import com.repomedic.dto.Views;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.*;

@Service
public class RepositoryService {
    private final RepositoryRecordRepository repository;
    public RepositoryService(RepositoryRecordRepository repository) { this.repository = repository; }

    public Views.RepositoryView register(String rawPath) {
        try {
            Path path = Paths.get(rawPath).toRealPath();
            if (!Files.isDirectory(path)) throw new IllegalArgumentException("Selected path is not a directory.");
            if (!Files.isRegularFile(path.resolve("package.json"))) throw new IllegalArgumentException("No package.json found. The MVP currently supports JavaScript/TypeScript repositories.");
            if (!Files.isDirectory(path.resolve(".git")) && !Files.isRegularFile(path.resolve(".git"))) throw new IllegalArgumentException("A Git repository is required for safe repair workflows. Initialize Git or select a repository clone.");
            String canonical = path.toString();
            if (repository.findByLocalPath(canonical).isPresent()) throw new IllegalArgumentException("This repository is already registered.");
            RepositoryRecord record = new RepositoryRecord();
            record.setLocalPath(canonical); record.setName(path.getFileName() == null ? canonical : path.getFileName().toString()); record.setProjectType(detectType(path));
            return view(repository.save(record));
        } catch (InvalidPathException e) { throw new IllegalArgumentException("Repository path is invalid."); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Repository path cannot be accessed."); }
    }
    public List<Views.RepositoryView> list() { return repository.findAll().stream().map(this::view).toList(); }
    public RepositoryRecord getEntity(Long id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id)); }
    public Views.RepositoryView view(RepositoryRecord r) { return new Views.RepositoryView(r.getId(), r.getName(), r.getLocalPath(), r.getProjectType(), r.getCreatedAt()); }
    private String detectType(Path p) {
        try {
            String pkg = Files.readString(p.resolve("package.json"));
            boolean vite = pkg.contains("vite"); boolean react = pkg.contains("react"); boolean next = pkg.contains("next"); boolean ts = Files.exists(p.resolve("tsconfig.json"));
            if (next) return ts ? "NEXTJS_TYPESCRIPT" : "NEXTJS";
            if (vite && react) return ts ? "VITE_REACT_TYPESCRIPT" : "VITE_REACT";
            if (react) return ts ? "REACT_TYPESCRIPT" : "REACT";
            return ts ? "TYPESCRIPT" : "NODEJS";
        } catch (Exception e) { return "UNKNOWN"; }
    }
}
