package com.example.Release_Assistant_Backend.dto;

import com.example.Release_Assistant_Backend.entity.BriefStatement;

import java.util.List;
import java.util.Map;

public record CompareResult(
        String from,
        String to,
        Map<String, SectionDiff> sections,
        List<BriefStatement> staleStatements) {

    public record SectionDiff(List<String> added, List<String> removed) {}
}
