package com.repomedic.controller;

import com.repomedic.dto.Requests;
import com.repomedic.dto.Views;
import com.repomedic.service.RepositoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/repositories")
public class RepositoryController {
    private final RepositoryService service;
    public RepositoryController(RepositoryService service) { this.service=service; }
    @GetMapping public List<Views.RepositoryView> list() { return service.list(); }
    @PostMapping public Views.RepositoryView register(@Valid @RequestBody Requests.RegisterRepositoryRequest request) { return service.register(request.path()); }
}
