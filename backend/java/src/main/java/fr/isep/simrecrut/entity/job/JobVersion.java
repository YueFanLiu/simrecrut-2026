package fr.isep.simrecrut.entity.job;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Persistence row; controllers return explicit projections instead. */
public class JobVersion {
    public Long id;
    public Long jobId;
    public Long ownerUserId;
    public Long matchingRuleVersionId;
    public Long createdBy;
    public Long publishedBy;
    public String jobPublicId;
    public String publicId;
    public String title;
    public String description;
    public String status;
    public Integer versionNo;
    public Long revisionNo;
    public String activeCriteriaJson;
    public String publicRequirementsJson;
    public BigDecimal weightSkills;
    public BigDecimal weightExperience;
    public BigDecimal weightEducation;
    public BigDecimal weightLanguages;
    public BigDecimal weightProjects;
    public String extractionStatus;
    public String extractionToken;
    public String extractionError;
    public LocalDateTime extractionStartedAt;
    public LocalDateTime publishedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public String matchingRuleVersion;
    public String ruleStatus;
    public String configPath;
    public String configSha256;
}
