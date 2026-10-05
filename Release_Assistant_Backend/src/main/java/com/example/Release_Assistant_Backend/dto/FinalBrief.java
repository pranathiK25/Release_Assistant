package com.example.Release_Assistant_Backend.dto;


import com.example.Release_Assistant_Backend.entity.ChangeImpact;
import com.example.Release_Assistant_Backend.entity.Finding;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The reviewed brief: only statements a person accepted or edited. */
public record FinalBrief(
        String version,
        String reviewedBy,
        Instant reviewedAt,
        String impact,
        List<ChangeImpact> changeImpacts,
        Map<String, List<Line>> technical,
        Map<String, List<Line>> client,
        List<Finding> risks,
        List<Finding> unsupportedClaims,
        List<String> missingInformation,
        Map<String, String> evidence,
        String markdown) {

    public record Line(String text, List<String> evidence, boolean edited) {}
}
