// Adapted from WANG dbd345c; synthetic Research fixtures only.
export const fixtures = {
  "experiments": [
    {
      "id": "EXP-001",
      "name": "Gender attribute study",
      "type": "Single-factor paired",
      "job": "Software Engineer v3",
      "subjects": 24,
      "status": "Completed",
      "protocol": "P-2026.01",
      "date": "2026-10-08"
    },
    {
      "id": "EXP-002",
      "name": "Age attribute analysis",
      "type": "Single-factor paired",
      "job": "Data Analyst v2",
      "subjects": 0,
      "status": "Draft",
      "protocol": "P-2026.01",
      "date": "2026-10-07"
    },
    {
      "id": "EXP-003",
      "name": "Controlled language comparison",
      "type": "Single-factor paired",
      "job": "Backend Developer v1",
      "subjects": 32,
      "status": "Queued",
      "protocol": "P-2026.01",
      "date": "2026-10-06"
    },
    {
      "id": "EXP-004",
      "name": "Baseline validation",
      "type": "Baseline",
      "job": "Software Engineer v3",
      "subjects": 16,
      "status": "Completed",
      "protocol": "P-2026.01",
      "date": "2026-10-05"
    }
  ],
  "subjects": [
    {
      "id": "SUB-001",
      "source": "Reviewed Profiles",
      "name": "Software Engineer",
      "status": "Eligible",
      "skills": "Java, SQL, Git",
      "education": "Bachelor / Computer Science"
    },
    {
      "id": "SUB-002",
      "source": "Reviewed Profiles",
      "name": "Backend Developer",
      "status": "Eligible",
      "skills": "Java, Docker",
      "education": "Master / Software Engineering"
    },
    {
      "id": "SUB-003",
      "source": "Authorized Candidate Cases",
      "name": "Data Analyst",
      "status": "Eligible",
      "skills": "SQL, Python",
      "education": "Bachelor / Mathematics"
    },
    {
      "id": "SUB-004",
      "source": "Authorized Candidate Cases",
      "name": "Restricted case",
      "status": "Grant revoked",
      "skills": "Unavailable"
    },
    {
      "id": "SUB-005",
      "source": "Reviewed Profiles",
      "name": "Expired profile",
      "status": "Expired",
      "skills": "Unavailable"
    }
  ],
  "models": [
    {
      "id": "N-2026.01",
      "name": "Neutral ML",
      "version": "1.0.0",
      "status": "Active",
      "schema": "32 inputs",
      "protocol": "P-2026.01",
      "mode": "Professional block + zero research block"
    },
    {
      "id": "B-2026.01",
      "name": "Biased ML",
      "version": "1.0.0",
      "status": "Active",
      "schema": "32 inputs",
      "protocol": "P-2026.01",
      "mode": "Professional block + declared research attributes"
    },
    {
      "id": "N-2026.02",
      "name": "Neutral ML",
      "version": "1.1.0",
      "status": "Tested",
      "schema": "32 inputs",
      "protocol": "P-2026.01",
      "mode": "Professional block + zero research block"
    }
  ]
}
