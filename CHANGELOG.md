# Changelog

A projekt a [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) formátumot és Semantic Versioninget követ.

## [Unreleased]

### Added

- Java 21 multi-module Gradle Kotlin DSL build convention pluginekkel és version cataloggal.
- CI minőségi kapuk: JUnit 5, Spotless, Checkstyle és Error Prone.
- Testcontainers tesztplatform-függőségek a jövőbeli infrastruktúra-integrációkhoz.
- Típusos exception hierarchy, dependency container, event bus és bounded scheduler.
- Szigorú properties-alapú configuration loader és production validator.
- SLF4J/Logback logging és Micrometer lifecycle metrics.
- Stabil plugin API alap, standalone bootstrap és determinisztikus application lifecycle.
- Konkurens shutdown rekurzió nélküli koordinációja és listenerhiba esetén is garantált erőforrás-takarítás.
- Gradle wrapper validation és teljes artifact-generálás a CI buildben.
- Hordozható Gradle runtime policy és a Phase 1 `clean check` elfogadási parancs explicit CI végrehajtása.
