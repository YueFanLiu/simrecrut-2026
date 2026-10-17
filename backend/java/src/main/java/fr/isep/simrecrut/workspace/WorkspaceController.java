package fr.isep.simrecrut.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.context.annotation.Profile("resume-workspace")
public class WorkspaceController {
  final ResumeService service;

  WorkspaceController(ResumeService service) {
    this.service = service;
  }

  static Map<String, Object> envelope(Object data) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("code", 0);
    e.put("message", "OK");
    e.put("requestId", UUID.randomUUID().toString());
    e.put("data", data);
    return e;
  }

  @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
  String index() throws Exception {
    try (var in = new ClassPathResource("static/index.html").getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8)
          .replace("__APP_TOKEN__", LocalGuard.TOKEN);
    }
  }

  @GetMapping("/api/v1/settings")
  Object settings() throws Exception {
    return envelope(service.publicSettings());
  }

  @PostMapping("/api/v1/settings")
  Object saveSettings(@RequestBody JsonNode body) throws Exception {
    return envelope(service.saveSettings(body));
  }

  @GetMapping("/api/v1/resumes")
  Object resumes() throws Exception {
    return envelope(service.list());
  }

  @GetMapping("/api/v1/resumes/{id}")
  Object resume(@PathVariable String id) throws Exception {
    return envelope(service.detail(id));
  }

  @GetMapping(value = "/api/v1/resumes/{id}/page/{page}", produces = MediaType.IMAGE_JPEG_VALUE)
  byte[] page(@PathVariable String id, @PathVariable int page) throws Exception {
    return service.page(id, page);
  }

  @PostMapping("/api/v1/import")
  Object importFile(@RequestBody JsonNode body) throws Exception {
    return envelope(service.importFile(body));
  }

  @PostMapping("/api/v1/cloud/test")
  Object test() throws Exception {
    return envelope(service.testCloud());
  }

  @PostMapping("/api/v1/resumes/{id}/save")
  Object save(@PathVariable String id, @RequestBody JsonNode body) throws Exception {
    return envelope(service.save(id, body));
  }

  @PostMapping("/api/v1/resumes/{id}/upload")
  Object upload(@PathVariable String id) throws Exception {
    return envelope(service.upload(id));
  }

  @PostMapping("/api/v1/resumes/{id}/cleanup")
  Object cleanup(@PathVariable String id) throws Exception {
    return envelope(Map.of("originalRemoved", service.cleanupOriginal(id)));
  }

  @PostMapping("/api/v1/resumes/{id}/clean")
  ResponseEntity<Object> clean(@PathVariable String id, @RequestBody JsonNode body)
      throws Exception {
    return ResponseEntity.status(202).body(envelope(service.clean(id, body)));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> error(Exception ex) {
    String message = "Operation failed. Check the file, network, and settings, then retry.";
    int status = 400;
    if (ex instanceof IllegalArgumentException) message = ex.getMessage();
    if (ex instanceof SQLException sql) {
      message =
          "Database operation failed ("
              + sql.getErrorCode()
              + "). Check connection details, CA certificate, and permissions.";
      status = 503;
    }
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "code",
                status,
                "message",
                message == null ? "Invalid input." : message,
                "requestId",
                UUID.randomUUID().toString(),
                "data",
                Map.of()));
  }
}
