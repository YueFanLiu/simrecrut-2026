package fr.isep.simrecrut.job;

import static fr.isep.simrecrut.dto.job.JobDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import fr.isep.simrecrut.common.id.PublicIdGenerator;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.mapper.job.JobMapper;
import fr.isep.simrecrut.security.currentuser.AccountPrincipal;
import fr.isep.simrecrut.service.job.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Exercises workflow boundaries against isolated persistence doubles, never a shared database. */
class JobWorkflowTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final JobMapper db = mock(JobMapper.class);
    final JobRules rules = mock(JobRules.class);
    final JobRequirementExtractor extractor = mock(JobRequirementExtractor.class);
    final Map<Long, JobVersion> rows = new LinkedHashMap<>();
    final Map<Long, List<String>> requirements = new HashMap<>();
    final Map<String, Map<String, Object>> receipts = new HashMap<>();
    final AccountPrincipal actor = new AccountPrincipal(1, "01AAAAAAAAAAAAAAAAAAAAAAAA", UUID.randomUUID().toString(),
            0, Instant.now().plusSeconds(3600));
    JobService jobs;
    long sequence = 0;
    Runnable worker;

    @BeforeEach void setup() {
        PlatformTransactionManager transactions = new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object transaction, TransactionDefinition definition) { }
            protected void doCommit(DefaultTransactionStatus status) { }
            protected void doRollback(DefaultTransactionStatus status) { }
        };
        jobs = new JobService(db, json, new PublicIdGenerator(), rules, extractor, transactions, "RULE_1");
        login("HR", "list", "create", "edit", "publish");
        when(db.lockActor(1)).thenReturn(1L);
        when(db.rule("RULE_1")).thenAnswer(i -> {
            JobVersion row = new JobVersion(); row.matchingRuleVersionId = 1L;
            row.matchingRuleVersion = "RULE_1"; row.ruleStatus = "FROZEN"; return row;
        });
        when(rules.catalogue(any())).thenReturn(json.createObjectNode());
        when(rules.validate(any(), any(), any(), any())).thenReturn(new Validation(true, List.of(), List.of()));
        doAnswer(i -> { JobVersion row = i.getArgument(0); row.jobId = 1L; return null; }).when(db).insertJob(any());
        doAnswer(i -> {
            JobVersion row = i.getArgument(0); row.id = ++sequence;
            rows.put(row.id, copy(row)); requirements.put(row.id, new ArrayList<>()); return null;
        }).when(db).insertVersion(any());
        when(db.lockJob(anyString(), anyLong())).thenAnswer(i -> rows.values().stream()
                .filter(r -> r.jobPublicId.equals(i.getArgument(0)) && r.ownerUserId.equals(i.getArgument(1)))
                .map(r -> r.jobId).findFirst().orElse(null));
        when(db.draft(anyString())).thenAnswer(i -> rows.values().stream()
                .filter(r -> r.jobPublicId.equals(i.getArgument(0)) && r.status.equals("DRAFT"))
                .findFirst().map(this::copy).orElse(null));
        when(db.version(anyString(), anyString())).thenAnswer(i -> rows.values().stream()
                .filter(r -> r.jobPublicId.equals(i.getArgument(0)) && r.publicId.equals(i.getArgument(1)))
                .findFirst().map(this::copy).orElse(null));
        when(db.published(anyString())).thenAnswer(i -> rows.values().stream()
                .filter(r -> r.jobPublicId.equals(i.getArgument(0)) && r.status.equals("PUBLISHED"))
                .max(Comparator.comparing(r -> r.versionNo)).map(this::copy).orElse(null));
        when(db.nextVersion(anyLong())).thenAnswer(i -> rows.size() + 1);
        when(db.updateDraft(any())).thenAnswer(i -> {
            JobVersion r = i.getArgument(0), old = rows.get(r.id);
            if (!old.status.equals("DRAFT") || !old.revisionNo.equals(r.revisionNo)) return 0;
            JobVersion saved = copy(r); saved.revisionNo++; rows.put(r.id, saved); return 1;
        });
        when(db.requirements(anyLong())).thenAnswer(i -> requirements.get(i.getArgument(0)));
        doAnswer(i -> { requirements.put(i.getArgument(0), new ArrayList<>()); return null; })
                .when(db).deleteRequirements(anyLong());
        doAnswer(i -> {
            Requirement r = new Requirement(Criterion.valueOf(i.getArgument(1)), i.getArgument(2),
                    json.readTree((String) i.getArgument(3)), i.getArgument(6), i.getArgument(4), i.getArgument(5),
                    i.getArgument(7), i.getArgument(8), i.getArgument(9),
                    json.readValue((String) i.getArgument(10), json.getTypeFactory().constructCollectionType(List.class, String.class)));
            requirements.get((Long) i.getArgument(0)).add(json.writeValueAsString(r)); return null;
        }).when(db).insertRequirement(anyLong(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), nullable(String.class), nullable(String.class), anyString(), anyInt());
        when(db.receipt(anyLong(), anyString())).thenAnswer(i -> receipts.get(i.getArgument(1)));
        doAnswer(i -> {
            receipts.put(i.getArgument(1), Map.of("requestHash", i.getArgument(2), "jobPublicId", i.getArgument(3),
                    "responseJson", i.getArgument(4))); return null;
        }).when(db).insertReceipt(anyLong(), anyString(), anyString(), anyString(), anyString());
        doAnswer(i -> { worker = i.getArgument(0); return null; }).when(extractor).submit(any());
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    void login(String role, String... permissions) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String permission : permissions) authorities.add(new SimpleGrantedAuthority("simrecrut:job:" + permission));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, "", authorities));
    }

    JobVersion copy(JobVersion r) { return json.convertValue(r, JobVersion.class); }
    String key() { return UUID.randomUUID().toString(); }
    JsonNode create() { return jobs.create(actor, key(), new CreateRequest("Original title", "Java required")); }

    @Test void repeatCreateReplaysAndChangedRequestConflicts() {
        String key = key();
        CreateRequest request = new CreateRequest("Java", "Java required");
        JsonNode first = jobs.create(actor, key, request);
        assertEquals(first.toString(), jobs.create(actor, key, request).toString());
        assertEquals(1, rows.size());
        assertEquals(409, assertThrows(JobException.class, () -> jobs.create(actor, key,
                new CreateRequest("Changed", "Java required"))).status());
    }

    @Test void staleEditsAndForeignOwnershipFail() {
        JsonNode created = create(); String id = created.path("jobId").asText();
        jobs.update(actor, id, key(), new UpdateRequest("New", "Java required", 1L));
        assertEquals(409, assertThrows(JobException.class, () -> jobs.update(actor, id, key(),
                new UpdateRequest("Old", "Java required", 1L))).status());
        AccountPrincipal foreign = new AccountPrincipal(2, actor.publicId(), actor.sessionId(), 0, actor.expiresAt());
        assertEquals(404, assertThrows(JobException.class, () -> jobs.detail(foreign, id, false)).status());
    }

    @Test void publicationIsImmutableAndCandidateProjectionOmitsPrivateRequirements() throws Exception {
        JsonNode created = create(); String id = created.path("jobId").asText();
        Requirement hidden = new Requirement(Criterion.SKILLS, "HIDDEN", json.readTree("{\"assessedSkillCodes\":[\"JAVA\"]}"),
                true, true, false, true, "Java", "Description", List.of());
        jobs.saveRequirements(actor, id, key(), new RequirementsRequest(1L, List.of(hidden)));
        JsonNode published = jobs.publish(actor, id, key(), new RevisionRequest(2L));
        assertEquals(409, assertThrows(JobException.class, () -> jobs.update(actor, id, key(),
                new UpdateRequest("Illegal", "Java required", 3L))).status());
        JsonNode draft = jobs.cloneDraft(actor, id, key(), new CloneRequest(published.path("jobVersionId").asText()));
        jobs.update(actor, id, key(), new UpdateRequest("Unpublished title", "Different description", draft.path("revision").asLong()));
        assertEquals(409, assertThrows(JobException.class, () -> jobs.cloneDraft(actor, id, key(),
                new CloneRequest(published.path("jobVersionId").asText()))).status());
        login("CANDIDATE", "browse");
        JsonNode visible = jobs.detail(actor, id, true);
        assertEquals("Original title", visible.path("title").asText());
        assertTrue(visible.path("requirements").isEmpty());
        for (String field : List.of("weights", "matchingRuleVersion", "ownerUserId", "configPath", "revision")) {
            assertFalse(visible.has(field), field);
        }
    }

    @Test void publicationRevalidatesAndKeepsInvalidDraftEditable() {
        JsonNode created = create(); String id = created.path("jobId").asText();
        when(rules.validate(any(), any(), any(), any())).thenReturn(new Validation(false,
                List.of(new Issue("weights", "INVALID_TOTAL", "Total must be one.")), List.of()));
        assertEquals(409, assertThrows(JobException.class, () -> jobs.publish(actor, id, key(), new RevisionRequest(1L))).status());
        assertEquals("DRAFT", rows.values().iterator().next().status);
    }

    @Test void weightsAreNotSilentlyNormalized() {
        JsonNode created = create(); String id = created.path("jobId").asText();
        assertThrows(IllegalArgumentException.class, () -> jobs.saveWeights(actor, id, key(), new WeightsRequest(1L,
                List.of(Criterion.SKILLS), new Weights(new BigDecimal("0.8"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))));
    }

    @Test void extractionIsQueuedAfterCommitAndOldWorkerCannotOverwriteNewLease() throws Exception {
        JsonNode created = create(); String id = created.path("jobId").asText();
        when(extractor.extract(anyString(), any())).thenReturn(List.of());
        JsonNode accepted = jobs.extract(actor, id, key());
        assertEquals("RUNNING", accepted.path("status").asText());
        assertNotNull(worker);
        JobVersion stored = rows.values().iterator().next();
        stored.extractionToken = UUID.randomUUID().toString();
        worker.run();
        assertEquals("RUNNING", stored.extractionStatus);
        assertEquals(2L, rows.values().iterator().next().revisionNo);
    }

    @Test void extractionFailureIsVisibleAndDraftCanBeRetried() throws Exception {
        JsonNode created = create(); String id = created.path("jobId").asText();
        when(extractor.extract(anyString(), any())).thenThrow(new IllegalStateException("Private provider error"));
        jobs.extract(actor, id, key()); worker.run();
        JsonNode status = jobs.extractionStatus(actor, id);
        assertEquals("FAILED_RETRYABLE", status.path("status").asText());
        assertFalse(status.toString().contains("Private provider error"));
        jobs.extract(actor, id, key());
        assertEquals("RUNNING", rows.values().iterator().next().extractionStatus);
    }

    @Test void roleAndActionPermissionAreBothRequired() {
        login("HR", "list");
        assertThrows(AccessDeniedException.class, this::create);
        login("ADMIN", "create");
        assertThrows(AccessDeniedException.class, this::create);
        verify(db, never()).insertJob(any());
    }
}
