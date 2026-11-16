package com.relaybook.document;

/** Interface inheritance: one shareable document combines export and validation contracts. */
public interface ShareableDocument extends Exportable, Validatable {
    boolean isShareAllowed();
}
