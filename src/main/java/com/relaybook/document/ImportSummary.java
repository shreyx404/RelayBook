package com.relaybook.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of one vCard import run: how many contacts were created, how many
 * were skipped as duplicates of existing records, and the per-card rejection
 * reasons (one entry per unusable card). Immutable.
 */
public final class ImportSummary {
    private final List<String> importedNames;
    private final List<String> skippedDuplicates;
    private final List<String> rejected;

    public ImportSummary(List<String> importedNames, List<String> skippedDuplicates, List<String> rejected) {
        this.importedNames = List.copyOf(importedNames);
        this.skippedDuplicates = List.copyOf(skippedDuplicates);
        this.rejected = List.copyOf(rejected);
    }

    public static ImportSummary empty() {
        return new ImportSummary(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public int getImportedCount() { return importedNames.size(); }
    public int getSkippedDuplicatesCount() { return skippedDuplicates.size(); }
    public int getRejectedCount() { return rejected.size(); }
    public List<String> getImportedNames() { return importedNames; }
    public List<String> getSkippedDuplicates() { return skippedDuplicates; }
    public List<String> getRejected() { return rejected; }

    /** One-line human summary for dialogs and logs. */
    public String summaryText() {
        return "Imported " + getImportedCount() + " contact(s), skipped "
                + getSkippedDuplicatesCount() + " duplicate(s), rejected " + getRejectedCount() + " card(s).";
    }

    public boolean isEmpty() {
        return importedNames.isEmpty() && skippedDuplicates.isEmpty() && rejected.isEmpty();
    }

    /** Builder used while the importer walks the cards. */
    public static final class Collector {
        private final List<String> importedNames = new ArrayList<>();
        private final List<String> skippedDuplicates = new ArrayList<>();
        private final List<String> rejected = new ArrayList<>();

        public void imported(String name) {
            importedNames.add(name);
        }

        public void duplicate(String name) {
            skippedDuplicates.add(name);
        }

        public void rejected(String reason) {
            rejected.add(reason);
        }

        public ImportSummary build() {
            return new ImportSummary(Collections.unmodifiableList(new ArrayList<>(importedNames)),
                    Collections.unmodifiableList(new ArrayList<>(skippedDuplicates)),
                    Collections.unmodifiableList(new ArrayList<>(rejected)));
        }
    }
}
