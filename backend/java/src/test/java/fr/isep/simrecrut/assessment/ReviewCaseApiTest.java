package fr.isep.simrecrut.assessment;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import fr.isep.simrecrut.config.SecurityConfig;
import fr.isep.simrecrut.controller.review.*;
import fr.isep.simrecrut.security.authentication.*;
import fr.isep.simrecrut.service.assessment.ReviewCaseService;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;

class ReviewCaseApiTest {
    @TempDir Path directory;
    static ReviewCaseWorkflowTest f;
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void start() throws Exception {
        f = new ReviewCaseWorkflowTest(); f.directory = directory; f.setup();
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void stop() { context.close(); f.close(); }
    @Test void anonymousWrongRoleAndMissingPermissionAreRejected() throws Exception {
        mvc.perform(get("/api/v1/hr/cases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/hr/cases").with(auth("CANDIDATE", "simrecrut:hr-case:list"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/hr/cases").with(auth("HR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reviewer/tasks").with(auth("ADMIN", "simrecrut:review:list"))).andExpect(status().isForbidden());
        verify(f.db, never()).hrCases(anyLong(), anyString(), anyInt(), anyInt());
    }
    @Test void hrReadsExactDocumentRoutesWithNoStoreEnvelope() throws Exception {
        mvc.perform(get("/api/v1/hr/cases").param("jobId", f.f.job.jobPublicId)
                .with(auth("HR", "simrecrut:hr-case:list"))).andExpect(status().isOk())
                .andExpect(jsonPath("code").value(0)).andExpect(jsonPath("data.total").value(0));
        mvc.perform(get("/api/v1/hr/cases/" + f.caseId).with(auth("HR", "simrecrut:hr-case:detail")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("data.caseId").value(f.caseId));
    }
    @Test void assignmentRequiresResearchRoleAndRejectsSpoofedFields() throws Exception {
        String body = "{\"assignments\":[{\"reviewerId\":\"" + f.reviewerId + "\",\"condition\":\"STANDARD\"}]}";
        mvc.perform(post("/api/v1/research/cases/" + f.caseId + "/reviews")
                .with(auth("HR", "simrecrut:research:review:assign")).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/research/cases/" + f.caseId + "/reviews")
                .with(auth("RESEARCHER", "simrecrut:research:review:assign")).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(body.replace("\"STANDARD\"", "\"STANDARD\",\"instruction\":\"Override\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/research/cases/" + f.caseId + "/reviews")
                .with(auth("RESEARCHER", "simrecrut:research:review:assign")).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("data.tasks[0].status").value("PENDING"));
    }
    @Test void missingOperationKeyAndInvalidDecisionAreBadRequests() throws Exception {
        String id = "01GGGGGGGGGGGGGGGGGGGGGGGG";
        mvc.perform(post("/api/v1/reviewer/tasks/" + id + "/submit").with(auth("HR", "simrecrut:review:submit"))
                .contentType("application/json").content("{\"decision\":\"ACCEPT\",\"reason\":\"Fits\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reviewer/tasks/" + id + "/submit").with(auth("HR", "simrecrut:review:submit"))
                .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                .content("{\"decision\":\"MAYBE\",\"reason\":\"Fits\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void eligibleReviewerRouteIsScopedAndBounded() throws Exception {
        mvc.perform(get("/api/v1/research/cases/" + f.caseId + "/eligible-reviewers")
                .with(auth("RESEARCHER", "simrecrut:research:review:assign"))).andExpect(status().isOk());
        when(f.db.researchAccess(10, 2)).thenReturn(false);
        mvc.perform(get("/api/v1/research/cases/" + f.caseId + "/eligible-reviewers")
                .with(auth("RESEARCHER", "simrecrut:research:review:assign"))).andExpect(status().isNotFound());
    }
    @Test void reviewerTaskListAndDetailUseAssignedUserOnly() throws Exception {
        mvc.perform(get("/api/v1/reviewer/tasks").param("status", "PENDING")
                .with(auth("HR", "simrecrut:review:list"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reviewer/tasks/01GGGGGGGGGGGGGGGGGGGGGGGG")
                .with(auth("HR", "simrecrut:review:detail"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hr/cases/invalid").with(auth("HR", "simrecrut:hr-case:detail")))
                .andExpect(status().isBadRequest());
    }
    @Test void mapperStatementsResolveAllParametersAndScopeFragments() throws Exception {
        var config = new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/assessment/ReviewCaseMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        var params = new HashMap<String, Object>();
        params.put("actor", 2L); params.put("caseId", 10L); params.put("status", "PENDING"); params.put("job", "job");
        params.put("size", 20); params.put("offset", 0); params.put("query", "reviewer"); params.put("limit", 50);
        String prefix = "fr.isep.simrecrut.mapper.assessment.ReviewCaseMapper.";
        for (String name : List.of("hrCases", "countHrCases", "hrAccess", "researchAccess", "reviewerAccess", "eligible", "tasks", "countTasks")) {
            String sql = config.getMappedStatement(prefix + name).getBoundSql(params).getSql();
            assertTrue(sql.contains("UTC_TIMESTAMP(3)")); assertFalse(sql.contains("${"));
        }
        String eligible = config.getMappedStatement(prefix + "eligible").getBoundSql(params).getSql();
        assertTrue(eligible.contains("HR_CASE_RESULTS_READ")); assertTrue(eligible.contains("u.id != c.candidate_user_id"));
        assertTrue(eligible.contains("simrecrut:review:submit"));
    }
    private RequestPostProcessor auth(String role, String... permissions) {
        var authorities = new ArrayList<SimpleGrantedAuthority>(); authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String value : permissions) authorities.add(new SimpleGrantedAuthority(value));
        return authentication(new UsernamePasswordAuthenticationToken(f.actor, null, authorities));
    }
    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, HrCaseController.class, ReviewerTaskController.class, ResearchCaseReviewController.class,
            ReviewCaseExceptionHandler.class, AuthenticationErrorHandler.class})
    static class Config {
        @Bean com.fasterxml.jackson.databind.ObjectMapper json() { return f.f.json; }
        @Bean ReviewCaseService service() { return f.service; }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean JwtSessionAuthenticationConverter converter() { return mock(JwtSessionAuthenticationConverter.class); }
    }
}
