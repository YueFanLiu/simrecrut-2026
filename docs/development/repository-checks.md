# Repository checks

Run the standard-library-only repository checker from the repository root:

```text
python scripts/maintenance/check_repository.py
```

The checker uses Git's tracked and unignored file list. It verifies that required
directories in `.github/project-layout.json` exist, project-owned text files and
paths contain no Han characters, text files use UTF-8, and local Markdown links
resolve within the repository. It also verifies that documentation pages live in
`docs/`, except the root README and repository templates under `.github/`. It
does not follow symlinks or fetch external links. Binary files and ignored private data are not
treated as authored text.

The language scan is a practical guard against Chinese text. Human review still
checks that prose, UI text, errors, names, and comments are clear English and that
comments describe the current code. CI cannot determine comment usefulness or
business correctness from this scan.

The repository workflow runs this checker. The Java workflow runs Maven
verification on JDK 17; Maven also rejects other JDK major versions. Component
tests are added when there is meaningful behavior to validate. Frontend and ML
workflows will be added with their working build configurations.

When adding or moving a directory, update the layout manifest and the applicable
architecture description together. When adding a documentation page, link it
from `docs/README.md`. Every empty retained directory uses a `.gitkeep` marker.
