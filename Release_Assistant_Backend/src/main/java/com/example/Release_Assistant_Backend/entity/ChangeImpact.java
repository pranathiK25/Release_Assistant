package com.example.Release_Assistant_Backend.entity;


/** Per-item user-impact classification. level: HIGH, MEDIUM, LOW or NONE. */
public record ChangeImpact(String evidenceId, String level, String rationale) {}
