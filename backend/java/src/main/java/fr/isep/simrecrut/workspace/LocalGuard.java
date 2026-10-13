package fr.isep.simrecrut.workspace;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Local single-user protection. Replace with the existing RuoYi identity layer for shared
 * deployment.
 */
@Component
@org.springframework.context.annotation.Profile("resume-workspace")
public class LocalGuard extends OncePerRequestFilter {
  static final String TOKEN;

  static {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    TOKEN = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src"
            + " 'self'; frame-ancestors 'none'; base-uri 'none'");
    String host = req.getHeader("Host");
    if (!Set.of("127.0.0.1:8765", "localhost:8765").contains(host == null ? "" : host)) {
      reject(res, 403, "Host not allowed.");
      return;
    }
    if (req.getRequestURI().startsWith("/api/")) {
      String token = req.getHeader("X-App-Token");
      if (req.getMethod().equals("GET")
          && req.getRequestURI().matches("/api/v1/resumes/[a-f0-9]{32}/page/[0-9]+"))
        token = req.getParameter("token");
      if (token == null
          || !MessageDigest.isEqual(
              TOKEN.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
        reject(res, 403, "Refresh the local page and retry.");
        return;
      }
    }
    if (req.getMethod().equals("POST")
        && (req.getContentLengthLong() < 1 || req.getContentLengthLong() > 15L * 1024 * 1024)) {
      reject(res, 413, "Invalid request size.");
      return;
    }
    chain.doFilter(req, res);
  }

  static void reject(HttpServletResponse res, int code, String message) throws IOException {
    res.setStatus(code);
    res.setContentType("application/json;charset=UTF-8");
    Facts.JSON.writeValue(
        res.getOutputStream(),
        Map.of(
            "code",
            code,
            "message",
            message,
            "requestId",
            UUID.randomUUID().toString(),
            "data",
            Map.of()));
  }
}
