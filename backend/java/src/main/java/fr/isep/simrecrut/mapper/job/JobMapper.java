package fr.isep.simrecrut.mapper.job;

import fr.isep.simrecrut.entity.job.JobVersion;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobMapper {
    Long lockActor(long userId);
    Long lockJob(@Param("publicId") String publicId, @Param("owner") long owner);
    Map<String, Object> receipt(@Param("owner") long owner, @Param("key") String key);
    void insertReceipt(@Param("owner") long owner, @Param("key") String key,
            @Param("hash") String hash, @Param("jobId") String jobId, @Param("response") String response);
    JobVersion rule(String code);
    void insertJob(JobVersion row);
    void insertVersion(JobVersion row);
    int nextVersion(long jobId);
    JobVersion draft(String jobPublicId);
    JobVersion version(@Param("jobPublicId") String jobPublicId, @Param("versionPublicId") String versionPublicId);
    JobVersion published(String jobPublicId);
    List<JobVersion> versions(String jobPublicId);
    List<JobVersion> list(@Param("owner") Long owner, @Param("keyword") String keyword,
            @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("owner") Long owner, @Param("keyword") String keyword);
    int updateDraft(JobVersion row);
    void updateJob(@Param("id") long id, @Param("title") String title, @Param("published") boolean published);
    List<String> requirements(long versionId);
    void deleteRequirements(long versionId);
    void insertRequirement(@Param("versionId") long versionId, @Param("criterion") String criterion,
            @Param("code") String code, @Param("payload") String payload,
            @Param("mandatory") boolean mandatory, @Param("visible") boolean visible,
            @Param("included") boolean included, @Param("reviewed") boolean reviewed,
            @Param("excerpt") String excerpt, @Param("location") String location,
            @Param("warnings") String warnings, @Param("order") int order);
}
