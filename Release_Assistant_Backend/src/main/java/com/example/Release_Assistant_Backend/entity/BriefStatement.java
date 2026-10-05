package com.example.Release_Assistant_Backend.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "brief_statements")
@Getter
@Setter
@NoArgsConstructor
public class BriefStatement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private Release release;

    /** TECHNICAL or CLIENT */
    private String audience;
    /** NEW, IMPROVEMENT, CHANGE, LIMITATION, QA, MIGRATION */
    private String section;

    @Column(length = 2000) private String text;
    @Column(length = 2000) private String originalText;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> evidence = new ArrayList<>();
    /** True when no valid evidence id could be linked to this statement. */
    private boolean unsupported;

    /** PENDING, ACCEPTED, REJECTED, EDITED */
    private String status = "PENDING";

    private boolean stale;
    @Column(length = 1000) private String staleReason;
    /** Id of the newer release that made this statement stale. */
    private Long staleSource;
}