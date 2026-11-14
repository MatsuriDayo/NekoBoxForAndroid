# Phase 2 tests

Run the Android JVM tests with the existing Android/JDK toolchain:

```text
gradlew -I app/src/test/phase2.init.gradle :app:testOssDebugUnitTest
```

The test-only init script supplies JUnit and Robolectric without changing production dependencies.
Tests execute against SDK 21 and 28 shadows. They cover the generated Room 6→7 migration,
old node/group/rule data, CRUD, references, actual backup v1 import/export, HTTP failures,
invalid content, cache preservation, atomic replacement, and deletion. Windows can reject
replacing a concurrently opened file; the test reports those refusals and checks preservation.
These tests do not establish Android device networking, background scheduling or file-watch behavior.

`Regression checks` now runs automatically on pushes and pull requests. It builds the pinned
Android libcore, runs the JVM suite (including API 34/35 foreground notification checks), generates
actual ConfigBuilder fixtures, and runs the Go contracts against those fixtures and Geo assets.
The new cases cover unused subscriptions, referenced subscription disabling, blocked redirects,
and backup rollback across the profile and settings databases. The fixture runner also checks
remote-only routes retain their DNS rule-set conditions.

For Linux host contracts after fetching the verified sibling sources, run:

```sh
bash buildScript/test/core.sh
```

Set the `NEKO_*` fixture/asset variables documented below to enable the complete fixture matrix.
The runner uses an isolated temporary module. It runs full functional contracts, then applies the
race detector to App DNS callback and GeoSite file-ownership tests. Full host startup under the
race detector currently reports a race in the pinned upstream sing-box `route/network.go`
(`NetworkManager.Start` and `updateInterface`); the official checkout is kept unchanged.

`core/` contains additional host contracts for the unchanged libcore constructor and official
sing-box v1.14.0. Run in an isolated copy alongside the existing `compat_host_test.go` host
file list and `dns_callback_test.go`, selecting the platform-specific replacement helper.
Set `NEKO_GEO_ASSETS` to the verified GeoIP/GeoSite asset directory and
`NEKO_CONFIG_FIXTURES` to the 37 actual ConfigBuilder fixtures. Do not patch the official Core.
