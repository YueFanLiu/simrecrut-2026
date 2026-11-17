package fr.isep.simrecrut.application.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.dto.resume.ResumeDtos.EditRequest;
import fr.isep.simrecrut.dto.resume.ResumeDtos.ImportRequest;
import fr.isep.simrecrut.gateway.resume.ResumeGateway;
import fr.isep.simrecrut.gateway.resume.ResumeGateway.Action;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@Profile("!resume-workspace")
public class ResumeApplicationService {

    private final ResumeGateway gateway;
    private final ObjectMapper json;

    public ResumeApplicationService(ResumeGateway gateway, ObjectMapper json) {
        this.gateway = gateway;
        this.json = json;
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:list')")
    public List<JsonNode> list(long userId) throws Exception {
        return gateway.list(userId);
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:list')")
    public JsonNode detail(long userId, String id) throws Exception {
        requireId(id);
        return gateway.detail(userId, id);
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:upload')")
    public JsonNode importFile(long userId, String key, ImportRequest body) throws Exception {
        return perform(userId, Action.IMPORT, "", key, json.valueToTree(body));
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:edit')")
    public JsonNode clean(long userId, String id, String key) throws Exception {
        return perform(userId, Action.CLEAN, id, key, json.createObjectNode());
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:edit')")
    public JsonNode save(long userId, String id, String key, EditRequest body) throws Exception {
        if (!Boolean.FALSE.equals(body.confirmed())) {
            throw new IllegalArgumentException("Use the confirmation action to lock reviewed facts.");
        }
        return perform(userId, Action.SAVE, id, key, json.valueToTree(body));
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:edit') "
            + "and hasAuthority('simrecrut:resume:confirm')")
    public JsonNode confirm(long userId, String id, String key, EditRequest body) throws Exception {
        if (!Boolean.TRUE.equals(body.confirmed())) {
            throw new IllegalArgumentException("Explicit review confirmation is required.");
        }
        return perform(userId, Action.CONFIRM, id, key, json.valueToTree(body));
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:confirm')")
    public JsonNode publish(long userId, String id, String key) throws Exception {
        return perform(userId, Action.UPLOAD, id, key, null);
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:confirm')")
    public JsonNode cleanup(long userId, String id, String key) throws Exception {
        return perform(userId, Action.CLEANUP, id, key, null);
    }

    @PreAuthorize("hasRole('CANDIDATE') and hasAuthority('simrecrut:resume:delete')")
    public JsonNode delete(long userId, String id, String key) throws Exception {
        return perform(userId, Action.DELETE, id, key, null);
    }

    private JsonNode perform(long userId, Action action, String id, String key, JsonNode body) throws Exception {
        if (action != Action.IMPORT) {
            requireId(id);
        }
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new IllegalArgumentException("Supply a valid Idempotency-Key header.");
        }
        return gateway.perform(userId, action, id, key, body);
    }

    private void requireId(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("Invalid resume ID.");
        }
    }
}
