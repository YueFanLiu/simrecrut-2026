package fr.isep.simrecrut.workspace;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Apply the business envelope even when authentication rejects a request before its controller. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@org.springframework.context.annotation.Profile("!resume-workspace")
public class BusinessResponseFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/v1/resumes"); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        var wrapped = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapped);
            if (wrapped.getContentType() != null && wrapped.getContentType().contains("application/json")) {
                var body = Facts.JSON.readTree(wrapped.getContentAsByteArray());
                // Controller responses already follow the contract; only adapt RuoYi errors.
                if (body.has("message") && body.has("requestId") && body.has("data")) {
                    response.setHeader("X-Request-Id", body.path("requestId").asText());
                    return;
                }
                int code = body.path("code").asInt(wrapped.getStatus()); if (code == 200) code = 0;
                if (code >= 400 && code <= 599 && wrapped.getStatus() < 400) wrapped.setStatus(code);
                var envelope = Facts.JSON.createObjectNode().put("code", code)
                    .put("message", body.path("message").asText(body.path("msg").asText(code == 0 ? "OK" : "Request failed.")))
                    .put("requestId", body.path("requestId").asText(UUID.randomUUID().toString()));
                if (body.has("data")) envelope.set("data", body.get("data"));
                else envelope.set("data", Facts.JSON.createObjectNode().put("retryable", code >= 500));
                wrapped.resetBuffer(); Facts.JSON.writeValue(wrapped.getOutputStream(), envelope);
                response.setHeader("X-Request-Id", envelope.path("requestId").asText());
            }
        } finally { wrapped.copyBodyToResponse(); }
    }
}
