package com.example.Release_Assistant_Backend.entity;

import com.example.Release_Assistant_Backend.service.Evidence;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "releases")
@Getter
@Setter
@NoArgsConstructor
public class Release {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON) private List<String> completedFeatures = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) private List<String> bugFixes = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) private List<String> changedBehaviour = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) private List<String> qaSummary = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) private List<String> knownLimitations = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) private List<String> migrationNotes = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) private List<String> affectedUsers = new ArrayList<>();

    /** AI suggestion only. A person decides whether the release ships. */
    private String impact;
    @Column(length = 2000) private String impactRationale;
    @JdbcTypeCode(SqlTypes.JSON) private List<ChangeImpact> changeImpacts = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) private List<String> missingInformation = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON) private List<Finding> findings = new ArrayList<>();

    /** DRAFT -> BRIEF_GENERATED -> REVIEWED (REVIEWED is only ever set by a named person). */
    private String status = "DRAFT";
    private String reviewedBy;
    private Instant reviewedAt;
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "release", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<BriefStatement> statements = new ArrayList<>();

    /** Evidence id (F1, Q2, ...) to source text. Serialized for the UI. */
    public Map<String, String> getEvidenceIndex() {
        return Evidence.index(this);
    }
}