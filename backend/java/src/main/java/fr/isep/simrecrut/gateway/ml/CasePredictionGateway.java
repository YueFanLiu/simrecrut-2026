package fr.isep.simrecrut.gateway.ml;

import com.fasterxml.jackson.databind.JsonNode;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.Model;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.Protocol;

/** Predictions use case-frozen versions; no training or application database access. */
public interface CasePredictionGateway {
    boolean available();
    JsonNode predict(String caseId, Model model, Protocol protocol, JsonNode professional, JsonNode research);
}
