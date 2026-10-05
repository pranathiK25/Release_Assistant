package com.example.Release_Assistant_Backend.ai;

import com.example.Release_Assistant_Backend.entity.BriefStatement;
import com.example.Release_Assistant_Backend.entity.ChangeImpact;
import com.example.Release_Assistant_Backend.entity.Finding;
import com.example.Release_Assistant_Backend.entity.Release;
import com.example.Release_Assistant_Backend.service.Evidence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.regex.Pattern;

/**
 * With AI_API_KEY set, calls an OpenAI-compatible chat completions endpoint.
 * Without it, falls back to keyword heuristics so the app can be demoed for free.
 * Either way the AI only drafts and flags: it never approves or deploys anything.
 */
@Service
@Slf4j
public class AiService {

    public record Draft(String audience, String section, String text, List<String> evidence) {}
    public record Analysis(String impact, String impactRationale, List<ChangeImpact> changeImpacts,
                           List<String> missingInformation, List<Finding> findings, List<Draft> statements) {}
    public record StaleItem(Long id, String reason) {}
    public record StaleResponse(List<StaleItem> stale) {}

    private static final String ANALYZE_SYSTEM = """
            You help a developer prepare release communication. You never approve releases and you never
            decide whether a release is ready to ship. A person makes that decision.
            You receive a release package where every item has an evidence id such as F1, B2, Q1.
            An item whose text is "None" means the developer declared that nothing applies.
            Do not write statements about such items.

            Return ONLY a JSON object, no prose, with this shape:
            {
              "impact": "LOW" | "MEDIUM" | "HIGH",
              "impactRationale": "one or two sentences",
              "changeImpacts": [
                {"evidenceId": "F1", "level": "HIGH" | "MEDIUM" | "LOW" | "NONE", "rationale": "short reason"}
              ],
              "missingInformation": ["..."],
              "findings": [
                {"type": "UNSUPPORTED_CLAIM" | "RISK", "message": "...", "evidence": ["F1", "Q1"]}
              ],
              "statements": [
                {"audience": "TECHNICAL" | "CLIENT",
                 "section": "NEW" | "IMPROVEMENT" | "CHANGE" | "LIMITATION" | "QA" | "MIGRATION",
                 "text": "...", "evidence": ["F1"]}
              ]
            }

            Rules:
            - changeImpacts: classify EVERY item in Completed features, Bug fixes and Changed behaviour
              by user impact. HIGH = users must act or existing behaviour breaks or changes.
              MEDIUM = a visible new or changed capability. LOW = a minor visible fix.
              NONE = internal only, no user-visible effect.
            - missingInformation: list what is absent or too vague to write an accurate brief. Examples:
              a change with no QA evidence, behaviour changes with no migration or communication note,
              unclear affected users, a limitation with no workaround. Do not pad: use an empty list if nothing is missing.
            - Every statement must cite at least one evidence id that exists in the package. Never invent ids.
            - Never state more than the evidence supports. If a claim is broader than the QA evidence
              (for example "works on all browsers" while QA lists only Chrome and Edge), add an
              UNSUPPORTED_CLAIM finding that names the gap, cite both the claim and the QA items,
              and keep the statement as narrow as the evidence.
            - Add RISK findings for known risks, for example migration steps with no QA coverage.
            - TECHNICAL statements are for developers and QA: precise and concise. Include QA coverage.
            - CLIENT statements are for non-technical stakeholders: plain language, no internal jargon,
              no ticket ids, no QA statistics.
            - Include real known limitations in both audiences.
            - Impact is a suggestion about user-visible change, migration effort and risk. It is not an approval.
            """;

    private static final String STALE_SYSTEM = """
            You compare an OLDER release's statements with a NEWER release package.
            Return ONLY a JSON object:
            {"stale": [{"id": <statement id>, "reason": "..."}]}
            List only older statements that the newer package contradicts or makes untrue
            (for example a platform or feature that is no longer supported, or behaviour that changed).
            Cite the newer evidence ids in the reason. If no statement is affected, return {"stale": []}.
            """;

    private static final Pattern BROAD =
            Pattern.compile("\\b(all|every|any|always|never|fully|everywhere)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATION =
            Pattern.compile("no longer|removed|dropped|deprecated|not supported|unsupported|discontinued",
                    Pattern.CASE_INSENSITIVE);

    private final ObjectMapper mapper;
    private final RestClient http;   // null in offline mode
    private final String url;
    @Value("${ai.model}")
    private final String model;

    @Value("${ai.api.key}")
    private final String apiKey;
    public AiService(ObjectMapper mapper,
                     @Value("${ai.api.key:}") String apiKey,
                     @Value("${ai.api.url}") String url,
                     @Value("${ai.model}") String model) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.url = url;
        this.model = model;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI_API_KEY is not set: running in offline heuristic mode (no AI calls).");
            this.http = null;
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(120_000);
            this.http = RestClient.builder().requestFactory(factory).build();
        }
    }

    public boolean isLive() {
        return http != null;
    }

    // ---------- public API ----------

    public Analysis analyze(Release release) {
        if (!isLive()) return mockAnalyze(release);
        Analysis a = parse(ask(ANALYZE_SYSTEM, packageText(release)), Analysis.class);
        if (a == null) throw new IllegalStateException("The AI returned an empty analysis.");
        return a;
    }

    public List<StaleItem> detectStale(Release newer, List<BriefStatement> older) {
        if (older.isEmpty()) return List.of();
        if (!isLive()) return mockStale(newer, older);

        StringBuilder sb = new StringBuilder("NEWER RELEASE PACKAGE\n").append(packageText(newer));
        sb.append("\nOLDER STATEMENTS\n");
        for (BriefStatement s : older) sb.append(s.getId()).append(": ").append(s.getText()).append('\n');
        StaleResponse res = parse(ask(STALE_SYSTEM, sb.toString()), StaleResponse.class);
        return res == null || res.stale() == null ? List.of() : res.stale();
    }

    // ---------- live mode: OpenAI chat completions ----------

    private String packageText(Release r) {
        StringBuilder sb = new StringBuilder("Version: ").append(r.getVersion()).append('\n');
        int i = 0;
        for (Map.Entry<String, List<String>> e : Evidence.sections(r).entrySet()) {
            sb.append('\n').append(e.getKey()).append(":\n");
            List<String> items = e.getValue();
            if (items.isEmpty()) sb.append("(none)\n");
            for (int j = 0; j < items.size(); j++)
                sb.append(Evidence.PREFIXES[i]).append(j + 1).append(": ").append(items.get(j)).append('\n');
            i++;
        }
        return sb.toString();
    }

    private String ask(String system, String user) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));

        JsonNode res = http.post().uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        String text = res == null ? "" : res.path("choices").path(0).path("message").path("content").asText("");
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            log.warn("AI response had no JSON: {}", text);
            throw new IllegalStateException("The AI response did not contain JSON. Try again.");
        }
        return text.substring(start, end + 1);
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Unreadable AI JSON: {}", json);
            throw new IllegalStateException("The AI response could not be read. Try again.");
        }
    }

    // ---------- offline mode: keyword heuristics ----------

    private Analysis mockAnalyze(Release r) {
        List<Draft> drafts = new ArrayList<>();
        addDrafts(drafts, "F", r.getCompletedFeatures(), "NEW", "Feature delivered: ", "New: ");
        addDrafts(drafts, "B", r.getBugFixes(), "IMPROVEMENT", "Fixed: ", "Improvement: ");
        addDrafts(drafts, "C", r.getChangedBehaviour(), "CHANGE", "Behaviour change: ", "Changed: ");
        addDrafts(drafts, "L", r.getKnownLimitations(), "LIMITATION", "Known limitation: ", "Known limitation: ");
        addDrafts(drafts, "M", r.getMigrationNotes(), "MIGRATION", "Migration: ", null);
        addDrafts(drafts, "Q", r.getQaSummary(), "QA", "QA: ", null);

        List<ChangeImpact> impacts = new ArrayList<>();
        classify(impacts, "F", r.getCompletedFeatures(), "MEDIUM", "New capability that users can see.");
        classify(impacts, "B", r.getBugFixes(), "LOW", "Fix to existing behaviour.");
        classify(impacts, "C", r.getChangedBehaviour(), "HIGH", "Existing behaviour changes for users.");

        List<String> qaIds = new ArrayList<>();
        for (int i = 1; i <= r.getQaSummary().size(); i++) qaIds.add("Q" + i);

        List<Finding> findings = new ArrayList<>();
        scanBroad(findings, "F", r.getCompletedFeatures(), qaIds);
        scanBroad(findings, "B", r.getBugFixes(), qaIds);

        List<String> missing = new ArrayList<>();
        checkCoverage(missing, "F", r.getCompletedFeatures(), r.getQaSummary());
        checkCoverage(missing, "B", r.getBugFixes(), r.getQaSummary());
        checkCoverage(missing, "C", r.getChangedBehaviour(), r.getQaSummary());
        if (hasReal(r.getChangedBehaviour()) && !hasReal(r.getMigrationNotes()))
            missing.add("Behaviour changes are listed but the migration notes say none. Confirm users need no action.");

        boolean heavy = hasReal(r.getMigrationNotes()) || hasReal(r.getChangedBehaviour());
        long items = r.getCompletedFeatures().stream().filter(s -> !Evidence.isNone(s)).count()
                + r.getBugFixes().stream().filter(s -> !Evidence.isNone(s)).count();
        String impact = heavy ? "HIGH" : items > 3 ? "MEDIUM" : "LOW";

        return new Analysis(impact,
                "Offline mode: impact is estimated from migration notes, behaviour changes and item count. "
                        + "Set AI_API_KEY for a real analysis.",
                impacts, missing, findings, drafts);
    }

    private boolean hasReal(List<String> items) {
        return items.stream().anyMatch(s -> !Evidence.isNone(s));
    }

    private void classify(List<ChangeImpact> out, String prefix, List<String> items, String level, String why) {
        for (int i = 0; i < items.size(); i++)
            if (!Evidence.isNone(items.get(i))) out.add(new ChangeImpact(prefix + (i + 1), level, why));
    }

    private void checkCoverage(List<String> out, String prefix, List<String> items, List<String> qa) {
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            if (Evidence.isNone(item)) continue;
            boolean covered = qa.stream().anyMatch(q -> sharesKeyword(item, q));
            if (!covered) out.add("No QA item mentions " + prefix + (i + 1) + ": \"" + item + "\"");
        }
    }

    private void addDrafts(List<Draft> out, String prefix, List<String> items, String section,
                           String techPrefix, String clientPrefix) {
        for (int i = 0; i < items.size(); i++) {
            if (Evidence.isNone(items.get(i))) continue;
            List<String> ev = List.of(prefix + (i + 1));
            out.add(new Draft("TECHNICAL", section, techPrefix + items.get(i), ev));
            if (clientPrefix != null) out.add(new Draft("CLIENT", section, clientPrefix + items.get(i), ev));
        }
    }

    private void scanBroad(List<Finding> out, String prefix, List<String> items, List<String> qaIds) {
        for (int i = 0; i < items.size(); i++) {
            if (Evidence.isNone(items.get(i)) || !BROAD.matcher(items.get(i)).find()) continue;
            List<String> ev = new ArrayList<>(List.of(prefix + (i + 1)));
            ev.addAll(qaIds);
            out.add(new Finding("UNSUPPORTED_CLAIM",
                    "This claim sounds broad. Check that the QA summary covers its full scope: \"" + items.get(i) + "\"",
                    ev));
        }
    }

    private List<StaleItem> mockStale(Release newer, List<BriefStatement> older) {
        List<String> negatives = new ArrayList<>();
        for (String s : newer.getKnownLimitations()) if (NEGATION.matcher(s).find()) negatives.add(s);
        for (String s : newer.getChangedBehaviour()) if (NEGATION.matcher(s).find()) negatives.add(s);
        for (String s : newer.getBugFixes()) if (NEGATION.matcher(s).find()) negatives.add(s);

        List<StaleItem> out = new ArrayList<>();
        for (BriefStatement st : older) {
            for (String n : negatives) {
                if (sharesKeyword(st.getText(), n)) {
                    out.add(new StaleItem(st.getId(), "Release " + newer.getVersion() + " says: \"" + n + "\""));
                    break;
                }
            }
        }
        return out;
    }

    private boolean sharesKeyword(String a, String b) {
        Set<String> words = new HashSet<>();
        for (String w : a.toLowerCase().split("\\W+")) if (w.length() > 4) words.add(w);
        for (String w : b.toLowerCase().split("\\W+"))
            if (w.length() > 4 && words.contains(w) && !NEGATION.matcher(w).find()) return true;
        return false;
    }
}