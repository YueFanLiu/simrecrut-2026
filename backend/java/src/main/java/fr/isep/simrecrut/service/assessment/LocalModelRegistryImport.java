package fr.isep.simrecrut.service.assessment;

import com.fasterxml.jackson.databind.*;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.mapper.assessment.LocalModelRegistryMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in bootstrap for the dedicated local database; ACTIVE means available for simulation only. */
@Component
@Profile("ml-integration")
public class LocalModelRegistryImport implements ApplicationRunner {
    private final ObjectMapper json;
    private final LocalModelRegistryMapper db;
    private final PublicIdGenerator ids;
    private final TransactionTemplate tx;
    private final Path root;
    private final String url;

    public LocalModelRegistryImport(ObjectMapper json, LocalModelRegistryMapper db, PublicIdGenerator ids,
            PlatformTransactionManager transactions,
            @Value("${simrecrut.storage.model-artifacts:model_artifacts}") String root,
            @Value("${spring.datasource.url}") String url) {
        this.json = json; this.db = db; this.ids = ids; this.tx = new TransactionTemplate(transactions);
        this.root = Path.of(root).toAbsolutePath().normalize(); this.url = url;
    }

    @Override public void run(ApplicationArguments arguments) throws Exception {
        if (!url.matches("jdbc:mysql://(?:mysql|localhost|127\\.0\\.0\\.1)(?::\\d+)?/simrecrut_ml(?:\\?.*)?")) {
            throw new IllegalStateException("The ml-integration profile requires the isolated simrecrut_ml database.");
        }
        for (String mode : List.of("neutral", "biased")) {
            List<Path> manifests;
            try (var paths = Files.list(root.resolve(mode))) {
                manifests = paths.filter(Files::isDirectory).map(p -> p.resolve("manifest.json"))
                        .filter(Files::isRegularFile).toList();
            }
            if (manifests.size() != 1) throw new IllegalStateException("Select exactly one local package per model type.");
            Path path = manifests.get(0).toRealPath();
            JsonNode m = json.readTree(Files.readAllBytes(path));
            if (!m.path("modelType").asText().equals(mode.toUpperCase(Locale.ROOT))
                    || m.path("inputWidth").asInt() != 39 || m.path("testEvaluated").asBoolean(true)) {
                throw new IllegalStateException("Incompatible local research package.");
            }
            var fields = m.path("files").fields();
            while (fields.hasNext()) {
                var field = fields.next(); Path file = path.getParent().resolve(field.getKey()).normalize().toRealPath();
                if (!file.startsWith(path.getParent()) || !digest(file).equals(field.getValue().asText())) {
                    throw new IllegalStateException("Local model package verification failed.");
                }
            }
            Map<String, Object> values = values(path, m);
            tx.executeWithoutResult(ignored -> {
                String existing = db.existingDigest(m.path("modelVersion").asText());
                if (existing != null) {
                    if (!existing.equals(values.get("manifestHash"))) throw new IllegalStateException("Model version is immutable.");
                    return;
                }
                db.rule(values); db.schema(values); db.bias(values); db.protocol(values); db.actor(values);
                db.corpus(values); db.experiment(values); db.run(values); db.model(values);
            });
        }
    }

    private Map<String, Object> values(Path path, JsonNode m) throws Exception {
        Map<String, Object> v = new HashMap<>();
        String packagePath = root.relativize(path.getParent()).toString().replace('\\', '/');
        v.put("version", m.path("modelVersion").asText()); v.put("type", m.path("modelType").asText());
        v.put("schema", m.path("featureSchemaVersion").asText());
        v.put("schemaPath", packagePath + "/feature-schema.json");
        v.put("schemaHash", m.path("files").path("feature-schema.json").asText());
        v.put("rule", "objective-curated-v8-online-v1"); v.put("rulePath", "objective-curated-v8-online-v1.json");
        v.put("ruleHash", digest(root.resolve("rules/objective-curated-v8-online-v1.json")));
        v.put("bias", "controlled-five-family-v1"); v.put("protocol", "ml39-20261117-v1");
        v.put("ruleThreshold", m.path("ruleThreshold").decimalValue());
        JsonNode effects = m.path("labelProtocol").path("effects");
        for (String family : List.of("school", "referral", "gender", "ethnicity")) {
            v.put(family, effects.path(family).toString());
        }
        v.put("nameCue", effects.path("name_cue").toString());
        v.put("labels", m.path("labelProtocol").toString());
        v.put("corpus", "local-import-" + m.path("sourceCohort").asText());
        v.put("manifestPath", packagePath + "/manifest.json"); v.put("manifestHash", digest(path));
        v.put("packagePath", packagePath); v.put("actorId", ids.nextId()); v.put("experimentId", ids.nextId());
        v.put("password", new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()));
        v.put("threshold", m.path("modelThreshold").asDouble()); v.put("common", m.path("commonThreshold").asDouble());
        v.put("seed", m.path("seed").asLong()); v.put("training", m.path("training").toString());
        v.put("validation", m.path("validation").toString());
        var provenance = json.createObjectNode().put("sourceRun", m.path("sourceRun").asText())
                .put("sourceCohort", m.path("sourceCohort").asText()).put("privateMembersImported", false)
                .put("testEvaluated", false).put("scope", "LOCAL_RESEARCH_SIMULATION_UNCALIBRATED");
        v.put("provenance", provenance.toString());
        var evaluation = provenance.deepCopy(); evaluation.set("validation", m.path("validation"));
        v.put("evaluation", evaluation.toString());
        return v;
    }

    private String digest(Path path) throws Exception {
        try (var stream = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[65536]; int count;
            while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
