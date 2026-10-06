package com.example.Release_Assistant_Backend.controller;


import com.example.Release_Assistant_Backend.dto.*;
import com.example.Release_Assistant_Backend.entity.BriefStatement;
import com.example.Release_Assistant_Backend.entity.Release;
import com.example.Release_Assistant_Backend.service.ReleaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "https://release-assistant-frontend.onrender.com"})
@RequiredArgsConstructor
public class ReleaseController {
    private final ReleaseService service;

    /** Validation only. Field errors come back as 400 from the exception handler. */
    @PostMapping("/releases/validate")
    public Map<String, Object> validate(@Valid @RequestBody ReleaseRequest req) {
        return Map.of("valid", true);
    }

    @PostMapping("/releases")
    @ResponseStatus(HttpStatus.CREATED)
    public Release create(@Valid @RequestBody ReleaseRequest req) {
        return service.create(req);
    }

    @GetMapping("/releases")
    public List<Release> list() {
        return service.list();
    }

    @GetMapping("/releases/{id}")
    public Release get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/releases/{id}/generate")
    public Release generate(@PathVariable Long id) {
        return service.generate(id);
    }

    @PostMapping("/releases/{id}/finalize")
    public Release finalizeRelease(@PathVariable Long id, @Valid @RequestBody FinalizeRequest req) {
        return service.finalizeRelease(id, req.getReviewer());
    }

    @GetMapping("/releases/{id}/final-brief")
    public FinalBrief finalBrief(@PathVariable Long id) {
        return service.finalBrief(id);
    }

    @PutMapping("/statements/{id}")
    public BriefStatement review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return service.review(id, req);
    }

    @GetMapping("/compare")
    public CompareResult compare(@RequestParam Long from, @RequestParam Long to) {
        return service.compare(from, to);
    }
}