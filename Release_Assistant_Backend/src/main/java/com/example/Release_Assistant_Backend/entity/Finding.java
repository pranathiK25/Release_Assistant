package com.example.Release_Assistant_Backend.entity;


import java.util.List;

/** An AI observation about the package, e.g. an unsupported claim. Stored as JSON on Release. */
public record Finding(String type, String message, List<String> evidence) {}
