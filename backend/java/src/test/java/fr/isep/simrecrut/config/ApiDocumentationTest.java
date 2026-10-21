package fr.isep.simrecrut.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.application.identity.*;
import fr.isep.simrecrut.application.resume.ResumeApplicationService;
import fr.isep.simrecrut.controller.identity.*;
import fr.isep.simrecrut.controller.job.*;
import fr.isep.simrecrut.controller.resume.*;
import fr.isep.simrecrut.security.authentication.*;
import fr.isep.simrecrut.service.identity.RoleQueryService;
import fr.isep.simrecrut.service.job.JobService;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Generates documentation from actual controllers without database, mail or external-service calls. */
@SpringBootTest(classes = ApiDocumentationTest.DocumentationApplication.class, properties = {
        "spring.profiles.active=api-documentation-test", "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true", "management.endpoint.health.validate-group-membership=false",
        "SIMRECRUT_MAIL_FILE=__missing_documentation_test_mail__.env"})
@AutoConfigureMockMvc
class ApiDocumentationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;
    @Autowired RegistrationApplicationService registrationService;
    @Autowired AuthenticationApplicationService authenticationService;
    @Autowired ResumeApplicationService resumeService;
    @Autowired RoleQueryService roleService;
    @Autowired JobService jobService;

    JsonNode spec() throws Exception {
        return json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @AfterEach void documentationNeverExecutesBusinessOperations() {
        verifyNoInteractions(registrationService, authenticationService, resumeService, roleService, jobService);
    }

    @Test void everyActivePublicControllerMappingIsDocumented() throws Exception {
        JsonNode paths = spec().path("paths");
        long expected = mappings.getHandlerMethods().entrySet().stream()
                .filter(e -> e.getValue().getBeanType().getPackageName().startsWith("fr.isep.simrecrut.controller"))
                .flatMap(e -> e.getKey().getPatternValues().stream()
                        .flatMap(path -> e.getKey().getMethodsCondition().getMethods().stream().map(method -> {
                            assertTrue(paths.path(path).has(method.name().toLowerCase(Locale.ROOT)), path + " " + method);
                            return method;
                        }))).count();
        long actual = 0;
        for (JsonNode path : paths) actual += path.size();
        assertEquals(expected, actual);
        assertFalse(paths.has("/api/v1/settings"));
        assertFalse(paths.has("/api/v1/cases"));
        assertTrue(expected >= 32);
    }

    @Test void swaggerUiAndGroupedDocumentsAreReadableWithoutAToken() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
        JsonNode ui = json.readTree(mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("list", ui.path("docExpansion").asText());
        assertEquals(0, ui.path("defaultModelsExpandDepth").asInt(-1));
        assertEquals("all", ui.path("urls.primaryName").asText());
        JsonNode all = json.readTree(mvc.perform(get("/v3/api-docs/all")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(all.path("paths").has("/api/v1/auth/login"));
        assertTrue(all.path("paths").has("/api/v1/resumes"));
        assertTrue(all.path("paths").has("/api/v1/hr/jobs"));
        JsonNode group = json.readTree(mvc.perform(get("/v3/api-docs/jobs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(group.path("paths").has("/api/v1/hr/jobs/{jobId}/drafts"));
        assertFalse(group.path("paths").has("/api/v1/auth/login"));
        mvc.perform(get("/v3/api-docs.yaml/jobs")).andExpect(status().isOk());
    }

    @Test void publicLoginAndProtectedRoutesHaveCorrectAuthenticationMetadata() throws Exception {
        JsonNode document = spec();
        assertTrue(document.at("/paths/~1api~1v1~1auth~1login/post/security").isEmpty());
        assertEquals("bearerAuth", document.at("/paths/~1api~1v1~1auth~1me/get/security/0")
                .fieldNames().next());
        assertEquals("bearer", document.at("/components/securitySchemes/bearerAuth/scheme").asText());
        assertTrue(document.at("/components/schemas/LoginRequest/properties/password/writeOnly").asBoolean());
        assertEquals("CANDIDATE", document.at("/components/schemas/RegisterRequest/properties/accountType/enum/0").asText());
        assertFalse(document.at("/paths/~1api~1v1~1auth~1me/get/parameters").toString().contains("sessionId"));
        mvc.perform(get("/api/v1/hr/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void dynamicResponseFieldsAndActualStatusesAreExplained() throws Exception {
        JsonNode document = spec();
        JsonNode paths = document.path("paths");
        assertTrue(paths.at("/~1api~1v1~1hr~1jobs/post/responses").has("201"));
        assertFalse(paths.at("/~1api~1v1~1hr~1jobs/post/responses").has("200"));
        assertTrue(paths.at("/~1api~1v1~1hr~1jobs~1{jobId}~1extract-requirements/post/responses").has("202"));
        assertTrue(paths.at("/~1api~1v1~1resumes~1{id}/delete/responses").has("202"));
        JsonNode schemas = document.path("components").path("schemas");
        assertTrue(schemas.path("JobManagedDetail").path("properties").has("weights"));
        assertFalse(schemas.path("JobPublicDetail").path("properties").has("weights"));
        assertTrue(schemas.path("ResumeDetail").path("properties").has("draft"));
        assertFalse(schemas.path("ResumeDetail").path("properties").has("original_text"));
        assertEquals(5, schemas.path("Requirement").path("properties").path("payload").path("oneOf").size());
        assertEquals("#/components/schemas/ResumeDraft", schemas.path("EditRequest").path("properties").path("draft").path("$ref").asText());
        JsonNode cleanupData = paths.at("/~1api~1v1~1resumes~1{id}~1cleanup/post/responses/200/content/application~1json/schema/properties/data");
        assertEquals("boolean", cleanupData.path("type").asText());
    }

    @Test void yamlCanBeExportedAsACompleteReferenceFile() throws Exception {
        String yaml = mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode document = io.swagger.v3.core.util.Yaml.mapper().readTree(yaml);
        assertEquals(spec().path("paths"), document.path("paths"));
        assertEquals("/", document.path("servers").path(0).path("url").asText());
        String output = System.getProperty("simrecrut.docs.export", "");
        if (!output.isBlank()) {
            Path target = Path.of(output).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, yaml.replace("\r\n", "\n"), StandardCharsets.UTF_8);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class, MailSenderAutoConfiguration.class})
    @Import({ApiDocumentationConfig.class, SecurityConfig.class, AuthenticationErrorHandler.class,
            AuthenticationController.class, AdminRoleController.class, ResumeController.class,
            HrJobController.class, CandidateJobController.class})
    static class DocumentationApplication {
        @Bean RegistrationApplicationService registrationApplicationService() { return mock(RegistrationApplicationService.class); }
        @Bean AuthenticationApplicationService authenticationApplicationService() { return mock(AuthenticationApplicationService.class); }
        @Bean ResumeApplicationService resumeApplicationService() { return mock(ResumeApplicationService.class); }
        @Bean RoleQueryService roleQueryService() { return mock(RoleQueryService.class); }
        @Bean JobService jobService() { return mock(JobService.class); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean JwtSessionAuthenticationConverter converter() { return mock(JwtSessionAuthenticationConverter.class); }
    }
}

@SpringBootTest(classes = ApiDocumentationTest.DocumentationApplication.class, properties = {
        "spring.profiles.active=api-documentation-test", "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false", "management.endpoint.health.validate-group-membership=false",
        "SIMRECRUT_MAIL_FILE=__missing_documentation_test_mail__.env"})
@AutoConfigureMockMvc
class ApiDocumentationDisabledTest {
    @Autowired MockMvc mvc;

    @Test void disablingDocumentationDoesNotEnableBusinessAccess() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hr/jobs")).andExpect(status().isUnauthorized());
    }
}
