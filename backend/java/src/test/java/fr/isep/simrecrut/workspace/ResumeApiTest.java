package fr.isep.simrecrut.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.isep.simrecrut.application.resume.ResumeApplicationService;
import fr.isep.simrecrut.config.SecurityConfig;
import fr.isep.simrecrut.controller.resume.ResumeController;
import fr.isep.simrecrut.controller.resume.ResumeExceptionHandler;
import fr.isep.simrecrut.security.authentication.AuthenticationErrorHandler;
import fr.isep.simrecrut.security.authentication.JwtSessionAuthenticationConverter;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ResumeApiTest {

    private static final String API = "/api/v1/resumes";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir static Path directory;
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static DataSource dataSource;

    @BeforeAll
    static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfiguration.class);
        context.refresh();
        dataSource = context.getBean(DataSource.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
    }

    @BeforeEach
    void resetCloud() throws Exception {
        reset(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("Private connection diagnostic."));
    }

    private static MockHttpServletRequestBuilder candidate(MockHttpServletRequestBuilder request, long id) {
        return actor(request, id, "CANDIDATE", "simrecrut:resume:list", "simrecrut:resume:upload",
                "simrecrut:resume:edit", "simrecrut:resume:confirm", "simrecrut:resume:delete");
    }

    private static MockHttpServletRequestBuilder actor(
            MockHttpServletRequestBuilder request, long id, String role, String... permissions) {
        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        Arrays.stream(permissions).map(SimpleGrantedAuthority::new).forEach(authorities::add);
        var principal = new AccountPrincipal(id, "01ARZ3NDEKTSV4RRFFQ69G5FAV", UUID.randomUUID().toString(),
                0, Instant.now().plusSeconds(1800));
        return request.with(authentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities)));
    }

    private static MockHttpServletRequestBuilder write(String path, JsonNode body) {
        return post(path).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString());
    }

    private static byte[] pdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(30, 700);
                stream.showText("Technical skills: Java and Docker");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static ObjectNode importBody() throws Exception {
        return JSON.createObjectNode().put("filename", "resume.pdf")
                .put("content", Base64.getEncoder().encodeToString(pdf(1)));
    }

    private static String create(long userId) throws Exception {
        return data(mvc.perform(candidate(write(API + "/import", importBody()), userId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    private static JsonNode data(String response) throws Exception {
        return JSON.readTree(response).path("data");
    }

    private static ObjectNode edit(long revision, boolean confirmed) {
        ObjectNode draft = Facts.empty();
        draft.withObject("professional").withArray("skills").addObject().put("skillCode", "Java");
        ObjectNode request = JSON.createObjectNode().put("confirmed", confirmed).put("expectedRevision", revision);
        request.set("draft", draft);
        return request;
    }

    @Test
    void anonymousReadAndWriteRequireAuthentication() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value(401));
        mvc.perform(write(API + "/import", importBody())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "HR", "RESEARCHER"})
    void otherRolesCannotReadCandidateContentEvenWithPermission(String role) throws Exception {
        mvc.perform(actor(get(API), 1, role, "simrecrut:resume:list")).andExpect(status().isForbidden());
    }

    @Test
    void candidateNeedsOperationPermission() throws Exception {
        mvc.perform(actor(get(API), 1, "CANDIDATE")).andExpect(status().isForbidden());
        mvc.perform(actor(write(API + "/import", importBody()), 1, "CANDIDATE", "simrecrut:resume:list"))
                .andExpect(status().isForbidden());
    }

    @Test
    void importReplayAndDetailStayWithinAuthenticatedAccount() throws Exception {
        ObjectNode body = importBody();
        String key = UUID.randomUUID().toString();
        String response = mvc.perform(candidate(post(API + "/import").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 2))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = data(response).path("id").asText();
        mvc.perform(candidate(post(API + "/import").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()), 2))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.id").value(id));
        mvc.perform(candidate(get(API + "/" + id), 2)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("data.draft.professional").exists())
                .andExpect(jsonPath("data.original_text").doesNotExist())
                .andExpect(jsonPath("data.redacted_text").doesNotExist())
                .andExpect(jsonPath("data.sha256").doesNotExist());
        mvc.perform(candidate(get(API + "/" + id), 3)).andExpect(status().isNotFound());
        mvc.perform(candidate(get(API), 3)).andExpect(jsonPath("data").isEmpty());
        mvc.perform(candidate(delete(API + "/" + id).header("Idempotency-Key", key), 3))
                .andExpect(status().isNotFound());
        mvc.perform(candidate(post(API + "/import").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body.put("filename", "different.pdf").toString()), 2))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedInputsAndIdentitySpoofingAreRejected() throws Exception {
        mvc.perform(candidate(write(API + "/import", importBody().put("ownerUserId", 99)), 4))
                .andExpect(status().isBadRequest());
        mvc.perform(candidate(write(API + "/import", importBody().put("filename", "resume.txt")), 4))
                .andExpect(status().isBadRequest());
        mvc.perform(candidate(write(API + "/import", importBody().put("content", "not-base64")), 4))
                .andExpect(status().isBadRequest());
        mvc.perform(candidate(post(API + "/import").contentType(MediaType.APPLICATION_JSON)
                        .content(importBody().toString()), 4)).andExpect(status().isBadRequest());
        mvc.perform(candidate(get(API + "/invalid-id"), 4)).andExpect(status().isBadRequest());
        ObjectNode large = importBody().put("content", Base64.getEncoder().encodeToString(pdf(21)));
        mvc.perform(candidate(write(API + "/import", large), 4)).andExpect(status().isBadRequest());
    }

    @Test
    void revisionsConfirmationAndDeletionAreEnforced() throws Exception {
        String id = create(5);
        mvc.perform(candidate(write(API + "/" + id + "/save", edit(1, false)), 5)).andExpect(status().isOk());
        mvc.perform(candidate(write(API + "/" + id + "/save", edit(1, false)), 5)).andExpect(status().isConflict());
        mvc.perform(candidate(write(API + "/" + id + "/save", edit(2, true)), 5)).andExpect(status().isBadRequest());
        mvc.perform(candidate(write(API + "/" + id + "/confirm", edit(2, false)), 5)).andExpect(status().isBadRequest());
        mvc.perform(candidate(write(API + "/" + id + "/confirm", edit(2, true)), 5)).andExpect(status().isOk());
        mvc.perform(candidate(write(API + "/" + id + "/save", edit(3, false)), 5)).andExpect(status().isConflict());
        mvc.perform(candidate(write(API + "/" + id + "/clean", JSON.createObjectNode()), 5))
                .andExpect(status().isConflict());
        mvc.perform(candidate(delete(API + "/" + id).header("Idempotency-Key", "delete-resume-operation-123"), 5))
                .andExpect(status().isOk()).andExpect(jsonPath("data.deleted").value(true));
        mvc.perform(candidate(delete(API + "/" + id).header("Idempotency-Key", "delete-resume-operation-123"), 5))
                .andExpect(status().isOk());
        mvc.perform(candidate(get(API + "/" + id), 5)).andExpect(status().isNotFound());
    }

    @Test
    void extractionReturnsAcceptedAndPersistsReviewedFacts() throws Exception {
        String id = create(6);
        mvc.perform(candidate(write(API + "/" + id + "/clean", JSON.createObjectNode()), 6))
                .andExpect(status().isAccepted()).andExpect(jsonPath("data.processing").value(true));
        JsonNode record = null;
        for (int i = 0; i < 100; i++) {
            record = data(mvc.perform(candidate(get(API + "/" + id), 6)).andReturn()
                    .getResponse().getContentAsString());
            if (!record.path("status").asText().equals("EXTRACTING")) break;
            Thread.sleep(50);
        }
        assertNotNull(record);
        assertEquals("REVIEW_REQUIRED", record.path("status").asText());
        assertFalse(record.path("draft").path("professional").path("skills").isEmpty());
    }

    @Test
    void publicationUsesApplicationDataSourceAndCleansOriginal() throws Exception {
        String id = create(7);
        mvc.perform(candidate(write(API + "/" + id + "/save", edit(1, false)), 7));
        mvc.perform(candidate(write(API + "/" + id + "/confirm", edit(2, true)), 7));
        Connection connection = mock(Connection.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        doReturn(connection).when(dataSource).getConnection();
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(connection.prepareStatement(anyString())).thenReturn(insert);
        mvc.perform(candidate(write(API + "/" + id + "/upload", JSON.createObjectNode()), 7))
                .andExpect(status().isOk()).andExpect(jsonPath("data.uploaded").value(true))
                .andExpect(jsonPath("data.originalRemoved").value(true))
                .andExpect(jsonPath("data.table").doesNotExist());
        verify(connection).commit();
        mvc.perform(candidate(write(API + "/" + id + "/cleanup", JSON.createObjectNode()), 7))
                .andExpect(status().isOk()).andExpect(jsonPath("data").value(true));
        mvc.perform(candidate(get(API + "/" + id), 7)).andExpect(jsonPath("data.originalAvailable").value(false));
    }

    @Test
    void unavailablePublicationKeepsFactsAndDeletionBlocksAccess() throws Exception {
        String id = create(8);
        mvc.perform(candidate(write(API + "/" + id + "/save", edit(1, false)), 8));
        mvc.perform(candidate(write(API + "/" + id + "/confirm", edit(2, true)), 8));
        mvc.perform(candidate(write(API + "/" + id + "/upload", JSON.createObjectNode()), 8))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("data.retryable").value(true))
                .andExpect(jsonPath("message").value("The operation could not finish. Retry with the same operation key."));
        mvc.perform(candidate(get(API + "/" + id), 8)).andExpect(jsonPath("data.confirmed").value(1));
        mvc.perform(candidate(delete(API + "/" + id).header("Idempotency-Key", UUID.randomUUID().toString()), 8))
                .andExpect(status().isAccepted()).andExpect(jsonPath("data.pending").value(true));
        mvc.perform(candidate(get(API + "/" + id), 8)).andExpect(status().isNotFound());
    }

    @Test
    void actionBodiesCannotSmuggleFieldsOrConfirmWithMissingRevision() throws Exception {
        String id = create(9);
        mvc.perform(candidate(write(API + "/" + id + "/clean", JSON.createObjectNode().put("owner", 1)), 9))
                .andExpect(status().isBadRequest());
        ObjectNode body = edit(1, false);
        body.remove("expectedRevision");
        mvc.perform(candidate(write(API + "/" + id + "/save", body), 9)).andExpect(status().isBadRequest());
        mvc.perform(candidate(write(API + "/" + id + "/cleanup", JSON.createObjectNode()), 9))
                .andExpect(status().isConflict());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ResumeController.class, ResumeExceptionHandler.class,
            ResumeApplicationService.class, LocalResumeGateway.class, AuthenticationErrorHandler.class})
    static class TestConfiguration {

        @Bean UserResumeServices users(DataSource source) {
            return new UserResumeServices(directory.toString(), source);
        }
        @Bean DataSource dataSource() { return mock(DataSource.class); }
        @Bean ObjectMapper objectMapper() { return JSON; }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean JwtSessionAuthenticationConverter converter() { return mock(JwtSessionAuthenticationConverter.class); }
    }
}
