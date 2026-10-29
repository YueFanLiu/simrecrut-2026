package fr.isep.simrecrut.assessment;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fr.isep.simrecrut.config.SecurityConfig;
import fr.isep.simrecrut.controller.candidate.*;
import fr.isep.simrecrut.security.authentication.*;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.assessment.CandidateCaseService;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.CreateCaseRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class CandidateCaseApiTest {
    @TempDir Path temporary;
    static CaseTestSupport f;
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void start() throws Exception {
        f = new CaseTestSupport(temporary); context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); f.worker.stop(); }

    @Test void anonymousAndWrongRoleCannotReadCases() throws Exception {
        mvc.perform(get("/api/v1/candidate/cases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/candidate/cases").with(auth("HR", "list"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/candidate/cases").with(auth("CANDIDATE"))).andExpect(status().isForbidden());
        verify(f.db, never()).list(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test void createUsesDocumentPathAndCanonicalEnvelope() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"jobVersionId\":\"" + f.job.publicId + "\"}";
        String response = mvc.perform(post("/api/v1/candidate/cases").with(auth("CANDIDATE", "create"))
                .header("Idempotency-Key", key).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("code").value(0)).andExpect(jsonPath("data.processingStatus").value("AWAITING_UPLOAD"))
                .andReturn().getResponse().getContentAsString();
        String id = f.json.readTree(response).path("data").path("caseId").asText();
        mvc.perform(get("/api/v1/candidate/cases/" + id + "/status").with(auth("CANDIDATE", "list")))
                .andExpect(status().isOk()).andExpect(jsonPath("data.stage").value("UPLOAD"));
    }

    @Test void strictRequestsRejectOwnerSpoofingAndMissingKeys() throws Exception {
        mvc.perform(post("/api/v1/candidate/cases").with(auth("CANDIDATE", "create"))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"jobVersionId\":\"" + f.job.publicId + "\",\"candidateUserId\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/candidate/cases").with(auth("CANDIDATE", "create"))
                .contentType("application/json").content("{\"jobVersionId\":\"" + f.job.publicId + "\"}"))
                .andExpect(status().isBadRequest());
        verify(f.db, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test void uploadRequiresMultipartPdfAndReturnsAccepted() throws Exception {
        String id = create();
        mvc.perform(multipart("/api/v1/candidate/cases/" + id + "/cv")
                .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", CandidateCaseWorkflowTest.pdf("Java Skills")))
                .header("Idempotency-Key", UUID.randomUUID().toString()).with(auth("CANDIDATE", "upload")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("data.processingStatus").value("EXTRACTING"));
    }

    @Test void unconfirmedRunIsAConflictRatherThanFakeSuccess() throws Exception {
        String id = create();
        mvc.perform(post("/api/v1/candidate/cases/" + id + "/run").header("Idempotency-Key", UUID.randomUUID().toString())
                .with(auth("CANDIDATE", "run")).contentType("application/json").content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("data.errorCode").value("CASE_CONFLICT"));
    }

    @Test void malformedIdsAndNestedUnknownFieldsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/candidate/cases/not-an-id/status").with(auth("CANDIDATE", "list")))
                .andExpect(status().isBadRequest());
        String id = create();
        mvc.perform(put("/api/v1/candidate/cases/" + id + "/research-attributes").with(auth("CANDIDATE", "edit"))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"revision\":1,\"attributes\":[{\"type\":\"GENDER\",\"value\":\"UNKNOWN\",\"source\":\"EXTRACTED\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test void deletionImmediatelyRevokesOwnerReads() throws Exception {
        String id = create();
        mvc.perform(delete("/api/v1/candidate/cases/" + id).with(auth("CANDIDATE", "delete"))
                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("data.status").value("PURGING"));
        mvc.perform(get("/api/v1/candidate/cases/" + id + "/status").with(auth("CANDIDATE", "list")))
                .andExpect(status().isNotFound());
    }

    private String create() {
        return f.service.create(f.actor, UUID.randomUUID().toString(), new CreateCaseRequest(f.job.publicId)).data().path("caseId").asText();
    }
    private RequestPostProcessor auth(String role, String... permissions) {
        var authorities = new ArrayList<SimpleGrantedAuthority>(); authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String permission : permissions) authorities.add(new SimpleGrantedAuthority("simrecrut:case:" + permission));
        return authentication(new UsernamePasswordAuthenticationToken(f.actor, null, authorities));
    }
    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, CandidateCaseController.class, CandidateCaseExceptionHandler.class, AuthenticationErrorHandler.class})
    static class Config {
        @Bean com.fasterxml.jackson.databind.ObjectMapper json() { return f.json; }
        @Bean CandidateCaseService cases() { return f.service; }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean JwtSessionAuthenticationConverter converter() { return mock(JwtSessionAuthenticationConverter.class); }
    }
}
