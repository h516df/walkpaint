# Combined v0.65 candidate

Integration parents:
- PR104: 8b0f196a0d6961f5f08eb798ea53b38729f1fa44 (completed author branch)
- PR109/110/111 stack: 8376c743c590eb6f7845030ad840153b467618d5

The isolated integration preserves v0.65/code73, the current first-run consent, journal,
postcards, adaptive dock/replay, following, readable fog, user-selected landmarks and updater.
SQLite remains schema3. Journal and landmark backup sections are separate, versioned metadata;
local edits/explicit journal clears/landmark deletion tombstones win over older backup contents.
Both sections are validated before GPS import. Storage failures are handled independently after
GPS commit, retain original metadata files and report each corresponding partial-restore warning.

## Current evidence

- passed: 481 JVM tests, zero failures/errors/skips, including combined metadata tests.
- passed: 41 Python tests.
- passed: debug app and Android instrumentation APK compilation.
- passed: lintDebug, zero errors (52 warnings, 2 hints).
- not run yet: final combined native fog/backup/update-recreation/journal/navigation regression.
- not run: real-phone compass/GPS arrival and official-key map connectivity.

The separate PR104 and PR109/110/111 evidence is historical, not proof that this combined head
passed device acceptance. The new BackupRestoreProbe verifies both metadata sections together,
repeated restore with local clears/deletions, malformed metadata rejection, independent one-sided
and simultaneous failures, preservation of original metadata and retained GPS observations.

Only source, tests and synthetic acceptance descriptions are newly published here. Existing
PR104 synthetic screenshots remain inherited evidence for their original commit, not this one.
Formal signing/release is still a separate acceptance step in the original signing environment.
