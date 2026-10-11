package fr.isep.simrecrut.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Persist repeat-operation keys in the authenticated user's private index. */
final class ResumeOperation {
    interface Work { Object run(ResumeService service) throws Exception; }
    static Object perform(ResumeService service, String key, String action, String target, JsonNode body, Work work) throws Exception {
        Facts.require(key != null && key.matches("[A-Za-z0-9_-]{16,128}"), "Supply a valid Idempotency-Key header.");
        synchronized (service) {
            boolean deleted = false;
            if (!target.isEmpty()) {
                deleted = action.equals("delete") && !service.query("SELECT id FROM deletion_receipts WHERE id=?", target).isEmpty();
                if (!deleted) {
                    if (action.equals("delete")) service.storedRow(target);
                    else service.row(target); // Cached replies never bypass current access or retention checks.
                }
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (body == null ? "{}" : canonical(body).toString()).getBytes(StandardCharsets.UTF_8)));
            var existing = service.query("SELECT * FROM repeat_operations WHERE operation_key=?", key);
            if (!existing.isEmpty() && !service.clock.instant().isBefore(Instant.parse(existing.get(0).path("expires_at").asText()))) {
                service.execute("DELETE FROM repeat_operations WHERE operation_key=?", key); existing = List.of();
            }
            if (!existing.isEmpty()) {
                var saved = existing.get(0);
                Facts.require(saved.path("action").asText().equals(action) && saved.path("target").asText().equals(target)
                    && saved.path("request_hash").asText().equals(hash), "This operation key was already used for different data or another action.");
                if (saved.hasNonNull("response")) {
                    if (deleted) return Map.of("deleted", true, "pending", false);
                    JsonNode result = Facts.JSON.readTree(saved.path("response").asText());
                    if (action.equals("import")) service.row(result.path("id").asText());
                    return result;
                }
            } else {
                service.execute("INSERT INTO repeat_operations(operation_key,action,target,request_hash,expires_at) VALUES(?,?,?,?,?)",
                    key, action, target, hash, service.clock.instant().plus(Duration.ofHours(24)).toString());
            }
            Object result = deleted ? Map.of("deleted", true, "pending", false) : work.run(service);
            service.execute("UPDATE repeat_operations SET response=? WHERE operation_key=?", Facts.JSON.writeValueAsString(result), key);
            return result;
        }
    }
    private static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var result = Facts.JSON.createObjectNode(); var names = new TreeSet<String>(); value.fieldNames().forEachRemaining(names::add);
            for (String name : names) result.set(name, canonical(value.get(name))); return result;
        }
        if (value.isArray()) { var result = Facts.JSON.createArrayNode(); value.forEach(item -> result.add(canonical(item))); return result; }
        return value;
    }
}
