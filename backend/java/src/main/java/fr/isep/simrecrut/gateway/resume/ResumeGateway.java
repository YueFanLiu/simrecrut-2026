package fr.isep.simrecrut.gateway.resume;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Own-account storage boundary; implementations must scope replay and reads to userId. */
public interface ResumeGateway {

    enum Action {
        IMPORT, CLEAN, SAVE, CONFIRM, UPLOAD, CLEANUP, DELETE
    }

    List<JsonNode> list(long userId) throws Exception;

    JsonNode detail(long userId, String resumeId) throws Exception;

    JsonNode perform(long userId, Action action, String resumeId, String operationKey, JsonNode body) throws Exception;
}
