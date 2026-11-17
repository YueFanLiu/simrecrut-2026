package fr.isep.simrecrut.i18n;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.isep.simrecrut.auth.PasswordResetMail;
import fr.isep.simrecrut.config.properties.RegistrationProperties;
import fr.isep.simrecrut.dto.identity.RegistrationDtos.ApiResponse;
import fr.isep.simrecrut.dto.assessment.CandidateCaseDtos.*;
import fr.isep.simrecrut.dto.job.JobDtos.Issue;
import fr.isep.simrecrut.dto.job.JobDtos.Validation;
import fr.isep.simrecrut.integration.mail.RegistrationMailSender;
import fr.isep.simrecrut.security.authentication.AuthenticationErrorHandler;
import fr.isep.simrecrut.service.identity.AuthenticationFlowException;
import jakarta.servlet.ServletException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ApiLanguageTest {
    final JavaMailSender sender = mock(JavaMailSender.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        LocaleContextHolder.resetLocaleContext();
        var properties = new RegistrationProperties(Duration.ofMinutes(10), Duration.ofSeconds(60),
                5, 5, 30, 500, 60, "test-only-not-used-by-mail");
        var registration = new RegistrationMailSender(sender, properties, "mail@example.test", "");
        var recovery = new PasswordResetMail(sender, "mail@example.test", "test-only", "");
        mvc = MockMvcBuilders.standaloneSetup(new Probe(registration, recovery))
                .setControllerAdvice(new ApiLanguageResponseAdvice())
                .setLocaleResolver(new ApiLanguageConfiguration().localeResolver())
                .addFilters(new ApiLanguageFilter()).build();
    }
    @AfterEach void resetContext() { LocaleContextHolder.resetLocaleContext(); }

    @Test void negotiatesRegionalWeightedAndInvalidHeadersWithoutUsingHostLocale() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, ApiMessages.resolveLocale("zh-Hant-TW, en;q=0.5"));
        assertEquals(Locale.FRENCH, ApiMessages.resolveLocale("de-DE, fr-CA;q=0.8, en;q=0.5"));
        assertEquals(Locale.ENGLISH, ApiMessages.resolveLocale("fr;q=0,en;q=0.5"));
        assertEquals(Locale.ENGLISH, ApiMessages.resolveLocale("fr;q=bad"));
        assertEquals(Locale.ENGLISH, ApiMessages.resolveLocale("de"));
        assertEquals(Locale.ENGLISH, ApiMessages.resolveLocale(null));
        assertEquals(Locale.ENGLISH, ApiMessages.resolveLocale("x".repeat(1025)));
        assertEquals(Locale.ENGLISH, ApiMessages.currentLocale());
        assertEquals("external diagnostic", ApiMessages.translate("external diagnostic", Locale.FRENCH));
    }

    @Test void cataloguesHaveIdenticalKeysAndPreserveTemplateParameters() throws Exception {
        Properties english = properties("messages.properties");
        assertTrue(english.size() > 200);
        for (String suffix : List.of("zh", "fr")) {
            Properties translated = properties("messages_" + suffix + ".properties");
            assertEquals(english.stringPropertyNames(), translated.stringPropertyNames());
            for (String key : english.stringPropertyNames()) {
                String value = translated.getProperty(key);
                assertFalse(value.isBlank(), key);
                assertEquals(placeholders(english.getProperty(key)), placeholders(value), key);
            }
        }
        assertEquals("Votre code de vérification d’inscription est : {seconds}\n\nCe code expire dans 600 secondes et ne peut être utilisé qu’une fois.\nSi vous n’avez pas demandé ce code, ignorez ce message.",
                ApiMessages.format("mail.registration.body", Locale.FRENCH, Map.of("code", "{seconds}", "seconds", "600")));
    }

    @Test void localizesEnvelopeInAllLanguagesWithoutChangingBusinessData() throws Exception {
        for (String locale : List.of("en", "zh-CN", "fr")) {
            var selected = ApiMessages.resolveLocale(locale);
            mvc.perform(get("/api/language/probe").header("Accept-Language", locale))
                    .andExpect(status().isOk()).andExpect(header().string("Content-Language", selected.toLanguageTag()))
                    .andExpect(header().string("Vary", "Accept-Language"))
                    .andExpect(jsonPath("$.message").value(ApiMessages.translate("Case retrieved.", selected)))
                    .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.requestId").value("fixed-request-id"))
                    .andExpect(jsonPath("$.data.filename").value("\u6211\u7684\u7b80\u5386.pdf"))
                    .andExpect(jsonPath("$.data.message").value("Invalid username/email or password."))
                    .andExpect(jsonPath("$.data.reason").value("Access denied."))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.professional.skills[0]").value("\u4e2d\u6587 / français / English"));
        }
        mvc.perform(get("/api/language/probe").header("Accept-Language", "de,fr;q=0.8"))
                .andExpect(jsonPath("$.message").value("Dossier récupéré."));
        mvc.perform(get("/api/language/probe"))
                .andExpect(header().string("Content-Language", "en"))
                .andExpect(jsonPath("$.message").value("Case retrieved."));
    }

    @Test void localizesTypedValidationAndPublicationErrorsWhileKeepingCodesAndFields() throws Exception {
        for (String path : List.of("validation", "publication-error")) {
            mvc.perform(get("/api/language/" + path).header("Accept-Language", "zh-CN"))
                    .andExpect(status().is(path.equals("validation") ? 200 : 409))
                    .andExpect(jsonPath("$.data.errors[0].field").value("requirements"))
                    .andExpect(jsonPath("$.data.errors[0].code").value("MISSING_AREA"))
                    .andExpect(jsonPath("$.data.errors[0].message").value("\u8bf7\u4e3a\u6280\u80fd\u6dfb\u52a0\u7eb3\u5165\u8bc4\u4f30\u7684\u8981\u6c42。"))
                    .andExpect(jsonPath("$.data.warnings[0]").value("\u5019\u9009\u4eba\u54cd\u5e94\u4e0d\u5305\u542b\u9690\u85cf\u8981\u6c42。"));
        }
    }

    @Test void requestLanguageControlsBothMailTemplatesWithoutSendingRealMail() throws Exception {
        for (String locale : List.of("en", "zh-CN", "fr")) {
            reset(sender);
            for (String kind : List.of("registration", "recovery")) {
                mvc.perform(get("/api/language/mail/" + kind).header("Accept-Language", locale))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiresInSeconds").value(600));
            }
            ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(sender, times(2)).send(messages.capture());
            var selected = ApiMessages.resolveLocale(locale);
            assertEquals(ApiMessages.text("mail.registration.subject", selected), messages.getAllValues().get(0).getSubject());
            assertEquals(ApiMessages.text("mail.passwordReset.subject", selected), messages.getAllValues().get(1).getSubject());
            for (SimpleMailMessage message : messages.getAllValues()) {
                assertEquals("mail@example.test", message.getFrom());
                assertArrayEquals(new String[]{"recipient@example.test"}, message.getTo());
                assertTrue(message.getText().contains("012345"));
                assertFalse(message.getText().contains("{code}"));
            }
        }
    }

    @Test void securityErrorsUseRequestedLanguageAndKeepAuthenticationAndRetryHeaders() throws Exception {
        var errors = new AuthenticationErrorHandler(new ObjectMapper());
        var request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "zh-CN");
        var response = new MockHttpServletResponse();
        errors.commence(request, response, new BadCredentialsException("not exposed"));
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertEquals("zh-CN", response.getHeader("Content-Language"));
        assertTrue(response.getContentAsString().contains("\u8bf7\u767b\u5f55，\u6216\u8bbf\u95ee\u4ee4\u724c\u5df2\u5931\u6548。"));
        response = new MockHttpServletResponse();
        errors.commence(request, response, new BadCredentialsException("wrapper", AuthenticationFlowException.rateLimited(7)));
        assertEquals(429, response.getStatus());
        assertEquals("7", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("\u767b\u5f55\u5c1d\u8bd5\u8fc7\u591a，\u8bf7\u7a0d\u540e\u91cd\u8bd5。"));
        request = new MockHttpServletRequest(); request.addHeader("Accept-Language", "fr");
        response = new MockHttpServletResponse();
        errors.handle(request, response, new AccessDeniedException("not exposed"));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Accès refusé."));
    }

    @Test void filterRestoresLocaleOnFailuresAndPreservesExistingVaryHeaders() throws Exception {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        var request = new MockHttpServletRequest(); request.addHeader("Accept-Language", "zh-CN");
        var response = new MockHttpServletResponse(); response.setHeader("Vary", "Origin");
        assertThrows(ServletException.class, () -> new ApiLanguageFilter().doFilter(request, response, (req, res) -> {
            assertEquals(Locale.SIMPLIFIED_CHINESE, ApiMessages.currentLocale());
            ApiLanguageFilter.headers(response, Locale.SIMPLIFIED_CHINESE);
            throw new ServletException("test failure");
        }));
        assertEquals(Locale.FRENCH, ApiMessages.currentLocale());
        assertEquals(List.of("Origin", "Accept-Language"), new ArrayList<>(response.getHeaders("Vary")));
    }

    @Test void concurrentRequestsDoNotShareLanguage() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (String language : List.of("zh-CN", "fr")) futures.add(executor.submit(() -> {
                var request = new MockHttpServletRequest(); request.addHeader("Accept-Language", language);
                var response = new MockHttpServletResponse();
                new ApiLanguageFilter().doFilter(request, response, (req, res) -> {
                    ready.countDown();
                    try { assertTrue(ready.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException exception) { throw new ServletException(exception); }
                    assertEquals(ApiMessages.resolveLocale(language), ApiMessages.currentLocale());
                });
                assertEquals(Locale.ENGLISH, ApiMessages.currentLocale());
                return response.getHeader("Content-Language");
            }));
            assertEquals("zh-CN", futures.get(0).get(10, TimeUnit.SECONDS));
            assertEquals("fr", futures.get(1).get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test void dynamicDraftDiagnosticsAndResultExplanationsFollowTheRequestLanguage() throws Exception {
        for (String language : List.of("en", "zh-CN", "fr")) {
            Locale locale = ApiMessages.resolveLocale(language);
            String missing = "SKILLS: confirm the facts or explicitly confirm their absence.";
            String unsupported = "Resolve unsupported degreeOrder values.";
            mvc.perform(get("/api/language/draft").header("Accept-Language", language))
                    .andExpect(jsonPath("$.data.unresolvedRequiredFields[0]").value(ApiMessages.translate(missing, locale)))
                    .andExpect(jsonPath("$.data.unresolvedRequiredFields[1]").value(ApiMessages.translate(unsupported, locale)))
                    .andExpect(jsonPath("$.data.professional.source").value(missing))
                    .andExpect(jsonPath("$.data.revision").value(7));
            if (!language.equals("en")) {
                assertNotEquals(missing, ApiMessages.translate(missing, locale));
                assertNotEquals(unsupported, ApiMessages.translate(unsupported, locale));
            }
            String strength = "Skills assessed requirements are fully met.";
            mvc.perform(get("/api/language/results").header("Accept-Language", language))
                    .andExpect(jsonPath("$.data.objective.strengths[0]").value(ApiMessages.translate(strength, locale)))
                    .andExpect(jsonPath("$.data.objective.decision").value("Accept"))
                    .andExpect(jsonPath("$.data.objective.displayScore").value(100))
                    .andExpect(jsonPath("$.data.job.title").value(strength))
                    .andExpect(jsonPath("$.data.human.standard.reason").value(strength));
        }
    }

    private Properties properties(String file) throws Exception {
        Properties properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/i18n/" + file)) {
            assertNotNull(stream);
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
    private Set<String> placeholders(String value) {
        Set<String> keys = new HashSet<>();
        var matcher = java.util.regex.Pattern.compile("\\{\\w+\\}").matcher(value);
        while (matcher.find()) keys.add(matcher.group());
        return keys;
    }

    @RestController static class Probe {
        final RegistrationMailSender registration;
        final PasswordResetMail recovery;
        Probe(RegistrationMailSender registration, PasswordResetMail recovery) { this.registration = registration; this.recovery = recovery; }
        @GetMapping("/api/language/probe") ApiResponse<?> probe() {
            return new ApiResponse<>(0, "Case retrieved.", "fixed-request-id", Map.of(
                    "filename", "\u6211\u7684\u7b80\u5386.pdf", "message", "Invalid username/email or password.",
                    "reason", "Access denied.", "status", "PENDING", "professional", Map.of("skills", List.of("\u4e2d\u6587 / français / English"))));
        }
        @GetMapping("/api/language/validation") ApiResponse<?> validation() {
            return ApiResponse.success("Job request completed.", report());
        }
        @GetMapping("/api/language/draft") ApiResponse<?> draft() {
            String missing = "SKILLS: confirm the facts or explicitly confirm their absence.";
            return ApiResponse.success("Case retrieved.", new ReviewDraft("case", 7,
                    new ObjectMapper().createObjectNode().put("source", missing), List.of(),
                    List.of(missing, "Resolve unsupported degreeOrder values.")));
        }
        @GetMapping("/api/language/results") ApiResponse<?> results() {
            String strength = "Skills assessed requirements are fully met.";
            var objective = new ObjectMapper().createObjectNode().put("score", 1).put("displayScore", 100).put("decision", "Accept");
            objective.putArray("strengths").add(strength); objective.putArray("gaps");
            return ApiResponse.success("Case retrieved.", new FiveResults("case", "AUTOMATED_COMPLETE",
                    new PublicJob("job", "version", 1, strength), objective, null, null,
                    Map.of("standard", new HumanResult("Submitted", "Accept", strength)), new Privacy(null, true)));
        }
        @GetMapping("/api/language/publication-error") ResponseEntity<ApiResponse<?>> publicationError() {
            return ResponseEntity.status(409).body(new ApiResponse<>(409, "The draft is not ready for publication.",
                    "fixed-request-id", Map.of("errors", report().errors(), "warnings", report().warnings(), "retryable", false)));
        }
        @GetMapping("/api/language/mail/{kind}") ApiResponse<?> mail(@PathVariable String kind) {
            if (kind.equals("registration")) registration.sendRegistrationCode("recipient@example.test", "012345");
            else recovery.send("recipient@example.test", "012345");
            return ApiResponse.success("Verification code sent.", Map.of("expiresInSeconds", 600));
        }
        private Validation report() {
            return new Validation(false, List.of(new Issue("requirements", "MISSING_AREA", "Add an assessed requirement for SKILLS.")),
                    List.of("Hidden requirements are omitted from Candidate responses."));
        }
    }
}
