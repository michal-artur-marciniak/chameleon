# Package Structure Refactoring Plan

## Overview

This document outlines the complete 3-phase refactoring to align the codebase with DDD best practices as documented in ARCHITECTURE.md and DOMAINS.md.

---

## Phase 1: Application Layer Consolidation

### Goal
Move application services into their bounded contexts under `core/src/main/kotlin/com/chameleon/<context>/application/`.

### Current State
- Application services in `core/src/main/kotlin/com/chameleon/<context>/application/`
- Use cases in `core/src/main/kotlin/com/chameleon/<context>/application/`

### Target State
- `core/` module contains ALL application layer code
- `bootstrap/` module for wiring only
- Clear separation: domain + application (core) vs infrastructure (infra)

### Steps

1. **Ensure core application package structure:**
```
core/src/main/kotlin/com/chameleon/<context>/application/
├── AgentTurnService.kt
├── AgentRunService.kt
├── SessionAppService.kt
├── ToolExecutionService.kt
├── HandleInboundMessageUseCase.kt
├── LlmRequestBuilder.kt
└── MemoryContextAssembler.kt
```

2. **core/build.gradle.kts:**
- Ensure `core` includes application dependencies used by orchestration (ktor client, slf4j, logback, coroutines).

3. **Move files:**
- Ensure all application services live in `core/src/main/kotlin/com/chameleon/<context>/application/`
   - Remove application module once build succeeds

4. **Update settings.gradle.kts:**
- Remove `application` module include

5. **Update bootstrap/build.gradle.kts:**
- Remove `application` dependency

---

## Phase 2: Domain Packaging Standardization

### Goal
Standardize all bounded contexts to use `domain/` and `port/` subdirectories.

### Current State (after Phase 1)
- `agent/` - has `domain/` ✓
- `session/` - flat structure ✗
- `memory/` - flat structure ✗
- `tool/` - flat structure ✗
- `plugin/` - has `domain/` ✓
- `identity/` - missing entirely ✗
- Ports scattered in `ports/` or root

### Target State
All contexts follow:
```
<context>/
├── domain/          # Domain aggregates, entities, value objects, events
│   ├── *.kt
│   └── *Events.kt
└── port/            # Repository and service ports
    └── *Port.kt
```

### Steps

1. **Create directories:**
```bash
mkdir -p core/src/main/kotlin/com/chameleon/session/domain
mkdir -p core/src/main/kotlin/com/chameleon/session/port
mkdir -p core/src/main/kotlin/com/chameleon/memory/domain
mkdir -p core/src/main/kotlin/com/chameleon/memory/port
mkdir -p core/src/main/kotlin/com/chameleon/tool/domain
mkdir -p core/src/main/kotlin/com/chameleon/tool/port
mkdir -p core/src/main/kotlin/com/chameleon/identity/domain
mkdir -p core/src/main/kotlin/com/chameleon/identity/port
```

2. **Move Session files:**
```
Session.kt → session/domain/
Message.kt → session/domain/
SessionDomainEvents.kt → session/domain/
CompactionConfig.kt → session/domain/
SessionRepository.kt → session/port/
SessionManager.kt → session/port/
```

3. **Move Memory files:**
```
MemoryIndex.kt → memory/domain/
MemoryChunk.kt → memory/domain/
MemoryDomainEvents.kt → memory/domain/
MemoryConfig.kt → memory/domain/
MemorySearchService.kt → memory/domain/
ports/MemoryIndexRepositoryPort.kt → memory/port/
```

4. **Move Tool files:**
```
ToolPolicyService.kt → tool/domain/
ToolDomainEvents.kt → tool/domain/
ToolModels.kt → tool/domain/
ToolsConfig.kt → tool/domain/
ToolRegistry.kt → tool/port/
```

5. **Standardize existing port directories:**
```
agent/ports/ → agent/port/
llm/ports/ → llm/port/
```

6. **Update imports in moved files**

---

## Phase 3: Package Rename (agent.platform → com.chameleon)

### Goal
Rename root package from `agent.platform` to `com.chameleon` and flatten hierarchy.

### Current State (after Phases 1-2)
- Package: `com.chameleon.*`
- Structure: `com/chameleon/<context>/`

### Target State
- Package: `com.chameleon.*`
- Structure: `com/chameleon/<context>/`
- SDK: `com.chameleon.sdk`
- Plugins: `com.chameleon.plugin.*`

### Steps

1. **Create new package structure:**
```bash
# Core domain
mkdir -p core/src/main/kotlin/com/chameleon/{agent,session,memory,tool,plugin,identity,llm,channel,config}

# Application layer
mkdir -p core/src/main/kotlin/com/chameleon/{agent,session,tool,llm,memory}/application

# Infrastructure
mkdir -p infra/src/main/kotlin/com/chameleon/infrastructure/{persistence,llm,tool,agent,memory,channel,plugin,config}

# Bootstrap (formerly app)
mkdir -p bootstrap/src/main/kotlin/com/chameleon

# SDK
mkdir -p sdk/src/main/kotlin/com/chameleon/sdk

# Plugins
- Move built-in plugins into `infra/src/main/kotlin/com/chameleon/infrastructure/plugin/`
```

2. **Copy all files to new locations**

3. **Update package declarations:**
```kotlin
// Old
package com.chameleon.agent.domain
import com.chameleon.session.Session
import agent.sdk.ChannelPort

// New
package com.chameleon.agent.domain
import com.chameleon.session.Session
import com.chameleon.sdk.ChannelPort
```

4. **Update build files:**
```kotlin
// bootstrap/build.gradle.kts
application {
    mainClass.set("com.chameleon.ApplicationKt")
}

tasks.register<Jar>("fatJar") {
    manifest {
        attributes["Main-Class"] = "com.chameleon.ApplicationKt"
    }
}
```

5. **Rename app → bootstrap**
```kotlin
// settings.gradle.kts
include(
    "bootstrap"
    "core",
    "infra",
    "sdk"
)
```

---

## Execution Strategy

### Option A: Incremental (Recommended)
Execute one phase at a time, verify build after each:

1. Complete Phase 1 → Build → Commit
2. Complete Phase 2 → Build → Commit
3. Complete Phase 3 → Build → Commit

### Option B: Automated Script
Create a comprehensive script that does all phases atomically.

### Option C: Manual Step-by-Step
Follow each step manually with verification.

---

## Verification Checklist

After each phase, verify:
- [ ] `./gradlew build` succeeds
- [ ] No compilation errors
- [ ] All imports resolve
- [ ] Tests pass (if any)
- [ ] Main application starts

---

## Risk Mitigation

1. **Before starting:**
   - Commit all current work
   - Create feature branch
   - Ensure CI/CD is green

2. **During refactoring:**
   - Use `cp` not `mv` until verified
   - Keep old files until build succeeds
   - Run build frequently

3. **After completion:**
   - Run full test suite
   - Manual smoke test
   - Update documentation (README.md, AGENTS.md)

---

## Files to Update

### Phase 1
- `settings.gradle.kts`
- `bootstrap/build.gradle.kts`
- `core/build.gradle.kts`
- All application service files

### Phase 2
- All domain files (update imports)
- All files that import from moved locations

### Phase 3
- All Kotlin files (package + imports)
- `bootstrap/build.gradle.kts` (mainClass)
- `settings.gradle.kts` (if renaming app → bootstrap)
- Documentation files referencing packages

---

## Estimated Effort

- Phase 1: 30 minutes + build verification
- Phase 2: 45 minutes + import fixes + build verification
- Phase 3: 60 minutes + import fixes + build verification
- Total: ~2.5 hours with verification

---

## Next Steps

1. Decide on execution strategy (A, B, or C)
2. Create feature branch
3. Execute Phase 1
4. Verify build
5. Proceed to Phase 2
6. Verify build
7. Proceed to Phase 3
8. Final verification
9. Update documentation
10. Merge to main
