package com.repomedic.controller;

import com.repomedic.dto.Views;
import com.repomedic.repository.RepositoryRecordRepository;
import com.repomedic.service.OllamaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.Map;

@RestController @RequestMapping("/api")
public class SystemController {
    private final RepositoryRecordRepository repositories; private final OllamaService ai;
    @Value("${spring.ai.ollama.base-url:http://localhost:11434}") private String ollamaUrl;
    public SystemController(RepositoryRecordRepository repositories, OllamaService ai) { this.repositories=repositories; this.ai=ai; }
    @GetMapping("/health") public Views.HealthView health() {
        boolean db; try { repositories.count(); db=true; } catch(Exception e) { db=false; }
        boolean ollama=false; String message="Backend available";
        try { RestClient.create(ollamaUrl).get().uri("/api/tags").retrieve().body(Map.class); ollama=true; } catch(Exception e) { message="Ollama is not reachable at configured address."; }
        return new Views.HealthView(true,db,ollama,ai.modelName(),message);
    }
    @GetMapping("/settings/model") public Map<String,Object> modelStatus() {
        try { Map body=RestClient.create(ollamaUrl).get().uri("/api/tags").retrieve().body(Map.class); return Map.of("reachable",true,"configuredModel",ai.modelName(),"models",body==null?Map.of():body); }
        catch(Exception e) { return Map.of("reachable",false,"configuredModel",ai.modelName(),"message","Ollama is not reachable. Start Ollama and verify OLLAMA_BASE_URL."); }
    }
}
