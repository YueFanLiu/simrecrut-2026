package fr.isep.simrecrut.entity.assessment;

import java.time.LocalDateTime;

/** Internal row. Public responses are projected according to the assigned condition. */
public class HumanReview {
    public Long id;
    public Long caseId;
    public Long reviewerUserId;
    public String publicId;
    public String casePublicId;
    public String jobTitle;
    public String conditionType;
    public String purpose;
    public String protocolText;
    public String instruction;
    public String status;
    public String decision;
    public String reason;
    public LocalDateTime createdAt;
    public LocalDateTime submittedAt;
}
