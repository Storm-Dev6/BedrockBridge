# Phase A build recovery

## Completed work

- Restored the precompiled Java convention plugin's access to the target project's `libs`
  version catalog through `VersionCatalogsExtension`.
- Added a Gradle TestKit regression test and connected the included build's test task to the root
  `check` lifecycle.
- Applied the configured Spotless format to the existing Java source set and corrected Checkstyle
  violations.
- Corrected module dependency declarations required by the existing Bedrock codec and packet tests.
- Resolved Error Prone findings without weakening analysis of project-owned source, including stable
  RakNet reliability wire identifiers, immutable byte-array boundaries, enum-keyed queues, and
  loopback address construction. Error Prone excludes only sources emitted by the third-party JMH
  annotation processor.
- Isolated JUnit temporary files inside each Gradle test task to make cleanup reliable on Windows.

## Validation

- Java: Oracle JDK 21.0.9
- Command: `gradlew --no-daemon clean check assemble`
- Result: successful, 294 actionable tasks; all module tests, Spotless, Checkstyle, Error Prone,
  assembly tasks, and the build-logic TestKit regression passed.
- Prohibited marker scan: no `TODO`, `FIXME`, stub, placeholder, or dummy implementation markers in
  project sources, build scripts, workflows, or documentation.
- `gradle/wrapper/gradle-wrapper.jar` was not modified. Its SHA-256 during validation was
  `7D3A4AC4DE1C32B59BC6A4EB8ECB8E612CCD0CF1AE1E99F66902DA64DF296172`.

## Commits and CI

The Phase A commit and GitHub Actions result are recorded here after publication.

## Known limitations

- Javadoc generation succeeds but reports documentation-completeness warnings inherited from the
  existing public API. These warnings do not fail the configured verification lifecycle.
- Phase A restores and hardens the existing Phase 1–4 implementation; it does not add protocol
  features.

## Next phase

Phase 5: implement the Bedrock Play Protocol only after the Phase A publication and CI gates are
green.
