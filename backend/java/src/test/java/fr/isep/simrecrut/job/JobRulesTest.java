package fr.isep.simrecrut.job;

import static fr.isep.simrecrut.dto.job.JobDtos.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import fr.isep.simrecrut.entity.job.JobVersion;
import fr.isep.simrecrut.service.job.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class JobRulesTest {
    @TempDir Path root;
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    JobRules rules;
    JobVersion version;

    static final String CATALOGUE = """
        {"catalogs":{"skillCodes":["JAVA"],"roleFamilyCodes":["SOFTWARE"],
        "degreeLevelCodes":["BACHELOR"],"subjectCodes":["COMPUTER_SCIENCE"],
        "languageCodes":["EN"],"languageLevelCodes":["B2"],"conditionCodes":["API"]}}
        """;

    @BeforeEach void setup() throws Exception {
        rules = new JobRules(json, root.toString());
        Files.writeString(root.resolve("rule.json"), CATALOGUE);
        version = new JobVersion();
        version.ruleStatus = "FROZEN";
        version.configPath = "rule.json";
        version.configSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(CATALOGUE.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    Requirement skill(String code, boolean reviewed) throws Exception {
        return new Requirement(Criterion.SKILLS, "REQ1", json.readTree("{\"assessedSkillCodes\":[\"" + code + "\"]}"),
                true, false, true, reviewed, "Java", "Description", List.of());
    }

    @Test void reviewedTypedRequirementCanPublish() throws Exception {
        assertTrue(rules.validate(version, List.of(skill("JAVA", true)), json.readTree("[\"SKILLS\"]"),
                new Weights(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).valid());
    }

    @Test void unknownCodesAndUnreviewedProposalsCannotPublish() throws Exception {
        var report = rules.validate(version, List.of(skill("UNKNOWN", false)), json.readTree("[\"SKILLS\"]"),
                new Weights(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(e -> e.code().equals("UNRESOLVED_PAYLOAD")));
        assertTrue(report.errors().stream().anyMatch(e -> e.code().equals("REVIEW_REQUIRED")));
    }

    @Test void inactiveWeightsAndInexactTotalFail() throws Exception {
        var report = rules.validate(version, List.of(skill("JAVA", true)), json.readTree("[\"SKILLS\"]"),
                new Weights(new BigDecimal("0.7"), new BigDecimal("0.2"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        assertTrue(report.errors().stream().anyMatch(e -> e.code().equals("INVALID_TOTAL")));
        assertTrue(report.errors().stream().anyMatch(e -> e.code().equals("INACTIVE_WEIGHT")));
    }

    @Test void checksumMismatchAndPathEscapeFailClosed() {
        version.configSha256 = "0".repeat(64);
        assertEquals(503, assertThrows(JobException.class, () -> rules.catalogue(version)).status());
        version.configPath = "../outside.json";
        assertThrows(JobException.class, () -> rules.catalogue(version));
    }

    @Test void unknownPayloadAndUnknownRequestFieldsAreRejected() throws Exception {
        var row = new Requirement(Criterion.SKILLS, "REQ1", json.readTree("{\"ownerUserId\":42}"),
                true, false, true, false, null, null, List.of());
        assertThrows(IllegalArgumentException.class, () -> rules.checkShape(row));
        assertThrows(Exception.class, () -> json.readValue(
                "{\"title\":\"Java\",\"description\":\"Java\",\"ownerUserId\":42}", CreateRequest.class));
    }
}
