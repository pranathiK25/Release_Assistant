package com.example.Release_Assistant_Backend.service;

import com.example.Release_Assistant_Backend.ai.AiService;
import com.example.Release_Assistant_Backend.dto.CompareResult;
import com.example.Release_Assistant_Backend.dto.FinalBrief;
import com.example.Release_Assistant_Backend.dto.ReleaseRequest;
import com.example.Release_Assistant_Backend.dto.ReviewRequest;
import com.example.Release_Assistant_Backend.entity.BriefStatement;
import com.example.Release_Assistant_Backend.entity.ChangeImpact;
import com.example.Release_Assistant_Backend.entity.Finding;
import com.example.Release_Assistant_Backend.entity.Release;
import com.example.Release_Assistant_Backend.repo.ReleaseRepo;
import com.example.Release_Assistant_Backend.repo.StatementRepo;
import com.example.Release_Assistant_Backend.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class ReleaseService {
    private static final Set<String> IMPACTS = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> LEVELS = Set.of("HIGH", "MEDIUM", "LOW", "NONE");

    /** Section code to the heading used in the final brief, in display order. */
    private static final Map<String, String> TITLES = new LinkedHashMap<>();
    static {
        TITLES.put("NEW", "What's new");
        TITLES.put("IMPROVEMENT", "Fixes and improvements");
        TITLES.put("CHANGE", "Changed behaviour");
        TITLES.put("LIMITATION", "Known limitations");
        TITLES.put("MIGRATION", "Migration and configuration");
        TITLES.put("QA", "QA coverage");
    }

    private final ReleaseRepo releases;
    private final StatementRepo statements;
    private final AiService ai;

    // ---------- create / read ----------

    public Release create(ReleaseRequest req) {
        String version = req.getVersion().trim();
        if (releases.existsByVersion(version))
            throw new ValidationException(List.of(
                    "Version " + version + " already exists. Each version is saved once; use a new version number."));

        Release r = new Release();
        r.setVersion(version);
        r.setCompletedFeatures(trimmed(req.getCompletedFeatures()));
        r.setBugFixes(trimmed(req.getBugFixes()));
        r.setChangedBehaviour(trimmed(req.getChangedBehaviour()));
        r.setQaSummary(trimmed(req.getQaSummary()));
        r.setKnownLimitations(trimmed(req.getKnownLimitations()));
        r.setMigrationNotes(trimmed(req.getMigrationNotes()));
        r.setAffectedUsers(trimmed(req.getAffectedUsers()));
        return releases.save(r);
    }

    private List<String> trimmed(List<String> in) {
        return in == null ? new ArrayList<>() : new ArrayList<>(in.stream().map(String::trim).toList());
    }

    @Transactional(readOnly = true)
    public List<Release> list() {
        return releases.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Release get(Long id) {
        return releases.findById(id).orElseThrow(() -> new NoSuchElementException("Release " + id + " not found."));
    }

    // ---------- AI generation ----------

    /** Runs the AI, links evidence, and marks older statements that this release makes stale. */
    public Release generate(Long id) {
        Release r = get(id);
        if ("REVIEWED".equals(r.getStatus()))
            throw new IllegalStateException("This release has been finalized. Create a new version instead.");

        Map<String, String> valid = Evidence.index(r);
        AiService.Analysis a = ai.analyze(r);

        String impact = a.impact() == null ? "" : a.impact().toUpperCase();
        r.setImpact(IMPACTS.contains(impact) ? impact : "MEDIUM");
        r.setImpactRationale(a.impactRationale());

        List<ChangeImpact> impacts = new ArrayList<>();
        if (a.changeImpacts() != null)
            for (ChangeImpact c : a.changeImpacts()) {
                if (c.evidenceId() == null || !valid.containsKey(c.evidenceId())) continue;
                String level = c.level() == null ? "" : c.level().toUpperCase();
                impacts.add(new ChangeImpact(c.evidenceId(), LEVELS.contains(level) ? level : "MEDIUM", c.rationale()));
            }
        r.setChangeImpacts(impacts);

        r.setMissingInformation(a.missingInformation() == null
                ? new ArrayList<>() : new ArrayList<>(a.missingInformation()));

        List<Finding> findings = new ArrayList<>();
        if (a.findings() != null)
            for (Finding f : a.findings())
                findings.add(new Finding(f.type(), f.message(), keepValid(f.evidence(), valid)));
        r.setFindings(findings);

        r.getStatements().clear();
        if (a.statements() != null) {
            for (AiService.Draft d : a.statements()) {
                String audience = d.audience() == null ? "" : d.audience().toUpperCase();
                if (!audience.equals("TECHNICAL") && !audience.equals("CLIENT")) continue;
                if (d.text() == null || d.text().isBlank()) continue;

                String section = d.section() == null ? "" : d.section().toUpperCase();
                BriefStatement s = new BriefStatement();
                s.setRelease(r);
                s.setAudience(audience);
                s.setSection(TITLES.containsKey(section) ? section : "CHANGE");
                s.setText(d.text().trim());
                s.setOriginalText(s.getText());
                s.setEvidence(keepValid(d.evidence(), valid));
                s.setUnsupported(s.getEvidence().isEmpty());
                r.getStatements().add(s);
            }
        }
        r.setStatus("BRIEF_GENERATED");
        Release saved = releases.save(r);
        markStale(saved);
        return saved;
    }

    /** Drops any evidence id the AI invented. */
    private List<String> keepValid(List<String> ids, Map<String, String> valid) {
        if (ids == null) return new ArrayList<>();
        return new ArrayList<>(ids.stream().filter(valid::containsKey).distinct().toList());
    }

    private void markStale(Release newer) {
        Optional<Release> prev = releases.findFirstByIdLessThanOrderByIdDesc(newer.getId());
        if (prev.isEmpty()) return;

        List<BriefStatement> old = prev.get().getStatements();
        for (BriefStatement s : old)
            if (Objects.equals(s.getStaleSource(), newer.getId())) {
                s.setStale(false);
                s.setStaleReason(null);
                s.setStaleSource(null);
            }

        Map<Long, BriefStatement> candidates = new HashMap<>();
        for (BriefStatement s : old)
            if (!s.isStale() && !"REJECTED".equals(s.getStatus())) candidates.put(s.getId(), s);

        for (AiService.StaleItem item : ai.detectStale(newer, new ArrayList<>(candidates.values()))) {
            BriefStatement s = candidates.get(item.id());
            if (s == null) continue;
            s.setStale(true);
            s.setStaleReason(item.reason());
            s.setStaleSource(newer.getId());
        }
    }

    // ---------- human review ----------

    public BriefStatement review(Long statementId, ReviewRequest req) {
        BriefStatement s = statements.findById(statementId)
                .orElseThrow(() -> new NoSuchElementException("Statement " + statementId + " not found."));
        if ("REVIEWED".equals(s.getRelease().getStatus()))
            throw new IllegalStateException("This release has been finalized. Statements can no longer change.");

        if (req.getText() != null && !req.getText().isBlank() && !req.getText().trim().equals(s.getText())) {
            s.setText(req.getText().trim());
            s.setStatus("EDITED");
        }
        if (req.getStatus() != null) s.setStatus(req.getStatus());
        return statements.save(s);
    }

    /** Only a named person can mark a release reviewed. The AI never calls this. */
    public Release finalizeRelease(Long id, String reviewer) {
        Release r = get(id);
        if (r.getStatements().isEmpty())
            throw new ValidationException(List.of("Generate a brief before finalizing."));

        long pending = r.getStatements().stream().filter(s -> "PENDING".equals(s.getStatus())).count();
        if (pending > 0)
            throw new ValidationException(List.of(
                    pending + " statement(s) still need to be accepted, edited or rejected."));

        long uncited = r.getStatements().stream()
                .filter(s -> !"REJECTED".equals(s.getStatus()) && s.isUnsupported()).count();
        if (uncited > 0)
            throw new ValidationException(List.of(
                    uncited + " kept statement(s) cite no evidence. Reject them, or regenerate the brief."));

        r.setStatus("REVIEWED");
        r.setReviewedBy(reviewer.trim());
        r.setReviewedAt(Instant.now());
        return releases.save(r);
    }

    // ---------- final brief ----------

    @Transactional(readOnly = true)
    public FinalBrief finalBrief(Long id) {
        Release r = get(id);
        if (!"REVIEWED".equals(r.getStatus()))
            throw new IllegalStateException("Finalize the review before opening the final brief.");

        List<BriefStatement> kept = r.getStatements().stream()
                .filter(s -> !"REJECTED".equals(s.getStatus())).toList();
        Map<String, List<FinalBrief.Line>> technical = group(kept, "TECHNICAL");
        Map<String, List<FinalBrief.Line>> client = group(kept, "CLIENT");

        List<Finding> risks = nz(r.getFindings()).stream().filter(f -> "RISK".equals(f.type())).toList();
        List<Finding> unsupported = nz(r.getFindings()).stream()
                .filter(f -> "UNSUPPORTED_CLAIM".equals(f.type())).toList();
        List<ChangeImpact> impacts = nz(r.getChangeImpacts());
        List<String> missing = nz(r.getMissingInformation());

        Set<String> cited = new HashSet<>();
        for (var m : List.of(technical, client))
            m.values().forEach(lines -> lines.forEach(l -> cited.addAll(l.evidence())));
        for (Finding f : nz(r.getFindings())) cited.addAll(nz(f.evidence()));
        impacts.forEach(c -> cited.add(c.evidenceId()));

        Map<String, String> evidence = new LinkedHashMap<>();
        Evidence.index(r).forEach((k, v) -> { if (cited.contains(k)) evidence.put(k, v); });

        String markdown = toMarkdown(r, technical, client, risks, unsupported, missing, impacts, evidence);
        return new FinalBrief(r.getVersion(), r.getReviewedBy(), r.getReviewedAt(), r.getImpact(),
                impacts, technical, client, risks, unsupported, missing, evidence, markdown);
    }

    private Map<String, List<FinalBrief.Line>> group(List<BriefStatement> list, String audience) {
        Map<String, List<FinalBrief.Line>> out = new LinkedHashMap<>();
        TITLES.forEach((section, title) -> {
            List<FinalBrief.Line> lines = list.stream()
                    .filter(s -> audience.equals(s.getAudience()) && section.equals(s.getSection()))
                    .map(s -> new FinalBrief.Line(s.getText(), s.getEvidence(), !s.getText().equals(s.getOriginalText())))
                    .toList();
            if (!lines.isEmpty()) out.put(title, lines);
        });
        return out;
    }

    private <T> List<T> nz(List<T> l) {
        return l == null ? List.of() : l;
    }

    private String cite(List<String> ev) {
        return ev == null || ev.isEmpty() ? "" : " [" + String.join(", ", ev) + "]";
    }

    private void audienceMd(StringBuilder sb, String heading, Map<String, List<FinalBrief.Line>> groups) {
        sb.append("## ").append(heading).append("\n\n");
        if (groups.isEmpty()) sb.append("_No approved statements._\n\n");
        groups.forEach((title, lines) -> {
            sb.append("### ").append(title).append("\n");
            lines.forEach(l -> sb.append("- ").append(l.text()).append(cite(l.evidence())).append('\n'));
            sb.append('\n');
        });
    }

    private String toMarkdown(Release r, Map<String, List<FinalBrief.Line>> technical,
                              Map<String, List<FinalBrief.Line>> client, List<Finding> risks,
                              List<Finding> unsupported, List<String> missing, List<ChangeImpact> impacts,
                              Map<String, String> evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Release v").append(r.getVersion()).append(" - reviewed brief\n\n");
        sb.append("Drafted by AI. Reviewed and approved by ").append(r.getReviewedBy())
                .append(" on ").append(r.getReviewedAt()).append(".\n");
        sb.append("Suggested impact (AI, not an approval): ").append(r.getImpact()).append("\n\n");

        audienceMd(sb, "Client summary", client);
        audienceMd(sb, "Internal technical summary", technical);

        sb.append("## Known risks\n\n");
        if (risks.isEmpty()) sb.append("_None identified._\n");
        risks.forEach(f -> sb.append("- ").append(f.message()).append(cite(f.evidence())).append('\n'));

        sb.append("\n## Claims not supported by QA evidence\n\n");
        if (unsupported.isEmpty()) sb.append("_None identified._\n");
        unsupported.forEach(f -> sb.append("- ").append(f.message()).append(cite(f.evidence())).append('\n'));

        sb.append("\n## Missing information\n\n");
        if (missing.isEmpty()) sb.append("_None identified._\n");
        missing.forEach(m -> sb.append("- ").append(m).append('\n'));

        sb.append("\n## Change impact\n\n");
        impacts.forEach(c -> sb.append("- ").append(c.evidenceId()).append(" (").append(c.level()).append("): ")
                .append(c.rationale()).append('\n'));

        sb.append("\n## Evidence\n\n");
        evidence.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    // ---------- compare ----------

    @Transactional(readOnly = true)
    public CompareResult compare(Long fromId, Long toId) {
        Release from = get(fromId), to = get(toId);
        Map<String, List<String>> a = Evidence.sections(from), b = Evidence.sections(to);

        Map<String, CompareResult.SectionDiff> diff = new LinkedHashMap<>();
        for (String label : a.keySet()) {
            List<String> added = new ArrayList<>(b.get(label));
            added.removeAll(a.get(label));
            List<String> removed = new ArrayList<>(a.get(label));
            removed.removeAll(b.get(label));
            if (!added.isEmpty() || !removed.isEmpty())
                diff.put(label, new CompareResult.SectionDiff(added, removed));
        }
        List<BriefStatement> stale = from.getStatements().stream()
                .filter(s -> s.isStale() && Objects.equals(s.getStaleSource(), to.getId()))
                .toList();
        return new CompareResult(from.getVersion(), to.getVersion(), diff, stale);
    }
}