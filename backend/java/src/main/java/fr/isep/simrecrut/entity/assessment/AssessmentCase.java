package fr.isep.simrecrut.entity.assessment;

import java.time.LocalDateTime;

/** Internal persistence state, never serialized directly to a Candidate. */
public class AssessmentCase {
    public Long id;
    public Long candidateUserId;
    public Long jobVersionId;
    public Long protocolVersionId;
    public Long neutralModelVersionId;
    public Long biasedModelVersionId;
    public String publicId;
    public String processingStatus;
    public String humanStatus;
    public String privacyStatus;
    public String stage;
    public String draftJson;
    public String confirmedAbsentJson;
    public String cleanupStatus;
    public String safeErrorCode;
    public String assessmentReferenceMonth;
    public Long revisionNo;
    public LocalDateTime purgeAfter;
    public LocalDateTime confirmedAt;
    public LocalDateTime uploadedAt;
    public LocalDateTime automatedStartedAt;
    public LocalDateTime automatedCompletedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public String deletionReason;
}
