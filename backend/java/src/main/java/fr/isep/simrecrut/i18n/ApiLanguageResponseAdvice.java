package fr.isep.simrecrut.i18n;

import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.CaseStatus;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.ReviewDraft;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.FiveResults;
import fr.isep.simrecrut.dto.assessment.ReviewDtos.HrCaseDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.dto.job.JobDtos.Issue;
import fr.isep.simrecrut.dto.job.JobDtos.Validation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Localizes the API envelope and explicitly typed system diagnostics only. */
@RestControllerAdvice
public class ApiLanguageResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType media,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof ApiResponse<?> api)) return body;
        Locale locale = ApiMessages.resolveLocale(request.getHeaders().getFirst(HttpHeaders.ACCEPT_LANGUAGE));
        response.getHeaders().setContentLanguage(locale);
        return new ApiResponse<>(api.code(), ApiMessages.translate(api.message(), locale), api.requestId(),
                diagnostics(api.data(), locale, api.code() >= 400));
    }
    private Object diagnostics(Object data, Locale locale, boolean error) {
        if (data instanceof ReviewDraft draft) {
            return new ReviewDraft(draft.caseId(), draft.revision(), draft.professional(), draft.confirmedAbsent(),
                    draft.unresolvedRequiredFields().stream().map(value -> ApiMessages.translate(value, locale)).toList(),
                    draft.extractionWarnings().stream().map(value -> ApiMessages.translate(value, locale)).toList());
        }
        if (data instanceof FiveResults result) {
            return new FiveResults(result.caseId(), result.automatedStatus(), result.job(), objective(result.objective(), locale),
                    result.neutralMl(), result.biasedMl(), result.human(), result.privacy());
        }
        if (data instanceof HrCaseDetail detail) {
            return new HrCaseDetail(detail.caseId(), detail.professional(), detail.job(), detail.matching(), detail.weights(),
                    detail.contributions(), objective(detail.objective(), locale), detail.neutralMl(), detail.biasedMl(),
                    detail.human(), detail.versions(), detail.expiresAt());
        }
        if (data instanceof Validation validation) {
            return new Validation(validation.valid(), issues(validation.errors(), locale),
                    validation.warnings().stream().map(value -> ApiMessages.translate(value, locale)).toList());
        }
        if (data instanceof CaseStatus status) {
            return new CaseStatus(status.caseId(), status.processingStatus(), status.stage(), status.retryable(),
                    status.errorCode(), ApiMessages.translate(status.message(), locale), status.cleanupStatus(),
                    status.updatedAt(), status.confirmedAt(), status.expiresAt(), status.objectiveOnly());
        }
        // Publication validation failures use a map, but arbitrary user data must remain untouched.
        if (error && data instanceof Map<?, ?> map && map.get("errors") instanceof List<?> errors
                && errors.stream().allMatch(Issue.class::isInstance)) {
            Map<Object, Object> copy = new LinkedHashMap<>(map);
            copy.put("errors", errors.stream().map(Issue.class::cast).map(issue -> issue(issue, locale)).toList());
            if (map.get("warnings") instanceof List<?> warnings && warnings.stream().allMatch(String.class::isInstance)) {
                copy.put("warnings", warnings.stream().map(String.class::cast)
                        .map(value -> ApiMessages.translate(value, locale)).toList());
            }
            return copy;
        }
        return data;
    }
    private List<Issue> issues(List<Issue> issues, Locale locale) {
        return issues.stream().map(value -> issue(value, locale)).toList();
    }
    private Issue issue(Issue issue, Locale locale) {
        return new Issue(issue.field(), issue.code(), ApiMessages.translate(issue.message(), locale));
    }
    private JsonNode objective(JsonNode value, Locale locale) {
        if (value == null || !value.isObject()) return value;
        ObjectNode copy = value.deepCopy();
        for (String field : List.of("strengths", "gaps")) {
            if (!value.path(field).isArray()) continue;
            var translated = copy.putArray(field);
            value.path(field).forEach(item -> {
                if (item.isTextual()) translated.add(ApiMessages.translate(item.asText(), locale));
                else translated.add(item);
            });
        }
        return copy;
    }
}
