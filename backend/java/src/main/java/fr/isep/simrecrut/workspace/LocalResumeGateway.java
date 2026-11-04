package fr.isep.simrecrut.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.gateway.resume.ResumeGateway;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!resume-workspace")
public class LocalResumeGateway implements ResumeGateway {

    private static final List<String> PUBLIC_FIELDS = List.of(
            "id", "filename", "extension", "bytes", "pages", "status", "confirmed", "revision",
            "cloud_revision", "created_at", "updated_at", "error", "temporary_original", "expires_at",
            "confirmed_at", "originalAvailable", "reference_month");

    private final UserResumeServices users;

    public LocalResumeGateway(UserResumeServices users) {
        this.users = users;
    }

    @Override
    public List<JsonNode> list(long userId) throws Exception {
        ResumeService store = users.forUser(userId);
        synchronized (store) {
            return store.list().stream().map(row -> project(row, false)).toList();
        }
    }

    @Override
    public JsonNode detail(long userId, String resumeId) throws Exception {
        ResumeService store = users.forUser(userId);
        synchronized (store) {
            return project(store.detail(resumeId), true);
        }
    }

    private JsonNode project(ObjectNode row, boolean detail) {
        ObjectNode result = Facts.JSON.createObjectNode();
        for (String field : PUBLIC_FIELDS) {
            if (row.has(field)) {
                result.set(field, row.get(field).deepCopy());
            }
        }
        if (detail) {
            result.set("draft", row.path("draft").deepCopy());
        }
        return result;
    }

    @Override
    public JsonNode perform(long userId, Action action, String resumeId, String operationKey, JsonNode body)
            throws Exception {
        ResumeService store = users.forUser(userId);
        Object result = ResumeOperation.perform(
                store, operationKey, action.name().toLowerCase(Locale.ROOT), resumeId, body,
                current -> switch (action) {
                    case IMPORT -> current.importFile(body);
                    case CLEAN -> current.clean(resumeId, body);
                    case SAVE, CONFIRM -> current.save(resumeId, body);
                    case UPLOAD -> current.upload(resumeId);
                    case CLEANUP -> current.cleanupOriginal(resumeId);
                    case DELETE -> current.delete(resumeId);
                });
        JsonNode response = Facts.JSON.valueToTree(result);
        if (response.isObject()) {
            ((ObjectNode) response).remove("table");
        }
        return response;
    }
}
