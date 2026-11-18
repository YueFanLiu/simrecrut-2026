"""Protect source boundaries and the temporary Objective Rule labels."""

import copy
from decimal import Decimal

import pytest

from simrecrut_ml.features.methods.objective_rule import (
    activity_evidence,
    assess_pair,
    experience_evidence,
    feature_rows,
    language_evidence,
    literal_reference,
    map_job_requirements,
    requirement_evidence,
    skill_evidence,
    source_digest,
)
from simrecrut_ml.features.methods.pilot_training import build_development_pairs


def field(value, key="Moreinfo", quote=None):
    return {
        "value": value,
        "status": "OBSERVED" if value is not None else "UNKNOWN",
        "evidence": (
            [{"source_field": key, "excerpt": quote or value}] if value is not None else []
        ),
    }


def profile(text, english="intermediate"):
    sources = {"Moreinfo": text, "English Level": english}
    return {
        "source_record_id": "synthetic-profile",
        "status": "VALIDATED",
        "source_fields": sources,
        "redaction": {"fields": sources, "spans": {key: [] for key in sources}},
        "facts": {
            "professional": {
                area: [] for area in ("skills", "experience", "education", "languages", "projects")
            },
            "field_status": {
                area: "UNKNOWN"
                for area in ("skills", "experience", "education", "languages", "projects")
            },
        },
    }


def rule(codes, mandatory=False):
    return {
        "skills": [{"code": code, "any_of": [code], "mandatory": mandatory} for code in codes],
        "experience": [],
        "education": [],
        "projects": [],
        "language": None,
        "unresolved": [],
    }


def experience(role, period=None, duration=None, key="Moreinfo"):
    return {"roleTitle": field(role, key), "period": field(period), "duration": field(duration)}


def test_open_frontend_work_cannot_be_discarded_by_a_global_title():
    row = profile(
        "May 2019 - Till date\nWorked on React projects.\n"
        "October 2016 - February 2018\nFront End Developer"
    )
    row["source_fields"]["Position"] = "Frontend Developer"
    row["facts"]["professional"]["experience"] = [
        experience("Frontend Developer", "May 2019 - Till date", key="Position"),
        experience("Front End Developer", "October 2016 - February 2018"),
    ]
    result = experience_evidence(row, "frontend", Decimal(48))
    assert result["status"] == "UNKNOWN" and result["upper_months"] == "None"
    assert result["lower_months"] == "16"
    assert experience_evidence(row, "software", Decimal(36))["status"] == "UNKNOWN"


def test_technical_restoration_in_a_single_code_clause_is_complete_and_idempotent():
    from simrecrut_ml.features.methods.objective_rule import _separate_skill_requirements

    conditions = [
        {
            "code": "stack",
            "any_of": ["css"],
            "mandatory": False,
            "evidence": [
                {"source_field": "Long Description", "excerpt": "CSS, TailwindCSS, Alpine.js"}
            ],
        }
    ]
    fixed = _separate_skill_requirements(conditions)
    assert {code for item in fixed for code in item["any_of"]} == {"css", "tailwind", "alpine"}
    assert _separate_skill_requirements(copy.deepcopy(fixed)) == fixed


def test_dated_frontend_duties_can_support_a_generic_software_title():
    row = profile(
        "Software Developer\nJan 2017 - Jan 2020\nTechnologies: React, TypeScript.\n"
        "Frontend Developer\nJan 2015 - Jan 2017\nDeveloped web pages."
    )
    row["facts"]["professional"]["experience"] = [
        experience("Software Developer", "Jan 2017 - Jan 2020"),
        experience("Frontend Developer", "Jan 2015 - Jan 2017"),
    ]
    result = experience_evidence(row, "frontend", Decimal(48))
    assert result["minimum_pass"] is True and result["lower_months"] == "60"


def test_frontend_scope_does_not_borrow_a_later_skills_section():
    row = profile(
        "Software Developer\nJan 2017 - Jan 2020\nMaintained data pipelines.\n"
        "Skills:\nReact, TypeScript"
    )
    row["facts"]["professional"]["experience"] = [
        experience("Software Developer", "Jan 2017 - Jan 2020"),
    ]
    assert experience_evidence(row, "frontend", Decimal(24))["status"] == "UNKNOWN"


def test_boundary_weights_keep_qualifications_and_reject_unattainable_scores():
    from simrecrut_ml.features.methods.pilot_training import _boundary_weights

    matches = [Decimal(".2"), Decimal(1), Decimal(0), Decimal(1), Decimal(0)]
    weights = _boundary_weights(matches, [0, 1, 3], Decimal(".705"))
    assert sum(weights) == 1 and weights[2] == weights[4] == 0
    assert abs(sum(a * b for a, b in zip(matches, weights)) - Decimal(".705")) < Decimal(".000001")
    assert _boundary_weights([Decimal(1)] * 5, [0, 1], Decimal(".7")) is None


def test_missing_skill_area_is_held_instead_of_zero():
    result = assess_pair(profile("A brief professional summary."), rule(["wpf"]))
    assert result["status"] == "HELD"
    assert "SKILL_SOURCE_UNKNOWN" in result["issues"]


def test_position_alone_does_not_prove_skills_or_scoped_years():
    row = profile("Three years in retail. 3 years of experience in retail.")
    row["source_fields"]["Position"] = "Java Developer"
    row["facts"]["professional"]["experience"] = [
        {
            "roleTitle": field("Java Developer", "Position"),
            "period": field(None),
            "duration": field("3 years", quote="3 years of experience in retail"),
        },
    ]
    assert skill_evidence(row, "java") == []
    assert experience_evidence(row, "java", Decimal(24))["status"] == "UNKNOWN"


@pytest.mark.parametrize(
    "code,claim,unrelated",
    [
        ("csharp", "Skills: C #", "Skills: CSS"),
        ("async", "Skills: asyncio", "Skills: synchronous programming"),
        ("cypress", "Skills: Cypress", "Skills: Cyprus"),
        ("sqlalchemy", "Skills: SQLAlchemy", "Skills: SQL Server"),
        ("dotnet_core", "Skills: .NET Core", "Skills: .NET framework"),
        ("robot_framework", "Skills: Robot Framework", "Skills: robotic systems"),
    ],
)
def test_curated_job_aliases_preserve_technical_distinctions(code, claim, unrelated):
    assert skill_evidence(profile(claim), code)
    assert skill_evidence(profile(unrelated), code) == []


@pytest.mark.parametrize("role,scope", [("React Developer", "frontend"), ("AQA", "automation")])
def test_role_aliases_still_require_a_reported_duration(role, scope):
    row = profile(f"I have 3 years of experience as a {role}.")
    assert experience_evidence(row, scope, Decimal(24))["minimum_pass"] is True
    assert experience_evidence(profile(role), scope, Decimal(24))["status"] == "UNKNOWN"


@pytest.mark.parametrize(
    "scope,broader,specific",
    [
        ("manual_qa", "QA Engineer", "Manual QA Engineer"),
        ("csharp", ".NET Developer", "C# Developer"),
        ("dotnet_web", ".NET Developer", "ASP.NET Developer"),
    ],
)
def test_job_specific_years_do_not_follow_from_a_broader_role(scope, broader, specific):
    assert (
        experience_evidence(
            profile(f"I have 3 years of experience as a {broader}."), scope, Decimal(24)
        )["status"]
        == "UNKNOWN"
    )
    assert (
        experience_evidence(
            profile(f"I have 3 years of experience as a {specific}."), scope, Decimal(24)
        )["minimum_pass"]
        is True
    )


def test_required_robot_web_experience_needs_a_work_claim():
    assert (
        activity_evidence(profile("Skills: Robot Framework, web testing."), "robot_web_testing")[
            "status"
        ]
        == "UNKNOWN"
    )
    assert (
        activity_evidence(
            profile("I used Robot Framework for web application testing."), "robot_web_testing"
        )["pass"]
        is True
    )


def test_explicit_technical_list_recovers_omissions_without_java_javascript_confusion():
    row = profile("I am proficient with JavaScript. Tools & Technologies:\nSelenium, PostgreSQL")
    assert skill_evidence(row, "javascript")
    assert skill_evidence(row, "selenium")
    assert not skill_evidence(row, "java")


def test_overlapping_month_ranges_are_counted_once():
    row = profile("QA Engineer 2019-01 to 2021-01. QA Engineer 2020-01 to 2022-01.")
    row["facts"]["professional"]["experience"] = [
        experience("QA Engineer", "2019-01 to 2021-01"),
        experience("QA Engineer", "2020-01 to 2022-01"),
    ]
    result = experience_evidence(row, "qa", Decimal(48))
    assert result["status"] == "OBSERVED"
    assert result["match"] == Decimal("0.75")
    assert result["minimum_pass"] is False


def test_open_period_does_not_use_the_current_date():
    row = profile("QA Engineer, 2020-01 to Present")
    row["facts"]["professional"]["experience"] = [experience("QA Engineer", "2020-01 to Present")]
    assert experience_evidence(row, "qa", Decimal(12))["status"] == "UNKNOWN"


def test_approximate_career_duration_is_not_an_exact_number():
    row = profile("I am a Python developer with nearly 8 years of experience.")
    assert experience_evidence(row, "python", Decimal(24))["status"] == "UNKNOWN"


def test_academy_project_duration_is_not_professional_employment():
    row = profile(
        "Project Description:\nCustomer: Example Academy\n"
        "Project Role: Automation Tester\n3 months"
    )
    row["facts"]["professional"]["experience"] = [
        experience("Automation Tester", duration="3 months")
    ]
    assert experience_evidence(row, "automation", Decimal(12))["status"] == "UNKNOWN"


def test_closed_history_can_establish_a_floor_despite_an_open_overlapping_role():
    row = profile("QA Engineer July 2019 to September 2021. QA Engineer November 2020 to present")
    row["facts"]["professional"]["experience"] = [
        experience("QA Engineer", "July 2019 to September 2021"),
        experience("QA Engineer", "November 2020 to present"),
    ]
    result = experience_evidence(row, "qa", Decimal(24))
    assert result["status"] == "OBSERVED" and result["match"] == 1
    assert result["lower_months"] == "26"


def test_conflicting_english_claims_are_not_resolved_by_picking_the_higher_level():
    row = profile("My English is fluent.", "intermediate")
    assert language_evidence(row)["status"] == "CONFLICT"


def test_backslash_dates_keep_the_original_quote_and_bound_the_related_role():
    text = r"Junior .NET Developer 11\2022 - 04\2023"
    row = profile(text)
    row["facts"]["professional"]["experience"] = [
        experience("Junior .NET Developer", r"11\2022 - 04\2023")
    ]
    result = experience_evidence(row, "dotnet", Decimal(24))
    assert result["status"] == "OBSERVED" and result["match"] == Decimal(5) / 24
    assert result["lower_months"] == result["upper_months"] == "5"
    assert any(ref["excerpt"] == r"11\2022 - 04\2023" for ref in result["evidence"])
    assert row["source_fields"]["Moreinfo"] == text


@pytest.mark.parametrize("duration, months", [("a year", 12), ("One year", 12), ("6-months", 6)])
def test_literal_duration_variants_preserve_evidence_and_role_scope(duration, months):
    text = f"QA Engineer with {duration} of professional experience."
    row = profile(text)
    row["facts"]["professional"]["experience"] = [experience("QA Engineer", duration=duration)]
    result = experience_evidence(row, "qa", Decimal(24))
    assert result["status"] == "OBSERVED" and result["match"] == Decimal(months) / 24
    assert result["lower_months"] == result["upper_months"] == str(months)
    assert any(ref["excerpt"] == duration for ref in result["evidence"])
    assert experience_evidence(row, "automation", Decimal(24))["status"] == "UNKNOWN"


@pytest.mark.parametrize(
    "duration", ["about a year", "within a year", "nearly One year", "1-2 years"]
)
def test_uncertain_duration_variants_do_not_become_exact_values(duration):
    row = profile(f"QA Engineer with {duration} of professional experience.")
    row["facts"]["professional"]["experience"] = [experience("QA Engineer", duration=duration)]
    assert experience_evidence(row, "qa", Decimal(24))["status"] == "UNKNOWN"


def language_rule(target):
    assessed = rule([])
    assessed["language"] = {"code": "english", "level": target, "mandatory": True}
    return assessed


def test_conflicting_levels_can_prove_the_job_minimum_without_selecting_a_level():
    row = profile("My English is upper intermediate.", "intermediate")
    original = copy.deepcopy(row)
    result = assess_pair(row, language_rule(2))
    proof = result["evidence"]["languages"]
    assert result["status"] == "READY" and result["mandatory_pass"] == 1
    assert proof["status"] == "CONFLICT" and proof["levels"] == [2, 3]
    assert "level" not in proof and proof["minimum_pass"] is True
    assert proof["comparison_status"] == "OBSERVED" and len(proof["evidence"]) >= 2
    assert row == original


def test_conflicting_levels_that_straddle_the_job_target_still_block():
    row = profile("My English is upper intermediate.", "intermediate")
    result = assess_pair(row, language_rule(3))
    assert result["status"] == "HELD" and "ENGLISH_CONFLICT" in result["issues"]
    assert result["evidence"]["languages"]["minimum_pass"] is None


def test_conflicting_levels_can_prove_a_failure_when_both_are_below_target():
    row = profile("My English is pre-intermediate.", "intermediate")
    result = assess_pair(row, language_rule(3))
    assert result["status"] == "READY" and result["mandatory_pass"] == 0
    assert result["matches"][3] == "0E-7"
    assert result["evidence"]["languages"]["status"] == "CONFLICT"
    assert result["evidence"]["languages"]["minimum_pass"] is False


def test_missing_language_level_cannot_supply_a_job_comparison():
    row = profile("An English-language professional summary.", english=None)
    result = assess_pair(row, language_rule(2))
    assert result["status"] == "HELD" and "ENGLISH_UNKNOWN" in result["issues"]


def test_unmapped_language_report_is_not_dropped_to_obtain_a_certain_comparison():
    row = profile("My English is fluent.", english="not assessed")
    result = assess_pair(row, language_rule(2))
    proof = result["evidence"]["languages"]
    assert result["status"] == "HELD" and proof["minimum_pass"] is None
    assert proof["unmapped_evidence"][0]["excerpt"] == "not assessed"


def test_unsupported_mandatory_skill_stays_unknown():
    result = assess_pair(profile("I am proficient with Java."), rule(["csharp"], mandatory=True))
    assert result["status"] == "HELD" and "MANDATORY_CHECK_UNKNOWN" in result["issues"]


def test_referral_changes_only_the_biased_target_and_never_overrides_the_gate():
    row = profile("I am proficient with Java and SQL.")
    result = assess_pair(row, rule(["java", "sql", "python"]))
    assert result["status"] == "READY" and result["score"] == "0.6666667"
    research = {
        "school": ["prestigious", "ordinary", "unknown"],
        "referral": ["yes", "no", "unknown"],
        "gender": ["woman", "man", "unknown"],
        "ethnicity": [
            "east_asian",
            "south_asian",
            "black",
            "white",
            "middle_eastern_or_north_african",
            "mixed_or_other",
            "unknown",
        ],
    }
    schema = {
        "professional_order": [
            f"{prefix}_{area}"
            for prefix in ("match", "weight", "contribution")
            for area in ("skills", "experience", "education", "languages", "projects")
        ]
        + ["mandatory_pass"],
        "research_order": research,
    }
    pair = {
        "base_profile_id": "synthetic-profile",
        "job_id": "synthetic-job",
        "split": "train",
        "job_partition": "development",
        "job_rule_sha256": "0" * 64,
    }
    rows = feature_rows(row, pair, result, schema, Decimal("0.70"), Decimal("0.08"))
    assert [(r["Y_N"], r["Y_B"]) for r in rows] == [(0, 0), (0, 1)]
    assert all(len(r["features_39"]) == 39 for r in rows)
    assert rows[0]["features_39"][:16] == rows[1]["features_39"][:16]
    failed = copy.deepcopy(result)
    failed.update(
        matches=["1", "1", "0", "1", "0"],
        weights=["0.25", "0.25", "0.25", "0.25", "0"],
        contributions=["0.25", "0.25", "0", "0.25", "0"],
        score="0.75",
        mandatory_pass=0,
    )
    rows = feature_rows(row, pair, failed, schema, Decimal("0.70"), Decimal("0.08"))
    assert all(r["Y_N"] == r["Y_B"] == 0 for r in rows)


def test_year_only_history_can_prove_a_floor_without_inventing_months():
    row = profile(".NET Developer 2012 - 2015. .NET Developer 2015 - 2017.")
    row["facts"]["professional"]["experience"] = [
        experience(".NET Developer", "2012 - 2015"),
        experience(".NET Developer", "2015 - 2017"),
    ]
    result = experience_evidence(row, "dotnet", Decimal(36))
    assert result["status"] == "OBSERVED" and result["match"] == 1
    assert result["lower_months"] == "36"


def test_uncertain_year_range_is_not_rounded_to_an_exact_duration():
    row = profile("QA Engineer 2012 - 2015")
    row["facts"]["professional"]["experience"] = [
        experience("QA Engineer", "2012 - 2015"),
        experience("QA Engineer", "2012 - 2015"),
    ]
    result = experience_evidence(row, "qa", Decimal(36))
    assert result["status"] == "UNKNOWN"
    assert (result["lower_months"], result["upper_months"]) == ("24", "48")


def test_year_and_month_ranges_are_merged_before_calculating_a_floor():
    row = profile("QA Engineer 2012 - 2015. QA Engineer 2014-01 to 2016-01.")
    row["facts"]["professional"]["experience"] = [
        experience("QA Engineer", "2012 - 2015"),
        experience("QA Engineer", "2014-01 to 2016-01"),
    ]
    result = experience_evidence(row, "qa", Decimal(48))
    assert result["status"] == "UNKNOWN"
    assert (result["lower_months"], result["upper_months"]) == ("36", "48")


def test_explicit_no_experience_is_scoped_and_does_not_prove_a_skill():
    row = profile("Before I, unfortunately, didn't work as Front-end developer.")
    result = experience_evidence(row, "frontend", Decimal(12))
    assert result["status"] == "OBSERVED" and result["match"] == 0
    assert result["minimum_pass"] is False and result["evidence"]
    assert experience_evidence(row, "python", Decimal(12))["status"] == "UNKNOWN"
    other = profile("I have never worked as Java Developer.")
    other["facts"]["professional"]["skills"] = [{"wording": field("Java")}]
    assert not skill_evidence(other, "java")


def test_learning_and_job_preferences_do_not_prove_zero_experience():
    for text in (
        "I am currently learning Python.",
        "I am looking for a job requiring no Python experience.",
    ):
        assert experience_evidence(profile(text), "python", Decimal(12))["status"] == "UNKNOWN"


def test_zero_experience_and_positive_history_remain_a_conflict():
    row = profile("I have no experience as Java Developer. I have 3 years of Java experience.")
    assert experience_evidence(row, "java", Decimal(12))["status"] == "CONFLICT"


def test_negative_role_quote_does_not_hide_a_separate_positive_duration():
    row = profile("I never worked as Java Developer. Java Developer for 3 months.")
    row["facts"]["professional"]["experience"] = [
        experience("Java Developer", duration="3 months")
    ]
    assert experience_evidence(row, "java", Decimal(12))["status"] == "CONFLICT"


def reviewed_job(description, experience_tag="2y", english="intermediate"):
    job = {
        "source_record_id": "synthetic-job",
        "status": "VALIDATED",
        "source_fields": {
            "Long Description": description,
            "Exp Years": experience_tag,
            "English Level": english,
        },
        "facts": {"requirements": []},
    }
    mapped = rule([])
    mapped.update(source_sha256=source_digest(job), requirement_map=[])
    return job, mapped


def test_description_experience_target_precedes_summary_tag_and_retains_other_issues():
    job, mapped = reviewed_job("At least 1.5 years of frontend experience.")
    mapped["experience"] = [
        {
            "code": "frontend",
            "scope": "frontend",
            "months": "18",
            "mandatory": True,
            "evidence": [
                literal_reference(
                    job, "Long Description", job["source_fields"]["Long Description"]
                )
            ],
        }
    ]
    mapped["unresolved"] = [
        "PUBLISHER_AND_PROSE_EXPERIENCE_VALUES_DIFFER",
        "SOURCE_TECHNOLOGY_MISSING",
    ]
    result = map_job_requirements(job, mapped)
    assert result["experience"][0]["months"] == "18"
    assert result["unresolved"] == ["SOURCE_TECHNOLOGY_MISSING"]
    assert result["source_mapping"]["notes"][0]["publisher_value"] == "2y"
    assert job["source_fields"]["Exp Years"] == "2y"


def test_description_english_target_precedes_scalar_without_changing_source():
    job, mapped = reviewed_job("Spoken English Pre-Intermediate or higher.")
    mapped["language"] = {
        "code": "english",
        "level": None,
        "evidence": [
            literal_reference(job, key, job["source_fields"][key])
            for key in ("English Level", "Long Description")
        ],
    }
    mapped["unresolved"] = ["PUBLISHER_AND_PROSE_ENGLISH_LEVELS_DIFFER"]
    result = map_job_requirements(job, mapped)
    assert result["language"]["level"] == 1 and not result["unresolved"]
    assert job["source_fields"]["English Level"] == "intermediate"


def test_conflicting_description_language_targets_still_block():
    job, mapped = reviewed_job("Intermediate English. Upper-Intermediate English.")
    mapped["language"] = {
        "code": "english",
        "level": None,
        "evidence": [
            literal_reference(job, "Long Description", text)
            for text in ("Intermediate English.", "Upper-Intermediate English.")
        ],
    }
    result = map_job_requirements(job, mapped)
    assert result["language"]["level"] is None
    assert "DESCRIPTION_ENGLISH_LEVELS_CONFLICT" in result["unresolved"]


def test_no_experience_job_tag_does_not_set_candidate_years_or_a_positive_target():
    job, mapped = reviewed_job("Java trainee role.", experience_tag="no_exp")
    job["facts"]["requirements"] = [{}]
    mapped["requirement_map"] = [
        {
            "index": 0,
            "wording": "no_exp",
            "disposition": "UNRESOLVED",
            "evidence": [literal_reference(job, "Exp Years", "no_exp")],
        }
    ]
    mapped["unresolved"] = ["REQUIREMENT_MAPPING_UNKNOWN:0"]
    result = map_job_requirements(job, mapped)
    assert not result["experience"] and not result["unresolved"]
    assert result["requirement_map"][0]["disposition"] == "NOT_ASSESSED"


def test_all_compatible_pairs_keep_people_and_reserved_jobs_separate(monkeypatch):
    families = {
        "Java": 20,
        ".NET": 20,
        "JavaScript": 20,
        "Python": 20,
        "QA": 10,
        "QA Automation": 10,
    }
    records, people = [], []
    for category, count in families.items():
        for i in range(count):
            identity = f"{category}-person-{i}"
            people.append(identity)
            records.append(
                {
                    "kind": "profile",
                    "source_record_id": identity,
                    "row_number": len(records),
                    "status": "VALIDATED",
                    "cohort": {"category": category},
                    "facts": {"field_status": {}},
                    "redaction": {"issues": []},
                }
            )
        for i in range(5):
            records.append(
                {
                    "kind": "job",
                    "source_record_id": f"{category}-job-{i}",
                    "row_number": len(records),
                    "status": "VALIDATED",
                    "cohort": {"category": category},
                    "fields": {"Long Description": ""},
                    "facts": {"requirements": []},
                }
            )
    splits = {
        identity: "train" if i < 60 else "validation" if i < 80 else "test"
        for i, identity in enumerate(people)
    }
    module = "simrecrut_ml.features.methods.pilot_training"
    monkeypatch.setattr(
        f"{module}.split_development_profiles", lambda rows, seed: (splits, [], 100)
    )
    monkeypatch.setattr(f"{module}._retrieval_evidence", lambda row: {})
    initial, before = build_development_pairs(records)
    expanded, after = build_development_pairs(records, include_all_compatible=True)
    key = lambda row: (row["base_profile_id"], row["job_id"], row["split"], row["job_partition"])
    assert len(initial) == 320 and len(expanded) == 420
    assert len({key(row) for row in expanded}) == 420
    assert {key(row) for row in initial} <= {key(row) for row in expanded}
    assert before["held_out_job_ids"] == after["held_out_job_ids"]
    assert all(row["split"] == "test" for row in expanded if row["job_partition"] == "held_out")
    held = [f"{category}-job-4" for category in families]
    frozen, report = build_development_pairs(
        records, include_all_compatible=True, profile_splits=splits, held_out_job_ids=held
    )
    assert set(report["held_out_job_ids"]) == set(held)
    assert all((row["job_id"] in held) == (row["job_partition"] == "held_out") for row in frozen)
    assert all(row["split"] == splits[row["base_profile_id"].partition(":")[2]] for row in frozen)
    with pytest.raises(ValueError, match="60/20/20"):
        build_development_pairs(records, profile_splits={key: "train" for key in people})
    with pytest.raises(ValueError, match="one frozen held-out job"):
        build_development_pairs(records, held_out_job_ids=["Java-job-0", "Java-job-1"] + held[2:])


def test_deployment_requires_reported_work_and_accepts_a_test_environment():
    row = profile("I improved a deployment pipeline for a test application.")
    result = activity_evidence(row, "deployment")
    assert result["status"] == "OBSERVED" and result["pass"] is True
    assert result["evidence"][0]["excerpt"] in row["source_fields"]["Moreinfo"]
    assert (
        activity_evidence(profile("I used a deployment pipeline for a service."), "deployment")[
            "pass"
        ]
        is True
    )


@pytest.mark.parametrize(
    "text",
    [
        "Tools: Jenkins, Docker, Kubernetes.",
        "Skills: deployment and CI/CD.",
        "I want to learn deployment processes.",
        "Desired tasks:\nDeploying applications.",
        "I am proficient with Jenkins.",
    ],
)
def test_tools_knowledge_and_future_roles_do_not_prove_deployment(text):
    assert activity_evidence(profile(text), "deployment")["status"] == "UNKNOWN"


def test_responsibility_context_is_retained_and_resets_at_a_tool_list():
    row = profile("Responsibilities:\n- deploying\nTechnology stack: monitoring tools")
    result = activity_evidence(row, "deployment")
    assert result["pass"] is True
    assert {ref["excerpt"] for ref in result["evidence"]} == {"- deploying", "Responsibilities:"}
    assert activity_evidence(row, "monitoring")["status"] == "UNKNOWN"


def test_explicit_denial_and_conflicting_activity_reports_are_distinct():
    row = profile("I have never performed deployment activities.")
    assert activity_evidence(row, "deployment")["pass"] is False
    row = profile("I have never performed deployments. I deployed an application.")
    assert activity_evidence(row, "deployment")["status"] == "CONFLICT"


@pytest.mark.parametrize(
    "text",
    [
        "I was the only backend developer on the team.",
        "I independently performed QA testing.",
        "I was a QA Team Lead.",
        "I was a developer working with the only QA on the project.",
        "I want to be the only QA on a project.",
    ],
)
def test_independent_work_and_other_peoples_roles_do_not_prove_sole_qa(text):
    assert activity_evidence(profile(text), "sole_qa")["status"] == "UNKNOWN"


def test_sole_qa_must_identify_the_candidates_own_project_role():
    row = profile("I was the only QA engineer on the project.")
    assert activity_evidence(row, "sole_qa")["pass"] is True
    assessed = rule([])
    assessed["projects"] = [{"code": "sole_qa", "activity": "sole_qa", "mandatory": True}]
    result = assess_pair(row, assessed)
    assert result["status"] == "READY" and result["matches"][4] == "1.0000000"
    assert result["active_areas"] == ["projects"]


def test_job_context_cannot_supply_a_candidates_sole_qa_history():
    row = profile("I performed manual testing.")
    row["source_fields"][
        "Long Description"
    ] = "Our project team includes one QA. Sole QA on a project."
    row["source_fields"]["Looking For"] = "I want to be the only QA on a project."
    assert activity_evidence(row, "sole_qa")["status"] == "UNKNOWN"


def test_mentoring_and_standards_need_separate_supported_actions():
    row = profile("I mentored junior engineers. I enforced coding standards.")
    condition = {
        "code": "engineering_guidance",
        "all_of": ["mentoring", "coding_standards", "best_practices"],
        "mandatory": True,
    }
    result = requirement_evidence(row, condition)
    assert result["status"] == "UNKNOWN" and result["pass"] is None
    assert [part["pass"] for part in result["components"]] == [True, True, None]


def test_a_labeled_work_bullet_can_establish_standards_review():
    row = profile("Code review: reviewed service changes for adherence to coding standards.")
    assert activity_evidence(row, "coding_standards")["pass"] is True


def test_an_explicit_first_person_experience_clause_can_establish_version_control():
    row = profile("I developed services, and have experience with version control systems.")
    assert activity_evidence(row, "versioning")["pass"] is True
    row = profile("I worked with people who have experience with version control systems.")
    assert activity_evidence(row, "versioning")["status"] == "UNKNOWN"


def test_mapping_cache_preserves_unicode_and_separates_changed_requests(tmp_path, monkeypatch):
    from simrecrut_ml.extraction.methods import deepseek
    from simrecrut_ml.features.methods.objective_rule import _mapping_reply
    import json

    calls = []

    def send(body, settings):
        calls.append(body)
        return {"excerpt": "First clause.\u2028Second clause.", "number": len(calls)}

    monkeypatch.setattr(deepseek, "send_request", send)
    monkeypatch.setattr(deepseek, "parse_response", lambda reply: reply)
    path = tmp_path / "job.json"
    original = _mapping_reply(path, {"prompt": "first"}, {})
    original["excerpt"] = "Caller mutation"
    cached = _mapping_reply(path, {"prompt": "first"}, {})
    changed = _mapping_reply(path, {"prompt": "revised"}, {})
    assert cached["excerpt"] == "First clause.\u2028Second clause."
    assert cached["number"] == 1 and changed["number"] == 2 and len(calls) == 2
    with (tmp_path / "mapping_responses.jsonl").open(encoding="utf-8") as stream:
        packets = [json.loads(line) for line in stream]
    assert len(packets) == 2 and not path.exists()
    assert packets[0]["request_sha256"] != packets[1]["request_sha256"]


def test_a_group_counts_once_and_duplicate_source_claims_do_not_inflate_the_score():
    row = profile("I configured build jobs. I deployed an application. I configured build jobs.")
    assessed = rule(["python"])
    assessed["skills"].append(
        {"code": "delivery", "all_of": ["build", "deployment"], "mandatory": True}
    )
    result = assess_pair(row, assessed)
    assert result["status"] == "READY" and result["matches"][0] == "0.5000000"
    assert len(result["evidence"]["skills"]) == 2


def test_partial_slash_list_support_does_not_become_an_invented_alternative():
    row = profile("I performed system testing. I performed integration testing.")
    result = requirement_evidence(
        row,
        {
            "all_of": ["system_testing", "integration_testing", "deployment"],
            "source_relation": "UNSPECIFIED_LIST",
        },
    )
    assert result["status"] == "UNKNOWN" and result["pass"] is None
    row = profile(
        "I performed system testing. I performed integration testing. I deployed a service."
    )
    assert (
        requirement_evidence(
            row,
            {
                "all_of": ["system_testing", "integration_testing", "deployment"],
                "source_relation": "UNSPECIFIED_LIST",
            },
        )["pass"]
        is True
    )


def test_missing_activity_keeps_the_pair_held_instead_of_becoming_a_failure():
    assessed = rule(["java"])
    assessed["skills"].append({"code": "deployment", "activity": "deployment", "mandatory": True})
    result = assess_pair(profile("I used Java."), assessed)
    assert result["status"] == "HELD"
    assert result["mandatory_checks"][-1]["pass"] is None


def test_unknown_activity_codes_and_ambiguous_condition_shapes_are_rejected():
    for condition in (
        {"activity": "unreviewed"},
        {"all_of": ["deployment"], "activity": "deployment"},
        {"all_of": ["deployment", "deployment"]},
    ):
        with pytest.raises(ValueError):
            requirement_evidence(profile("I deployed an application."), condition)
