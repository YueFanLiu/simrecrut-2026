package fr.isep.simrecrut.mapper.assessment;

import fr.isep.simrecrut.entity.assessment.AssessmentCase;
import fr.isep.simrecrut.entity.job.JobVersion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CandidateCaseMapper {
    Long lockActor(@Param("userId") long userId, @Param("version") long version);
    AssessmentCase find(@Param("publicId") String publicId);
    AssessmentCase lock(@Param("publicId") String publicId);
    boolean retained(@Param("caseId") long caseId);
    List<AssessmentCase> list(@Param("owner") long owner, @Param("offset") int offset, @Param("size") int size);
    long count(@Param("owner") long owner);
    JobVersion job(@Param("versionId") long versionId);
    JobVersion published(@Param("publicId") String publicId);
    List<Protocol> protocols(@Param("ruleId") long ruleId, @Param("code") String code);
    Protocol protocol(@Param("id") long id);
    List<Model> activeModels(@Param("protocolId") long protocolId);
    Model model(@Param("id") long id);
    int insert(AssessmentCase row);
    int update(AssessmentCase row);
    List<String> requirements(@Param("versionId") long versionId);
    String snapshot(@Param("caseId") long caseId);
    int insertSnapshot(@Param("caseId") long caseId, @Param("schema") String schema, @Param("data") String data);
    List<Attribute> attributes(@Param("caseId") long caseId);
    int deleteAttributes(@Param("caseId") long caseId);
    int insertAttribute(@Param("caseId") long caseId, @Param("type") String type, @Param("value") String value);
    int confirmAttributes(@Param("caseId") long caseId);
    Receipt receipt(@Param("owner") long owner, @Param("key") String key);
    int insertReceipt(@Param("owner") long owner, @Param("key") String key, @Param("hash") String hash,
            @Param("caseId") String caseId, @Param("action") String action, @Param("status") int status,
            @Param("response") String response);
    int insertArtifact(@Param("caseId") long caseId, @Param("path") String path,
            @Param("bytes") long bytes, @Param("hash") String hash, @Param("deadline") LocalDateTime deadline);
    String artifact(@Param("caseId") long caseId);
    int artifactsDeleted(@Param("caseId") long caseId);
    int enqueue(@Param("taskId") String taskId, @Param("caseId") String caseId, @Param("type") String type);
    List<Task> pendingTasks();
    Task lockTask(@Param("id") long id);
    int claim(@Param("id") long id, @Param("token") String token, @Param("worker") String worker);
    int heartbeat(@Param("id") long id, @Param("token") String token);
    int finishTask(@Param("id") long id, @Param("token") String token, @Param("status") String status);
    int expireTask(@Param("id") long id);
    int resetCleanupAttempts(@Param("caseId") String caseId, @Param("type") String type);
    boolean hasOpenTask(@Param("caseId") String caseId, @Param("type") String type);
    int cancelTasks(@Param("caseId") String caseId);
    int requeue(@Param("caseId") String caseId, @Param("type") String type);
    String savedResult(@Param("caseId") long caseId, @Param("method") String method);
    int saveResult(@Param("caseId") long caseId, @Param("method") String method, @Param("data") String data);
    int writeMatching(@Param("caseId") long caseId, @Param("ruleId") long ruleId);
    int writeObjective(@Param("caseId") long caseId, @Param("protocolId") long protocolId, @Param("cutoff") BigDecimal cutoff);
    int writeMl(@Param("caseId") long caseId, @Param("method") String method, @Param("model") Model model);
    List<Human> humans(@Param("caseId") long caseId);
    List<AssessmentCase> maintenanceCases();
    int cancelReviews(@Param("caseId") long caseId);
    List<Long> lockExperiments(@Param("caseId") long caseId);
    int invalidateExperiments(@Param("caseId") long caseId);
    int cancelExperimentTasks(@Param("caseId") long caseId);
    int deleteMetrics(@Param("caseId") long caseId);
    int deleteVariantReviews(@Param("caseId") long caseId);
    int deleteVariantResults(@Param("caseId") long caseId);
    int deleteVariants(@Param("caseId") long caseId);
    int deleteBaselines(@Param("caseId") long caseId);
    int deleteSubjects(@Param("caseId") long caseId);
    int deleteReviews(@Param("caseId") long caseId);
    int deleteGrants(@Param("caseId") long caseId);
    int deleteArtifacts(@Param("caseId") long caseId);
    int deleteSnapshots(@Param("caseId") long caseId);
    int deleteMatching(@Param("caseId") long caseId);
    int deleteObjective(@Param("caseId") long caseId);
    int deleteMl(@Param("caseId") long caseId);
    int deleteResults(@Param("caseId") long caseId);
    int deleteTasks(@Param("caseId") String caseId);
    int redactOperations(@Param("caseId") String caseId);
    int completeDeletionReceipts(@Param("caseId") String caseId, @Param("response") String response);
    int deletionReceipt(@Param("hash") String hash, @Param("reason") String reason);
    int deleteCase(@Param("caseId") long caseId);
    int audit(@Param("actor") long actor, @Param("action") String action, @Param("hash") String hash);

    record Protocol(long id, long matchingRuleVersionId, long featureSchemaVersionId, String versionCode,
            BigDecimal tRule, String schemaVersion, String schemaPath, String schemaSha256, int dimensionCount) { }
    record Model(long id, long featureSchemaVersionId, long protocolVersionId, String versionCode,
            String modelType, double threshold, double commonThreshold, String status) { }
    record Receipt(String requestHash, String casePublicId, String action, int httpStatus, String responseJson) { }
    record Attribute(String type, String value, String source, boolean confirmed) { }
    record Task(long id, String resourcePublicId, String taskType, String status, int attemptCount,
            String leaseToken, LocalDateTime leaseUntil, int maxAttempts) { }
    record Human(String conditionType, String status, String decision, String reason) { }
}
