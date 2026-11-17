package fr.isep.simrecrut.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Manual corrections cannot retain an automatic extraction label. */
final class FactProvenance {
    static void markCorrections(ObjectNode draft, JsonNode previous) {
        for (String group : List.of("skills", "experience", "education", "languages", "projects")) {
            for (JsonNode fact : draft.path("professional").path(group)) {
                boolean unchanged = false;
                for (JsonNode old : previous.path("professional").path(group)) {
                    if (fact.equals(old)) {
                        unchanged = true;
                        break;
                    }
                }
                if (!unchanged) {
                    ((ObjectNode) fact.path("evidence")).put("source", "USER_SUPPLIED");
                }
            }
        }
    }
}
