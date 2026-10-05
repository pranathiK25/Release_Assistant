package com.example.Release_Assistant_Backend.service;


import com.example.Release_Assistant_Backend.entity.Release;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Gives every item in a release package a stable evidence id: F1, B2, Q1, ... */
public final class Evidence {
    public static final String[] PREFIXES = {"F", "B", "C", "Q", "L", "M", "U"};
    public static final String[] LABELS = {
            "Completed features", "Bug fixes", "Changed behaviour", "QA summary",
            "Known limitations", "Migration notes", "Affected user groups"};

    private static final Pattern NONE =
            Pattern.compile("^(none|n/?a|nil|nothing|not applicable)\\.?$", Pattern.CASE_INSENSITIVE);

    private Evidence() {}

    /** True when the developer explicitly declared that a section does not apply. */
    public static boolean isNone(String s) {
        return s != null && NONE.matcher(s.trim()).matches();
    }

    private static List<List<String>> lists(Release r) {
        return List.of(r.getCompletedFeatures(), r.getBugFixes(), r.getChangedBehaviour(), r.getQaSummary(),
                r.getKnownLimitations(), r.getMigrationNotes(), r.getAffectedUsers());
    }

    public static Map<String, String> index(Release r) {
        Map<String, String> m = new LinkedHashMap<>();
        List<List<String>> all = lists(r);
        for (int i = 0; i < all.size(); i++)
            for (int j = 0; j < all.get(i).size(); j++)
                m.put(PREFIXES[i] + (j + 1), all.get(i).get(j));
        return m;
    }

    public static Map<String, List<String>> sections(Release r) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        List<List<String>> all = lists(r);
        for (int i = 0; i < all.size(); i++) m.put(LABELS[i], all.get(i));
        return m;
    }
}