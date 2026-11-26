package fr.isep.simrecrut.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.isep.simrecrut.dto.job.JobDtos.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** Documentation-only projections for endpoints that currently return JsonNode or Map. */
public final class ApiDocumentationModels {
    private ApiDocumentationModels() { }

    @Schema(name = "JobPublicSummary")
    public record PublicJobSummary(String jobId, String jobVersionId, String title, Integer version,
            Integer currentVersion, String summary, String publishedAt) { }

    @Schema(name = "JobManagedSummary")
    public record ManagedJobSummary(String jobId, String jobVersionId, String title, Integer version,
            Integer currentVersion, String summary, @Schema(nullable = true) String publishedAt, String status, Long revision,
            String createdAt) { }

    @Schema(name = "JobPublicPage")
    public record PublicJobPage(List<PublicJobSummary> items, long total, int page, int pageSize) { }

    @Schema(name = "JobManagedPage")
    public record ManagedJobPage(List<ManagedJobSummary> items, long total, int page, int pageSize) { }

    @Schema(name = "JobSkillsPayload")
    public record SkillsPayload(List<String> assessedSkillCodes) { }

    @Schema(name = "JobExperiencePayload")
    public record ExperiencePayload(BigDecimal targetYears, List<String> relevantRoleFamilyCodes,
            BigDecimal minimumYears) { }

    @Schema(name = "JobEducationPayload")
    public record EducationPayload(String minimumDegreeLevelCode, List<String> acceptedSubjectCodes,
            Boolean unrestrictedSubject) { }

    @Schema(name = "JobRequiredLanguage")
    public record RequiredLanguage(String languageCode, String minimumLevelCode) { }

    @Schema(name = "JobLanguagesPayload")
    public record LanguagesPayload(List<RequiredLanguage> requiredLanguages) { }

    @Schema(name = "JobProjectsPayload")
    public record ProjectsPayload(List<String> assessedConditionCodes) { }

    @Schema(name = "JobPublicRequirement", description = "Public professional fields only; no weights or evidence.")
    public record PublicRequirement(Criterion criterionType, String requirementCode, Boolean mandatory,
            @Schema(oneOf = {SkillsPayload.class, ExperiencePayload.class, EducationPayload.class,
                    LanguagesPayload.class, ProjectsPayload.class}) Object payload) { }

    @Schema(name = "JobPublicDetail")
    public record PublicJobDetail(String jobId, String jobVersionId, String title, Integer version,
            Integer currentVersion, String summary, String publishedAt, String description,
            List<PublicRequirement> requirements) { }

    @Schema(name = "JobExtractionStatus")
    public record ExtractionStatus(String jobId, Long revision,
            @Schema(allowableValues = {"IDLE", "RUNNING", "COMPLETE", "FAILED_RETRYABLE"}) String status,
            String stage, Boolean retryable, @Schema(nullable = true) String errorCode,
            @Schema(nullable = true) String message, @Schema(nullable = true) String updatedAt) { }

    @Schema(name = "JobManagedDetail")
    public record ManagedJobDetail(String jobId, String jobVersionId, String title, Integer version,
            Integer currentVersion, String summary, @Schema(nullable = true) String publishedAt, String status, Long revision,
            String createdAt, String description, String matchingRuleVersion, List<Requirement> requirements,
            List<Criterion> activeCriteria, Weights weights, ExtractionStatus extraction) { }

    @Schema(name = "JobRequirementsView")
    public record RequirementsView(String jobId, Long revision, List<Requirement> requirements) { }

    @Schema(name = "JobWeightsView")
    public record WeightsView(String jobId, Long revision, Weights weights, List<Criterion> activeCriteria) { }

    @Schema(name = "ResumeEvidence")
    public record Evidence(String text, @Schema(nullable = true) Integer page,
            @Schema(allowableValues = {"EXTRACTED", "USER_SUPPLIED"}) String source) { }

    @Schema(name = "ResumeSkill")
    public record Skill(String skillCode, Evidence evidence) { }

    @Schema(name = "ResumeExperience")
    public record Experience(String roleFamilyCode, @Schema(nullable = true) String startMonth,
            @Schema(nullable = true) String endMonth, Boolean isCurrent,
            List<String> supportedSkillCodes, @Schema(nullable = true) BigDecimal reviewedRelevantYears, Evidence evidence) { }

    @Schema(name = "ResumeEducation")
    public record Education(String degreeLevelCode, String subjectCode, Evidence evidence) { }

    @Schema(name = "ResumeLanguage")
    public record Language(String languageCode, String levelCode, Evidence evidence) { }

    @Schema(name = "ResumeProject")
    public record Project(String projectId, List<String> supportedConditionCodes,
            List<String> supportedSkillCodes, Evidence evidence) { }

    @Schema(name = "ResumeProfessionalFacts")
    public record ProfessionalFacts(List<Skill> skills, List<Experience> experience,
            List<Education> education, List<Language> languages, List<Project> projects) { }

    @Schema(name = "ResumeDraft", description = "Professional facts only. No names, contact details or full CV text.")
    public record ResumeDraft(ProfessionalFacts professional, List<String> warnings) { }

    @Schema(name = "ResumeSummary", description = "SQLite flags confirmed and temporary_original are integers 0 or 1.")
    public record ResumeSummary(String id, String filename, String extension, Long bytes, Integer pages,
            String status, Integer confirmed, Integer revision, @JsonProperty("cloud_revision") Integer cloudRevision,
            @JsonProperty("created_at") String createdAt, @JsonProperty("updated_at") String updatedAt,
            @Schema(nullable = true) String error,
            @JsonProperty("temporary_original") Integer temporaryOriginal,
            @JsonProperty("expires_at") @Schema(nullable = true) String expiresAt,
            @JsonProperty("confirmed_at") @Schema(nullable = true) String confirmedAt,
            Boolean originalAvailable) { }

    @Schema(name = "ResumeDetail")
    public record ResumeDetail(String id, String filename, String extension, Long bytes, Integer pages,
            String status, Integer confirmed, Integer revision, @JsonProperty("cloud_revision") Integer cloudRevision,
            @JsonProperty("created_at") String createdAt, @JsonProperty("updated_at") String updatedAt,
            @Schema(nullable = true) String error,
            @JsonProperty("temporary_original") Integer temporaryOriginal,
            @JsonProperty("expires_at") @Schema(nullable = true) String expiresAt,
            @JsonProperty("confirmed_at") @Schema(nullable = true) String confirmedAt,
            Boolean originalAvailable, @JsonProperty("reference_month") @Schema(nullable = true) String referenceMonth,
            ResumeDraft draft) { }

    @Schema(name = "ResumeImportResult")
    public record ImportResult(String id, Boolean duplicate) { }
    @Schema(name = "ResumeProcessingResult")
    public record ProcessingResult(Boolean processing) { }
    @Schema(name = "ResumeSaveResult")
    public record SaveResult(Boolean saved) { }
    @Schema(name = "ResumePublicationResult")
    public record PublicationResult(Boolean uploaded, Integer revision, Boolean originalRemoved) { }
    @Schema(name = "ResumeDeletionResult")
    public record DeletionResult(Boolean deleted, Boolean pending) { }

    @Schema(name = "ApiErrorData")
    public record ErrorData(Boolean retryable, String jobId, String draftId, Long revision,
            List<Issue> errors, List<String> warnings) { }
}
