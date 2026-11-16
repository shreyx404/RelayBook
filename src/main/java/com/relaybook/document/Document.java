package com.relaybook.document;

/**
 * Abstract base for generated contact-related documents.
 *
 * <p>Template Method: {@link #export()} is final and fixes the skeleton
 * (header, content, footer); subclasses fill in {@link #renderContent()} and
 * optionally override the header/footer hooks. This guarantees every document
 * is produced the same way while the per-format details stay in one subclass.</p>
 */
public abstract class Document implements Exportable {
    private final String title;

    protected Document(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Document title is required.");
        }
        this.title = title.trim();
    }

    public final String getTitle() {
        return title;
    }

    /** Final template method: header + rendered content + footer. */
    @Override
    public final String export() {
        StringBuilder document = new StringBuilder();
        appendHeader(document);
        document.append(renderContent());
        appendFooter(document);
        return document.toString();
    }

    /** Hook: emit everything that belongs before the content. */
    protected void appendHeader(StringBuilder document) {
        // Default documents have no header.
    }

    /** Hook: emit everything that belongs after the content. */
    protected void appendFooter(StringBuilder document) {
        // Default documents have no footer.
    }

    /** The format-specific body; called by the final {@link #export()}. */
    public abstract String renderContent();
}
