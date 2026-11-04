package fr.isep.simrecrut.mapper.assessment;

import fr.isep.simrecrut.dto.assessment.ReviewDtos.EligibleReviewer;
import fr.isep.simrecrut.entity.assessment.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReviewCaseMapper {
    record ProtocolReference(String protocolVersion, String featureSchemaVersion) { }
    ProtocolReference protocolReference(@Param("id") long id);
    List<AssessmentCase> hrCases(@Param("actor") long actor, @Param("job") String job,
            @Param("offset") int offset, @Param("size") int size);
    long countHrCases(@Param("actor") long actor, @Param("job") String job);
    boolean hrAccess(@Param("caseId") long caseId, @Param("actor") long actor);
    boolean researchAccess(@Param("caseId") long caseId, @Param("actor") long actor);
    boolean reviewerAccess(@Param("caseId") long caseId, @Param("actor") long actor);
    boolean hasResultExposure(@Param("caseId") long caseId, @Param("actor") long actor);
    List<EligibleReviewer> eligible(@Param("caseId") long caseId, @Param("query") String query,
            @Param("limit") int limit);
    Long lockReviewer(@Param("publicId") String publicId);
    List<HumanReview> tasks(@Param("actor") long actor, @Param("status") String status,
            @Param("offset") int offset, @Param("size") int size);
    long countTasks(@Param("actor") long actor, @Param("status") String status);
    HumanReview findReview(@Param("id") String id);
    HumanReview lockReview(@Param("id") String id);
    boolean hasUnfinished(@Param("caseId") long caseId, @Param("actor") long actor);
    List<HumanReview> caseReviews(@Param("caseId") long caseId);
    int insertReview(HumanReview review);
    int opened(@Param("id") long id);
    int submit(@Param("id") long id, @Param("decision") String decision, @Param("reason") String reason);
    int refreshHumanStatus(@Param("caseId") long caseId);
}
