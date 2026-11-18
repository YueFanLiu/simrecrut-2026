package fr.isep.simrecrut.integration.ml;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.gateway.ml.CasePredictionGateway;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.*;
import fr.isep.simrecrut.service.assessment.CaseException;
import java.net.URI;
import java.net.http.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!resume-workspace")
public class HttpCasePredictionGateway implements CasePredictionGateway {
    private final ObjectMapper json;
    private final String base, token, neutralPath, biasedPath;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public HttpCasePredictionGateway(ObjectMapper json, @Value("${simrecrut.ml.base-url:}") String base,
            @Value("${simrecrut.ml.internal-token:}") String token,
            @Value("${simrecrut.ml.neutral-path:}") String neutralPath,
            @Value("${simrecrut.ml.biased-path:}") String biasedPath) {
        this.json = json; this.base = base; this.token = token; this.neutralPath = neutralPath; this.biasedPath = biasedPath;
    }
    @Value("${simrecrut.storage.model-artifacts:model_artifacts}")
    private String artifactRoot = "model_artifacts";
    @Override public boolean available() {
        return !base.isBlank() && !token.isBlank() && neutralPath.startsWith("/") && biasedPath.startsWith("/");
    }
    @Override public JsonNode predict(String caseId, Model model, Protocol protocol, JsonNode professional, JsonNode research) {
        return predict(caseId, model, protocol, professional, research, json.createObjectNode());
    }
    @Override public JsonNode predict(String caseId, Model model, Protocol protocol, JsonNode professional,
            JsonNode research, JsonNode media) {
        if (!available()) throw CaseException.dependency();
        String requestId = UUID.randomUUID().toString();
        ObjectNode body = json.createObjectNode().put("requestId", requestId).put("caseId", caseId)
                .put("modelVersion", model.versionCode()).put("featureSchemaVersion", protocol.schemaVersion());
        body.set("professional", professional);
        if (model.modelType().equals("BIASED")) body.set("research", research);
        String path = model.modelType().equals("NEUTRAL") ? neutralPath : biasedPath;
        try {
            if (protocol.dimensionCount() == 39) {
                java.nio.file.Path rootPath = java.nio.file.Path.of(artifactRoot).toRealPath();
                java.nio.file.Path file = rootPath.resolve(protocol.schemaPath()).normalize().toRealPath();
                if (!file.startsWith(rootPath) || java.nio.file.Files.size(file) > 1_048_576) throw CaseException.dependency();
                byte[] schemaBytes = java.nio.file.Files.readAllBytes(file);
                if (!fr.isep.simrecrut.service.assessment.CandidateCaseService.hash(schemaBytes).equals(protocol.schemaSha256())) {
                    throw CaseException.dependency();
                }
                body.remove(List.of("professional", "research"));
                body.set("features", ModelFeatureEncoder.encode(json, professional, research,
                        json.readTree(schemaBytes), model.modelType().equals("NEUTRAL")));
                body.put("modelSha256", model.artifactSha256());
                if (model.modelType().equals("BIASED")) {
                    for (String field : List.of("biography", "portrait")) if (media.hasNonNull(field)) body.set(field, media.get(field));
                }
            }
            URI root = URI.create(base);
            URI uri = URI.create(base.replaceAll("/+$", "") + path);
            if (!Set.of("http", "https").contains(uri.getScheme()) || !Objects.equals(root.getHost(), uri.getHost())) {
                throw CaseException.dependency();
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            CompletableFuture<HttpResponse<byte[]>> exchange = client.sendAsync(request, info -> new LimitedBody());
            HttpResponse<byte[]> response;
            try { response = exchange.get(15, TimeUnit.SECONDS); }
            catch (Exception exception) { exchange.cancel(true); throw exception; }
            byte[] bytes = response.body();
            if (response.statusCode() != 200 || bytes.length > 131072) throw CaseException.dependency();
            JsonNode result = json.readTree(bytes);
            if (!requestId.equals(result.path("requestId").asText()) || !caseId.equals(result.path("caseId").asText())
                    || !model.versionCode().equals(result.path("modelVersion").asText())
                    || !protocol.schemaVersion().equals(result.path("featureSchemaVersion").asText())
                    || !"SUCCESS".equals(result.path("status").asText())) throw CaseException.dependency();
            for (String field : List.of("logit", "acceptanceScore", "modelThreshold", "commonThreshold")) {
                if (!result.path(field).isNumber() || !Double.isFinite(result.path(field).asDouble())) throw CaseException.dependency();
            }
            if (protocol.dimensionCount() == 39 && !Objects.equals(model.artifactSha256(), result.path("modelSha256").asText())) {
                throw CaseException.dependency();
            }
            boolean mandatory = protocol.dimensionCount() != 39 || professional.path("g").asInt(-1) == 1;
            double score = result.path("acceptanceScore").asDouble();
            if (score < 0 || score > 1 || result.path("modelThreshold").asDouble() != model.threshold()
                    || result.path("commonThreshold").asDouble() != model.commonThreshold()
                    || !decision(mandatory, score, model.threshold()).equals(result.path("decision").asText())
                    || !decision(mandatory, score, model.commonThreshold()).equals(result.path("commonThresholdDecision").asText())) {
                throw CaseException.dependency();
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw CaseException.dependency();
        } catch (Exception exception) { throw CaseException.dependency(); }
    }
    private String decision(boolean mandatory, double score, double threshold) { return mandatory && score >= threshold ? "Accept" : "Reject"; }

    /** Bound the complete response, including slow bodies, before accepting prediction JSON. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) {
                if ((long) bytes.size() + chunk.remaining() > 131072) {
                    subscription.cancel(); result.completeExceptionally(CaseException.dependency()); return;
                }
                byte[] part = new byte[chunk.remaining()]; chunk.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
