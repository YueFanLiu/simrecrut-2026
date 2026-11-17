package fr.isep.simrecrut.mapper.assessment;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

/** Import local package metadata without importing private corpus members. */
@Mapper
public interface LocalModelRegistryMapper {
    int rule(Map<String, Object> values);
    int schema(Map<String, Object> values);
    int bias(Map<String, Object> values);
    int protocol(Map<String, Object> values);
    int actor(Map<String, Object> values);
    int corpus(Map<String, Object> values);
    int experiment(Map<String, Object> values);
    int run(Map<String, Object> values);
    int model(Map<String, Object> values);
    String existingDigest(String version);
}
