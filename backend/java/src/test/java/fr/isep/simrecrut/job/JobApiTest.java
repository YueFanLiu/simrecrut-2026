package fr.isep.simrecrut.job;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.config.SecurityConfig;
import fr.isep.simrecrut.controller.job.*;
import fr.isep.simrecrut.dto.job.JobDtos.*;
import fr.isep.simrecrut.security.authentication.*;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.job.*;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class JobApiTest {
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    static JobService jobs;
    static ObjectMapper json = new ObjectMapper();
    static final String ID = "01AAAAAAAAAAAAAAAAAAAAAAAA";
    static final String KEY = "job-operation-key-0001";

    @BeforeAll static void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        jobs = context.getBean(JobService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void close() { context.close(); }
    @BeforeEach void resetMocks() { reset(jobs); }

    static org.springframework.test.web.servlet.request.RequestPostProcessor hr() {
        AccountPrincipal actor = new AccountPrincipal(1, ID, UUID.randomUUID().toString(), 0, Instant.now().plusSeconds(600));
        return authentication(new UsernamePasswordAuthenticationToken(actor, "", List.of(new SimpleGrantedAuthority("ROLE_HR"))));
    }

    @Test void anonymousAndWrongRoleAreDenied() throws Exception {
        mvc.perform(get("/api/v1/hr/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/hr/jobs").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(jobs);
    }

    @Test void candidateUsesDocumentRouteAndReceivesPagedEnvelope() throws Exception {
        when(jobs.list(any(), eq(true), eq(2), eq(5), eq("Java"))).thenReturn(new Page<>(List.of(), 0, 2, 5));
        mvc.perform(get("/api/v1/candidate/jobs?page=2&pageSize=5&keyword=Java")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("code").value(0))
                .andExpect(jsonPath("data.page").value(2)).andExpect(jsonPath("data.items").isArray());
    }

    @Test void documentDraftReadAndUpdateAreMapped() throws Exception {
        when(jobs.detail(any(), eq(ID), eq(false))).thenReturn(json.readTree("{\"revision\":1}"));
        mvc.perform(get("/api/v1/hr/jobs/" + ID).with(hr())).andExpect(status().isOk());
        mvc.perform(put("/api/v1/hr/jobs/" + ID).with(hr()).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Java\",\"description\":\"Java required\",\"revision\":1}"))
                .andExpect(status().isOk());
        verify(jobs).update(any(), eq(ID), eq(KEY), eq(new UpdateRequest("Java", "Java required", 1L)));
    }

    @Test void missingRevisionUnknownFieldsAndMissingKeyAreBadRequests() throws Exception {
        for (String body : List.of("{\"title\":\"Java\",\"description\":\"Java required\"}",
                "{\"title\":\"Java\",\"description\":\"Java required\",\"revision\":1,\"ownerUserId\":9}")) {
            mvc.perform(put("/api/v1/hr/jobs/" + ID).with(hr()).header("Idempotency-Key", KEY)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/hr/jobs").with(hr()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Java\",\"description\":\"Java required\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(jobs);
    }

    @Test void asynchronousExtractionReturns202OnDocumentPath() throws Exception {
        when(jobs.extract(any(), eq(ID), eq(KEY))).thenReturn(json.readTree("{\"status\":\"RUNNING\",\"revision\":2}"));
        mvc.perform(post("/api/v1/hr/jobs/" + ID + "/extract-requirements").with(hr())
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("data.status").value("RUNNING"));
    }

    @Test void cloneUsesPluralDraftsAndDocumentSourceField() throws Exception {
        mvc.perform(post("/api/v1/hr/jobs/" + ID + "/drafts").with(hr())
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceJobVersionId\":\"" + ID + "\"}"))
                .andExpect(status().isCreated());
        verify(jobs).cloneDraft(any(), eq(ID), eq(KEY), eq(new CloneRequest(ID)));
    }

    @Test void conflictResponseIncludesExistingDraftAndSanitizedMessage() throws Exception {
        when(jobs.cloneDraft(any(), anyString(), anyString(), any())).thenThrow(new JobException(409,
                "An editable draft already exists.", Map.of("draftId", ID, "revision", 2)));
        mvc.perform(post("/api/v1/hr/jobs/" + ID + "/drafts").with(hr())
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceJobVersionId\":\"" + ID + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("data.draftId").value(ID))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void mybatisMappingsLoadWithoutARealDatabase() throws Exception {
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        try (var input = new ClassPathResource("mapper/job/JobMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/job/JobMapper.xml", config.getSqlFragments()).parse();
        }
        org.junit.jupiter.api.Assertions.assertTrue(config.hasStatement("fr.isep.simrecrut.mapper.job.JobMapper.list"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, HrJobController.class, CandidateJobController.class,
            JobExceptionHandler.class, AuthenticationErrorHandler.class})
    static class Config {
        @Bean JobService jobs() { return mock(JobService.class); }
        @Bean ObjectMapper json() { return json; }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean JwtSessionAuthenticationConverter converter() { return mock(JwtSessionAuthenticationConverter.class); }
    }
}
