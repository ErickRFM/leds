# Tasks - Compi V14 Real Corrective Execution

- [x] Separate Wi-Fi SoftAP route availability (`isNetworkRouteAvailable`) from key validity (`CompiLabSession.kt`) `[id: 1]`
- [x] Add unit test proving valid key without network route does not produce `LOCAL_ROUTE_AVAILABLE` (`CompiLabSessionTest.kt`) `[id: 2]`
- [x] Execute local Gradle unit tests, lint checks, and assemble debug APK (`app-debug.apk`) with SHA-256 calculation `[id: 3]`
- [x] Commit, push feature branch, merge into `main` (`6b38ed6`), and push to `origin/main` `[id: 4]`
