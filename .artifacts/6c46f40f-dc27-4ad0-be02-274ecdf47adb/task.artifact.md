# Tasks - Compi V15 CI Recovery & Refactoring

- [x] Fix `tests/rgb_script_runtime_test.cpp` to advance ticks sequentially across the 10 wait steps `[id: 1]`
- [x] Remove all string-matching decisions (`contains("rechazó")`, `contains("clave")`) in `CompiLabSession.kt` in favor of explicit typed `LabHttpException` handling `[id: 2]`
- [x] Run local Android build verification, unit tests, and assemble debug APK (`app-debug.apk`) `[id: 3]`
- [x] Commit, push to `origin/main` (`42191c1`), and verify new HEAD SHA `[id: 4]`
- [x] Provide technical walkthrough report `[id: 5]`
