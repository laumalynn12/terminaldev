# CI notes and speed optimization

This document records confirmed specifications for GitHub Actions runners and our CI pipeline design, looked up on 21 November 2026.

## 1. Confirmed runner specifications

Confirmed from GitHub documentation and changelogs:

- **Public repositories:**
  - Standard GitHub-hosted Linux x64 runners (`ubuntu-24.04` or `ubuntu-latest`) provide **4 vCPUs, 16 GB RAM, and 14 GB SSD**.
  - Standard GitHub-hosted Linux arm64 runners (`ubuntu-24.04-arm`) provide **4 vCPUs, 16 GB RAM**.
  - Compute minutes on public repositories are free and unlimited.
- **Private repositories:**
  - Standard runners provide 2 vCPUs and 8 GB RAM, billed against account minute/dollar pools.
- **`ubuntu-slim`:**
  - Provides only 1 vCPU and 3.75 GB RAM. Forbidden for Gradle or NDK builds.

## 2. CI build pipeline architecture

### Workflows
1. `ci.yml` (Triggered on push and pull_request):
   - **Job 1 (Compile & Fail-Fast):** Runs `./gradlew assembleDebug` (or `compileReleaseKotlin`) first to catch syntax, import, or typing errors rapidly.
   - **Job 2 (Unit tests & Lint - Parallel):** Runs JVM unit tests (`testDebugUnitTest`) and Android Lint in parallel once compile succeeds.
   - **Job 3 (Size gate):** Inspects produced binaries and release APKs against the 6 MB per-ABI budget.
   - Uses `concurrency` with `cancel-in-progress: true`.
2. `release.yml` (Triggered on tag `v*` and manual `workflow_dispatch`):
   - Matrix build across ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
   - Decodes release keystore from GitHub Secret `KEYSTORE_BASE64` to `$RUNNER_TEMP`, builds signed release APKs, runs `apksigner verify --print-certs`, removes the temporary keystore file, and uploads assets to GitHub Releases with SHA-256 checksums.
   - Verification job (`verify-release`): Downloads published assets and verifies signatures and checksums.
3. `compat.yml` (Scheduled / manual):
   - Runs instrumented emulator smoke tests across the agreed API levels without blocking standard commits.

## 3. Caching and build acceleration techniques

1. **Gradle action setup:**
   - Use `gradle/actions/setup-gradle@v6` for automatic caching of dependencies, Gradle wrappers, and build-cache artifacts.
   - Do not use `actions/cache` on `~/.gradle` directly, and do not use `cache: gradle` in `actions/setup-java`.
2. **Gradle daemon and flags:**
   - Configure `gradle.properties`:
     ```properties
     org.gradle.jvmargs=-Xmx6144m -XX:+UseG1GC
     org.gradle.parallel=true
     org.gradle.caching=true
     org.gradle.configuration-cache=true
     ```
3. **NDK and PRoot build caching:**
   - Pin PRoot commit hash.
   - Cache compiled native artifacts or integrate `ccache` in CMake:
     ```yaml
     - name: Setup ccache
       uses: hendrikmuhs/ccache-action@v1.2
       with:
         key: ${{ runner.os }}-ndk-${{ env.NDK_VERSION }}
     ```
4. **Shallow checkouts:**
   - `actions/checkout@v4` with `fetch-depth: 1`.
   - Add `paths-ignore` for documentation changes (`docs/**`, `*.md`).

## 4. Gradle wrapper plan

Because build tools are not executed locally (Section 0A), the repository will not initially commit a generated `gradlew` binary script from a local machine. In CI, `gradle/actions/setup-gradle@v6` with a pinned `gradle-version: "8.11"` will execute the build commands directly (`gradle assembleRelease`). If a wrapper script is specifically required for external compatibility, a one-shot `workflow_dispatch` CI workflow can generate and commit the wrapper files.
