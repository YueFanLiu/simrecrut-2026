package fr.isep.simrecrut.assessment;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.isep.simrecrut.mapper.assessment.CandidateCaseMapper.Protocol;
import fr.isep.simrecrut.service.assessment.*;
import fr.isep.simrecrut.workspace.Facts;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CaseMatchingEngineTest {
    @TempDir Path temporary;
    CaseTestSupport f;
    @BeforeEach void setup() throws Exception { f = new CaseTestSupport(temporary); }
    @AfterEach void close() { f.worker.stop(); }

    @Test void skillsAreDeduplicatedAndMandatoryGatesAreIndependentOfCutoff() throws Exception {
        ObjectNode draft = Facts.empty(); draft.putArray("confirmedAbsent");
        draft.withObject("professional").withArray("skills").addObject().put("skillCode", "Java");
        draft.withObject("professional").withArray("skills").addObject().put("skillCode", "Java");
        JsonNode requirements = f.json.readTree("""
            [{"criterionType":"SKILLS","assessmentIncluded":true,"mandatory":true,
            "payload":{"assessedSkillCodes":["Java","Python"]}}]
            """);
        Protocol protocol = new Protocol(1, 1, 1, "P", new BigDecimal("0.4"), "S", "s", "h", 32);
        var result = f.engine.calculate(draft, f.job, requirements,
                new CaseConfiguration.Bundle(f.bundle.rule(), f.bundle.schema(), protocol), "2026-10");
        assertEquals(0.5, result.path("score").asDouble()); assertEquals("Reject", result.path("decision").asText());
        assertEquals(0, result.at("/professional/g").asInt());
    }

    @Test void explicitAbsenceIsZeroButUnknownIsUnresolved() throws Exception {
        ObjectNode draft = Facts.empty(); draft.putArray("confirmedAbsent");
        JsonNode requirements = requirements("SKILLS", "{\"assessedSkillCodes\":[\"Java\"]}");
        assertEquals(422, assertThrows(CaseException.class, () -> f.engine.calculate(draft, f.job, requirements, f.bundle, "2026-10")).status());
        draft.withArray("confirmedAbsent").add("SKILLS");
        assertEquals(0, f.engine.calculate(draft, f.job, requirements, f.bundle, "2026-10").path("score").asInt());
    }

    @Test void overlappingExperienceMonthsAreMergedAndReferenceMonthIsFixed() throws Exception {
        activate("EXPERIENCE"); ObjectNode draft = Facts.empty(); draft.putArray("confirmedAbsent");
        ArrayNode experiences = draft.withObject("professional").withArray("experience");
        experiences.addObject().put("roleFamilyCode", "SOFTWARE_ENGINEERING").put("startMonth", "2020-01").put("endMonth", "2021-01");
        experiences.addObject().put("roleFamilyCode", "SOFTWARE_ENGINEERING").put("startMonth", "2020-06").put("endMonth", "2021-06");
        var result = f.engine.calculate(draft, f.job, requirements("EXPERIENCE",
                "{\"targetYears\":2,\"relevantRoleFamilyCodes\":[\"SOFTWARE_ENGINEERING\"]}"), f.bundle, "2026-10");
        assertEquals(0.7083334, result.at("/professional/m/1").asDouble());
    }

    @Test void ambiguousExperienceCannotBeDoubleCounted() throws Exception {
        activate("EXPERIENCE"); ObjectNode draft = Facts.empty(); draft.putArray("confirmedAbsent");
        draft.withObject("professional").withArray("experience").addObject().put("roleFamilyCode", "SOFTWARE_ENGINEERING")
                .put("startMonth", "2020-01").put("endMonth", "2021-01").put("reviewedRelevantYears", 1);
        assertThrows(CaseException.class, () -> f.engine.calculate(draft, f.job, requirements("EXPERIENCE",
                "{\"targetYears\":2,\"relevantRoleFamilyCodes\":[\"SOFTWARE_ENGINEERING\"]}"), f.bundle, "2026-10"));
    }

    @Test void educationAndLanguageUseExplicitFrozenOrderings() throws Exception {
        activate("EDUCATION"); ObjectNode draft = Facts.empty(); draft.putArray("confirmedAbsent");
        draft.withObject("professional").withArray("education").addObject().put("degreeLevelCode", "MASTER").put("subjectCode", "COMPUTER_SCIENCE");
        assertEquals(1, f.engine.calculate(draft, f.job, requirements("EDUCATION",
                "{\"minimumDegreeLevelCode\":\"BACHELOR\",\"acceptedSubjectCodes\":[\"COMPUTER_SCIENCE\"]}"), f.bundle, "2026-10").path("score").asInt());
        activate("LANGUAGES"); draft.withObject("professional").withArray("languages").addObject().put("languageCode", "EN").put("levelCode", "B1");
        assertEquals(0, f.engine.calculate(draft, f.job, requirements("LANGUAGES",
                "{\"requiredLanguages\":[{\"languageCode\":\"EN\",\"minimumLevelCode\":\"C1\"}]}"), f.bundle, "2026-10").path("score").asInt());
    }

    @Test void projectEvidenceCountsEachConditionOnce() throws Exception {
        activate("PROJECTS"); ObjectNode draft = Facts.empty(); draft.putArray("confirmedAbsent");
        for (int i = 0; i < 2; i++) draft.withObject("professional").withArray("projects").addObject()
                .put("projectId", "P" + i).putArray("supportedConditionCodes").add("API_DEVELOPMENT");
        assertEquals(1, f.engine.calculate(draft, f.job, requirements("PROJECTS",
                "{\"assessedConditionCodes\":[\"API_DEVELOPMENT\"]}"), f.bundle, "2026-10").path("score").asInt());
    }

    @Test void unrestrictedEducationDoesNotRequireAResolvedSubject() throws Exception {
        activate("EDUCATION"); ObjectNode draft = Facts.empty(); draft.putArray("confirmedAbsent");
        draft.withObject("professional").withArray("education").addObject()
                .put("degreeLevelCode", "MASTER").put("subjectCode", "UNKNOWN");
        assertEquals(1, f.engine.calculate(draft, f.job, requirements("EDUCATION",
                "{\"minimumDegreeLevelCode\":\"BACHELOR\",\"unrestrictedSubject\":true}"), f.bundle, "2026-10").path("score").asInt());
    }

    @Test void invalidWeightsOrMissingOrderConfigurationAreNotReplacedWithDefaults() throws Exception {
        f.job.weightSkills = new BigDecimal("0.9");
        ObjectNode draft = Facts.empty(); draft.putArray("confirmedAbsent").add("SKILLS");
        assertEquals(503, assertThrows(CaseException.class, () -> f.engine.calculate(draft, f.job,
                requirements("SKILLS", "{\"assessedSkillCodes\":[\"Java\"]}"), f.bundle, "2026-10")).status());
    }

    private JsonNode requirements(String type, String payload) throws Exception {
        return f.json.readTree("[{\"criterionType\":\"" + type + "\",\"assessmentIncluded\":true,\"mandatory\":false,\"payload\":" + payload + "}]");
    }
    private void activate(String type) {
        f.job.activeCriteriaJson = "[\"" + type + "\"]";
        f.job.weightSkills = f.job.weightExperience = f.job.weightEducation = f.job.weightLanguages = f.job.weightProjects = BigDecimal.ZERO;
        switch (type) {
            case "EXPERIENCE" -> f.job.weightExperience = BigDecimal.ONE;
            case "EDUCATION" -> f.job.weightEducation = BigDecimal.ONE;
            case "LANGUAGES" -> f.job.weightLanguages = BigDecimal.ONE;
            case "PROJECTS" -> f.job.weightProjects = BigDecimal.ONE;
            default -> f.job.weightSkills = BigDecimal.ONE;
        }
    }
}
