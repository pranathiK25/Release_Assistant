package com.example.Release_Assistant_Backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Deterministic input validation (Bean Validation). No AI involved. */
@Data
public class ReleaseRequest {

    @NotBlank(message = "Version is required.")
    @Pattern(regexp = "^$|^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$", message = "Version must look like 1.2.0.")
    private String version;

    private List<@NotBlank(message = "Completed features must not contain empty items.")
    @Size(max = 1000, message = "Each completed feature must be under 1000 characters.") String>
            completedFeatures = new ArrayList<>();

    private List<@NotBlank(message = "Bug fixes must not contain empty items.")
    @Size(max = 1000, message = "Each bug fix must be under 1000 characters.") String>
            bugFixes = new ArrayList<>();

    private List<@NotBlank(message = "Changed behaviour must not contain empty items.")
    @Size(max = 1000, message = "Each changed behaviour item must be under 1000 characters.") String>
            changedBehaviour = new ArrayList<>();

    @NotEmpty(message = "QA summary is required.")
    private List<@NotBlank(message = "QA summary must not contain empty items.")
    @Size(max = 1000, message = "Each QA item must be under 1000 characters.") String>
            qaSummary = new ArrayList<>();

    private List<@NotBlank(message = "Known limitations must not contain empty items.")
    @Size(max = 1000, message = "Each known limitation must be under 1000 characters.") String>
            knownLimitations = new ArrayList<>();

    private List<@NotBlank(message = "Migration notes must not contain empty items.")
    @Size(max = 1000, message = "Each migration note must be under 1000 characters.") String>
            migrationNotes = new ArrayList<>();

    @NotEmpty(message = "Affected user groups are required.")
    private List<@NotBlank(message = "Affected user groups must not contain empty items.")
    @Size(max = 1000, message = "Each user group must be under 1000 characters.") String>
            affectedUsers = new ArrayList<>();

    @JsonIgnore
    @AssertTrue(message = "Add at least one completed feature or bug fix.")
    public boolean isChangesPresent() {
        return (completedFeatures != null && !completedFeatures.isEmpty())
                || (bugFixes != null && !bugFixes.isEmpty());
    }
}