package fr.isep.simrecrut.gateway.extraction;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared online/offline extraction boundary. Output is a draft requiring human review. */
public interface ResumeCleaningGateway {
    /**
     * Extract professional facts without storing the input or calling a remote model.
     * Accepts PDF, DOCX or UTF-8 TXT up to 10 MiB; rejects scanned PDFs without text.
     * The caller owns authorization, admission control and confirmation/persistence.
     */
    ObjectNode clean(String filename, byte[] content) throws java.io.IOException;
}
