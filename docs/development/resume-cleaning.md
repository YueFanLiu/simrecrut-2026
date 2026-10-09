# Shared resume cleaning backend

Java 17 `ResumeCleaningGateway` is the shared online/offline boundary. Spring services inject
`LocalResumeCleaner`; offline preparation invokes `ResumeCleaningCli`. Neither adapter uses
DeepSeek, trains a model, writes the original CV, or uploads unreviewed facts to MySQL.

The current baseline accepts UTF-8 TXT, text PDFs and DOCX paragraphs/tables, up to 10 MiB,
20 PDF pages and 500,000 extracted characters. Scanned PDFs require a separate OCR capability.
A finite dictionary extracts deduplicated skills with skill-only evidence and page numbers.
Experience, education, languages and projects remain empty pending richer rules and review.
This avoids claiming facts that have not been extracted. Skill mentions can include negations;
all output is `REVIEW_REQUIRED`, never a hiring decision or a training label.

Online controllers must enforce authentication, ownership and bounded concurrency before calling
this gateway. No public endpoint or persistence workflow is introduced by this component.
Do not save draft records as confirmed facts. Resource limits reduce risk but compressed DOCX
processing still requires bounded concurrency. The baseline is not a full anonymization system.

The repository dataset locations are `offline-ml/datasets/raw` and `offline-ml/datasets/clean`.
An external input directory is also supported. Research source files stay in that input directory;
the cleaner does not copy them. Live uploads must not automatically enter research datasets.

Build from `backend/java` with JDK 17:

```powershell
.\mvnw.cmd test package dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
$classpath = "target/classes;" + (Get-Content target/classpath.txt -Raw).Trim()
java -cp $classpath fr.isep.simrecrut.integration.pdf.ResumeCleaningCli INPUT_DIRECTORY OUTPUT_DIRECTORY
```

Batch preparation processes files sequentially, creates one draft JSON per source file,
refuses to overwrite output, and stops on the first failure. Earlier outputs remain available.
No CV text is logged. The draft schema and skill codes are provisional until team contracts
and equivalence tables are finalized. Neutral/Biased training and feature encoding are separate work.

## Two independent workflows

The local upload frontend source is `frontend/public/resume-workspace/`. The local workspace
build copies these assets and the canonical Java gateway/cleaner from this repository. It is
an adapter for the localhost workspace API, not an implemented Vue/authenticated team workflow.
The team backend business routes remain closed pending identity/resource authorization.

Web uploads create temporary originals, produce review drafts, save user-confirmed JSON to
Aiven and delete the temporary original only after successful cloud persistence. Cloud failures
retain originals; failed cleanup can be retried. Existing legacy library files are preserved.
No web upload is copied to the offline research dataset.

Offline research uses `scripts/dev/watch-resumes.ps1` with JDK 17 JAVA_HOME. It scans existing
and new PDF/DOCX/TXT files in `offline-ml/datasets/raw` every two seconds. Two stable scans
precede extraction. It writes JSON atomically to `offline-ml/datasets/clean`, uses source hashes
to skip unchanged records, and regenerates changed records. Failures create `.error.json` files
without exposing CV content; changed input triggers retry. Research originals are retained.
The watcher must remain running; this is not an installed Windows service. Only direct children
are processed. Stability checks reduce partial-copy risk; producers should rename completed
files into the raw directory for reliable handoff. Generated JSON is an unreviewed draft,
not a training label. Both workflows invoke `LocalResumeCleaner` without DeepSeek.

## Repository-local web adapter

The full local adapter source is now in `backend/java/src/main/java/fr/isep/simrecrut/workspace`.
Run `scripts/dev/resume-workspace.ps1` with JDK 17 to serve the frontend-owned assets on
127.0.0.1:8765. Stop another process using that port first. An optional `-DataDirectory` selects
an existing local workspace; otherwise data lives in ignored `runtime/resume-workspace`.
Configure Aiven through the local settings form. Never copy passwords or CV files into Git.
The dedicated launcher selects `workspace.yml` and scans only local workspace components.
Team business-route security is unchanged. This Windows single-user adapter uses SQLite and
Windows DPAPI; it does not replace the planned multi-user authentication/persistence workflow.
