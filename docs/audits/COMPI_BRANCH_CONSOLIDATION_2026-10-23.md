# Compi branch consolidation inventory — 2026-10-23

Generated UTC: 2026-10-24T00:45:27+00:00

Pinned comparison: `origin/main` = `eccdd22fa049fea65fc31254cd5c28c508241e46`. Remote branch tips are local fetched refs; this script does not fetch or mutate Git.

Scope: **234 origin branches** (including origin/main, excluding symbolic origin/HEAD).

## Method and interpretation

Each branch is checked with `git rev-list --left-right --count BASE...TIP`, `git cherry BASE TIP`, exact tip-tree comparison, and `git patch-id --stable` of the aggregate merge-base-to-tip diff against every nonmerge base-history commit. Left/right counts include merges; cherry counts exclude merge commits. All comparisons use the pinned SHA above, so concurrent workspace edits cannot change this inventory.

- A: the tip is an ancestor of the comparison commit (right count zero). Every reachable branch commit is included in history; later commits can still supersede its behavior.
- T: an unmerged tip has exactly the same tracked tree as the comparison commit.
- S: the aggregate merge-base-to-tip patch has the same stable patch ID as a nonmerge commit already in base history (usually a squash merge). The matched commit SHA is recorded. Later changes may supersede that patch; stable IDs ignore whitespace.
- P: every nonmerge commit reported by cherry has an equivalent patch in base history. This does **not** prove merge resolutions, aggregate squash equivalence, or present-day feature preservation.
- M: some individual patches are equivalent and others unmatched. Unmatched patches are review candidates, **not proof of missing functionality**.
- D: no equivalent nonmerge patches were found in the branch-only series. Squashing, rebasing with edits, manual integrations, and superseded implementations can all produce this result. Targeted source review is required.

Individual patch IDs do not detect general semantic equivalence or many-commit squashes; aggregate patch checks recover exact squash matches, but amended or partial squashes can remain unmatched. No branch is classified as safe to delete, and no bulk merge is recommended.

## Counts

- A: ancestor/included: **25**
- D: divergent; semantic review needed: **50**
- M: mixed; inspect unmatched patches: **1**
- P: patch-equivalent nonmerge series: **2**
- S: aggregate patch matches base-history commit: **156**

## Full branch inventory

`Base-only` / `tip-only` are ancestry counts; `−` / `+` are cherry-equivalent / unmatched nonmerge counts.

| Branch | Exact tip SHA | Base-only | Tip-only | − | + | Classification | Aggregate matching commit(s) |
|---|---|---:|---:|---:|---:|---|---|
| `origin/baseline/rgb-controller-v1.1` | `5effcbccc8f49222120c6fb0bb8c2c51f7ddc874` | 287 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/chore/compi-aurora-deep-audit-20261023` | `eccdd22fa049fea65fc31254cd5c28c508241e46` | 0 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/chore/compi-cleanup-active-paths-20261022` | `55afc268bac27403f448dd517b5d73541c54a899` | 9 | 2 | 0 | 2 | D: divergent; semantic review needed |  |
| `origin/chore/v15-1-consolidation` | `bbd1e5796b952d029a3c4f8995836a185e9ff618` | 191 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `2e7b86157f1c9c20e609780138394ee0daba0f9a` |
| `origin/ci/v3-firmware-size-budget` | `5a9134fa6dfccdea9aa3b5676acdb77f52440920` | 278 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `4504d8d75bd953d35e5dc19720d7a8214eff73e8` |
| `origin/ci/v3-hardware-feedback-artifacts` | `4797cac7b77ee700928b2db7a9b05f92be32bdbf` | 269 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `7e3515e6ef35975465a282f46fd736bcde1c4131` |
| `origin/docs/compi-end-to-end-closeout-plan-20261022` | `598a3b06d9fa852f4e7e2b2feca6b96b221fc654` | 17 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `b917a05bb9be900b22fd5fb67ee281fd21bc2a15` |
| `origin/docs/compi-integration-acceptance-20261022` | `2208047d24d66d90d9cdf2f6e698aee07fb80664` | 47 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `2e6bc56bdc22523add9acd352957719ffdab8371` |
| `origin/docs/compi-v4-v8-execution` | `1c2335a462fff745debf725391353fb6b941df17` | 245 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `6e572ef788796c2c9cae5021b32b1ff9e63fc40d` |
| `origin/docs/v2-integrated-status` | `6fab25ee77aa520300eab9f04259056d314aa428` | 283 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `1ee9e0ef3e5ff9521dadcd5fb035f1eb954729ef` |
| `origin/docs/v3-field-testing-handoff` | `d3ca187e5775f20e884c243d24c93483d8703d36` | 264 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `d87f7b5c95edaad0079a495506939905c023a8b6` |
| `origin/docs/v3-verified-integration-status` | `14fbda6e5dc1a43d5ac4bfe726b422a37aa27d6f` | 270 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `0146c7d4779c0089ae7acdad973035e8ab054b2a` |
| `origin/docs/v5-editor-and-arduino-workflow` | `78a9c0cd5788ee83e522683abac9e6c7b0f4cee2` | 247 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `15c1cdff40237a200ce874d9106fc0f8594aa214` |
| `origin/docs/verified-esp32-d0wd-v3-4mb` | `e6e43588f788e16b35adc2a2e48b8a0804bdc7d7` | 282 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `60f7b4fee6e2f0987454de835a2f737818fe93e4` |
| `origin/download/compi-arduino-full-lab-20261022` | `a1adb79278a7ef2556f2c8054f94d2b28d3d083c` | 63 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/download/compi-full-arduino-20261022-final` | `20036a3abf822b00ac9df9b110dc3e80d7f41220` | 9 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/feat/compi-aurora-premium-ui-20261022` | `ecd29923f85c1f403d0bed5c9572b3e031856cf7` | 33 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/feat/compi-automation-scheduler` | `c091023be10927047acf27b0322731a5970166b4` | 242 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `3f1837c3e4b92100ff670e818d59d0969283241c` |
| `origin/feat/compi-dfplayer-audio-policy-20261022` | `3ae2962e1bc5096b428bcd1acbd512ae095d7fe6` | 42 | 3 | 3 | 0 | P: patch-equivalent nonmerge series |  |
| `origin/feat/compi-dfplayer-environmental-voice-20261021` | `28ebeeb664b3e02406ae7cd5d16f7b9c64e8e893` | 99 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `15bc6347893550aaa46eed140c3f3681fac7f250` |
| `origin/feat/compi-dfplayer-visible-module-20261022` | `cbadb9756ad17db8169711776fa6a927500d7f74` | 82 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `e17fcd24f3ee916bf6214fe6210eaca4e2c3cbf8` |
| `origin/feat/compi-humidity-aware-rgb-20261022` | `b5c9ef74a2a11650795bad3195a2c69bdfe97375` | 57 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `cb52ddf1cb125ab51bae8e25d9b893f29f224c5f` |
| `origin/feat/compi-ldr-persistent-calibration-20261022` | `70b61819307bc0f412c85d53a162b265d18c969c` | 93 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `f0fe427742b3feb796a01d70b5e69fecfef83d93` |
| `origin/feat/compi-ldr-ux-calibration-guidance-20261022` | `f4226db9c2c1c27ee827d7aaee69e9ffd719b5d0` | 81 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `01c4c40c28f1c5d491b372f3734910c5c100b26b` |
| `origin/feat/compi-ldr-visual-calibration-20261022` | `6a7a51f5fae8b02589e333d09acc09ddb03d232b` | 53 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `a7ac997b27066d62cc68bad2e6a92fef35e01950` |
| `origin/feat/compi-modules-clean-hub-20261022` | `68bbf3dfa2192c93356675efa77c5b272d5ec246` | 78 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/feat/compi-modules-ui-polish-20261022` | `1f6da28827bb35446d64d179fc011899d7c8215f` | 80 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `ce5fdf727d5ee2d30fcabc34efa9a32b5f85b99d` |
| `origin/feat/compi-oled-boot-dht11-20261021` | `b56e873f1be434bc80c7f8604012ed9566d424e6` | 100 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `f86ba5c47270424b158a6cb50fd9a5ce5f659c34` |
| `origin/feat/compi-oled-cat-dance-20261022` | `c37ebe486ed04326726418eae03c4ae0a1d8a270` | 56 | 2 | 0 | 2 | D: divergent; semantic review needed |  |
| `origin/feat/compi-oled-faces-aligned-20261022` | `cc81dde57a03df8d3e22695ba44e000fefa01a80` | 57 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `8f61b8e167e2f7ceae044079236a118d32e56afe` |
| `origin/feat/compi-oled-live-controls-20261021` | `fcf8eb1b9ee7a0d979a66aa6e2de9a89be3b72e5` | 101 | 8 | 0 | 8 | D: divergent; semantic review needed |  |
| `origin/feat/compi-oled-live-preview-integration-20261021` | `57681f9acb987ee22b1f380460e7bcb7b6ca64b4` | 97 | 13 | 0 | 13 | S: aggregate patch matches base-history commit | `d22b15caaada4ecdd54e28e660c53f71ca9af5d5` |
| `origin/feat/compi-oled-scenes-ui-20261022` | `c2261f68150e22db45d969c05df69752dd0377a2` | 80 | 2 | 0 | 2 | D: divergent; semantic review needed |  |
| `origin/feat/compi-ota-preflight` | `d72d7068a10782d9987c2ca2a011542ad8a1646f` | 241 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `478c8316a10d803ac35d0f378c5a9fa426002210` |
| `origin/feat/compi-real-station-ux-20261022` | `0e0d36c01828cc6c7b69d26013e90a12275bdb67` | 63 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `feac5d9f6826861fa9631b6dd072223da7c96e73` |
| `origin/feat/compi-rgb-five-bands-reconciled-20261022` | `cdb59b87a226f986bdae08697c2a9e5ad6f9e647` | 21 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/feat/compi-station-compact-live-overview-20261022` | `bcc6851a6f4d25bfc114d495bd99b6ba9198d401` | 77 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `4f72885e7e674a357e10a1d1b4f04f4d9d229a5f` |
| `origin/feat/compi-station-live-charts-20261022` | `6c4c4f8c6b5d984c04b868f5717bb47e851d4032` | 62 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `b81537ec1df4fa264ec8fa11843eaacd0d60cca3` |
| `origin/feat/compi-station-overview-20261022` | `247a642ede7a69c174938ba52acb0d676ac272ed` | 79 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/feat/compi-station-voice-cycle-20261022` | `9d1204f9503cb8bbdbc1d68f268dd7058115c7f7` | 81 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `2eb58495ab893033fb40706bf73c1f18a844426b` |
| `origin/feat/compi-ux-master-v7` | `9c22a40b789acff99af29f479fb50cb0bad41088` | 239 | 21 | 0 | 21 | S: aggregate patch matches base-history commit | `7f4680c5c9214cf0a78773cc37c54fabf7e64e44` |
| `origin/feat/compi-v10-device-operations-and-studio-recovery` | `9ca12df6dca0bcd495c6d25725336cbd94096152` | 236 | 19 | 0 | 19 | S: aggregate patch matches base-history commit | `bfb5c19ed7b208182dcf5f219e1d1db38bac9ac7` |
| `origin/feat/compi-v12-bounded-automation-main` | `7ce934c6dc8014acae792d71a80134e73af1d4fc` | 235 | 9 | 0 | 9 | S: aggregate patch matches base-history commit | `04b2afb035023c50fea031d687996c8006ea2336` |
| `origin/feat/compi-v12-safe-rgb-automation-lab` | `9e87d62bf49fcde116fd19e160c4546d55de3b9d` | 236 | 26 | 0 | 26 | D: divergent; semantic review needed |  |
| `origin/feat/compi-v15-arbiter-async-scan` | `73d93c48d329721850c5ea22d6a79106ff15da6a` | 201 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/feat/compi-v15-hardware-component-studio` | `e7427c13042b09e10d26100495a2701377162f84` | 192 | 7 | 0 | 7 | S: aggregate patch matches base-history commit | `187731900d9889120b8f5acc236dc0ba7c91bbb4` |
| `origin/feat/compi-v15-modular-sensors-ui` | `420ee85f29374588e37cbb5f34da81e36179fa60` | 213 | 20 | 0 | 20 | D: divergent; semantic review needed |  |
| `origin/feat/compi-v4-capabilities-endpoint` | `55f5e1cd9f641e0dfbf684a12e19d15d9153648b` | 243 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `7d26f00fab00d38d57fdac774589126b792e47ce` |
| `origin/feat/compi-v4-capability-contract` | `c5f1c75ed6f4c5b34a1b00ec7bd488b0de34ebb1` | 244 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `a04c7b6d6716908f0450e07150e7edae16006483` |
| `origin/feat/compi-v6-rgb-clock-runtime` | `88044c2326daafaa442bb0280daed36ec49942f2` | 240 | 2 | 0 | 2 | D: divergent; semantic review needed |  |
| `origin/feat/compi-v9-reference-ui` | `4bd5bc4201de5901d852b3f8f4ddfba9b7da4eb7` | 235 | 14 | 14 | 0 | P: patch-equivalent nonmerge series |  |
| `origin/feat/compi-visual-merge-rebased-20261022` | `d3a6157f5ce6fb592f625949fe533bed6e777800` | 78 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `19543efbbb130c6eb824990a8a9df4e13e0ed10f` |
| `origin/feat/compi-visual-sensors-20261022` | `7ee2f743c46d56ae8abed9e67e0287b98353fadf` | 80 | 5 | 0 | 5 | D: divergent; semantic review needed |  |
| `origin/feat/compi-voice-card-ui-20261022` | `7259809cec273556af2388f934321d9d8b3e9950` | 69 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `3db3a0ddb610cb98149df2f2b918418f3ba23cae` |
| `origin/feat/dfplayer-clean-player-ui-20261022` | `278481dfcd3dd4eff82903db4f8c1cd3095af317` | 81 | 4 | 0 | 4 | D: divergent; semantic review needed |  |
| `origin/feat/dht11-dht22-gpio16-integration-20261018` | `9e7b2dc0adae69635c869616164ba0e7acb3b80d` | 122 | 7 | 0 | 7 | S: aggregate patch matches base-history commit | `9f9c85028001ff9d693b8c4090326500acfc3534` |
| `origin/feat/dht11-isolated-probe-ci-20261018` | `190f5088dbbe45c0e4ed94f603d7c019aca75f0c` | 115 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `f0b54c9449429f109fb3985acfe2311d688c1512` |
| `origin/feat/esp32-control-center-v2-foundation` | `6328811e0e8ebe282f373a94383b286d3e875867` | 287 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `183d668b0d9fa19ebde367f448b76c7f4321ca5f` |
| `origin/feat/esp32-v2-declarative-lab` | `9290a5c8511dd48500a722d620f84c980be25473` | 284 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `fb596c824171b0e4a036c8a70f80fdb7b7229532` |
| `origin/feat/esp32-v2-local-wifi-gpio-lab` | `8ab8dc8b1b77cf166551679633af5cd765662471` | 286 | 8 | 0 | 8 | S: aggregate patch matches base-history commit | `2c27fbf79388e54185b7e2f023d295e07873cd6b` |
| `origin/feat/esp32-v2-ota-preflight-diagnostics` | `5981351dcf449d81e764bd926ba75f5763d7ea8c` | 285 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `2419440332a688783d6d32b9631f94f7c48e70b9` |
| `origin/feat/modules-real-charts-dht11-ux-20261018` | `14eb77d1b5ada3c500856d41255e4e0c04686cfa` | 118 | 8 | 0 | 8 | S: aggregate patch matches base-history commit | `632017f89716baa015cd50f3b5076b2020438bb3` |
| `origin/feat/rgb-controller-app` | `c2b4cf9f809d3ec4ba563f977587025e1683c218` | 301 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/feat/rgb-controller-v1-1-polish` | `6403b77fac0137817d7c57b8c347761af57c7684` | 288 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `5effcbccc8f49222120c6fb0bb8c2c51f7ddc874` |
| `origin/feat/station-sensor-first-layout-20261022` | `aa791becdc481f65540711757badd03ccc79d9c0` | 48 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `99cf5e876d5fa8b3675380b35dfdc9a90b6142ac` |
| `origin/feat/ux-guided-gpio-connections-20261018` | `668bad33af3abbadb1ac398dc3f8dea48e5aa210` | 120 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `445eae320519e92fac1f93a2348897de12c06a4c` |
| `origin/feat/ux-module-authority-20261018` | `0b45a9e66f0f405b2ccf780a8423be6d34ce8f3c` | 120 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `e1885ef05be5c4dac958c3b3be899aaa301530e8` |
| `origin/feat/ux-navigation-hardware-studio-20261017` | `a5b6cf239887d5eaa58ed9e3e76eaab1f5797bac` | 121 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `98681fcd5907169bc5f77221a578a86d4fecbcf9` |
| `origin/feat/v17-dynamic-module-workspace-20261016` | `800d1807f975af7b7be6dee8a13bfd1a58618915` | 158 | 22 | 0 | 22 | S: aggregate patch matches base-history commit | `a620378c321eebccea8ab2c668c9b4c88e54eebb` |
| `origin/feat/v17-dynamic-wiring-preview-compose-20261016` | `1e96272d3cab8436177969c28241ecb2fff63e66` | 155 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `e5580f9900de06f67c4ab41e18267683b7f2403a` |
| `origin/feat/v17-firmware-authorized-wiring-transaction-20261017` | `3376f021456200a5ac8f6b3f62d886f8aebf063d` | 149 | 25 | 0 | 25 | D: divergent; semantic review needed |  |
| `origin/feat/v17-hardware-dashboard-sections-20261016` | `6ada94cfda02d11e802f885c5a8491b7ee9a1b77` | 157 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `1b69f6849c7521b23c2973793b683fa8ff29404c` |
| `origin/feat/v17-home-compact-reconciled-20261017` | `3e1a7d7af86c573ffc64c6b14429327f8d5579ab` | 133 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `d36c37a4f3055c07fe76a160866e5b1e1c617075` |
| `origin/feat/v17-home-reference-cleanup-20261017` | `0ffaa52711352e57ee0eaf1c749e3610bf3187c7` | 136 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/feat/v17-real-wifi-scan-ui-20261017` | `172a050882fa0e4659475a50eaa10d38be6535f9` | 133 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `9dc089be142830ef4a70dc05456aeeb4ee24f519` |
| `origin/feat/v17-restore-advanced-esp32-lab-20261016` | `a8e250842bad00f636a22846321fd397447dc611` | 155 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `2f953d215cbd82d53416a04ae3f3a13d2842a734` |
| `origin/feat/v17-verified-rgb-gpio-reassignment-20261017` | `63348818a3e2b69fa11566705572ae0514415316` | 132 | 14 | 0 | 14 | S: aggregate patch matches base-history commit | `00dc0dd28e8183c6358a0bafee43925767e2907b` |
| `origin/feat/v18-authorized-runtime-gpio-apply-20261017` | `8de08d4eb9b57c14f8c8a9c5338e001689b1712d` | 149 | 8 | 0 | 8 | S: aggregate patch matches base-history commit | `8ca13e61611d7d77b955eff558d206859a4765e9` |
| `origin/feat/v18-compi-ui-connection-modules-polish-20261017` | `f7ca525aa3389b8ef5657a2f048fe08994aaf93f` | 136 | 10 | 0 | 10 | D: divergent; semantic review needed |  |
| `origin/feat/v18-home-module-experience-20261017` | `31a961bc1d93504dc656e8ed131b594efa0b2ad9` | 148 | 7 | 0 | 7 | S: aggregate patch matches base-history commit | `e81764e5252b485d9dcf7e8db9b53a3deb171ccd` |
| `origin/feat/v18-modular-ux-composed-panels-20261017` | `db6b3a237050e7c8beb28ad0b18cec1a6c9b2a62` | 148 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `ada48597aeaf1e35462d1953244e3ebee4771bf6` |
| `origin/feat/v18-module-cards-state-ux-20261017` | `7058466997a07b9375f0b48755d7d9bd4d1af6ba` | 142 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `7caa7c2427afc4125808d398c54cf4b94c07438f` |
| `origin/feat/v18-replay-safe-component-apply-20261017` | `706db23cc6fd060c231d90e0a1570bc62fbff863` | 142 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `95191f17163a271ecf6cd4da66d85c3b41b4d4d4` |
| `origin/feat/v18-runtime-module-activation-20261017` | `66cfb013f61c979a976290852f45447fb0083b80` | 147 | 8 | 0 | 8 | D: divergent; semantic review needed |  |
| `origin/feat/v26-ldr-light-sensor` | `64a98ecddd06b725f7c769568cff1248caaf2a7a` | 111 | 19 | 0 | 19 | S: aggregate patch matches base-history commit | `f7b63ae4c7dff95dba78a75e80764db4dc2aaffd` |
| `origin/feat/v27-android-environmental-speech-20261020` | `3dcb96492e6900c53d13d6cdbc067679395a5610` | 110 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `95aa0e67f072b0c846dca85c2ebda751ae2bbe8a` |
| `origin/feat/v29-ambient-rgb-policy-foundation-20261020` | `daac44c4c3a7a1cf23f8e981877d08f28cf3ca66` | 111 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `3e23eda5503167f5db9681a1bf1737da3eec9d73` |
| `origin/feat/v29-ambient-rgb-runtime-20261020` | `570059bc7a8251678070ae610a4ef7829bc4548b` | 109 | 8 | 0 | 8 | S: aggregate patch matches base-history commit | `a61b878cd4ba753c4fbdf864d37e79aac0d9c0cc` |
| `origin/feat/v3-adc-telemetry` | `1a5f09edaea77a93af664d23f36c2764579dfe15` | 275 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `2e254494a6f3ad2c1d11fca9812ccad6174d9ed8` |
| `origin/feat/v3-board-pinout-and-safe-gpio` | `4f7b7194dc4f4f0b9ce5eabaf69af665339fbedb` | 279 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `b31a6e7dc06f91b24ee36bb8a14af3103ab2fe98` |
| `origin/feat/v3-device-diagnostics` | `30336cbca06e69bc234408071f3a0a3d3fdc0e73` | 274 | 6 | 0 | 6 | D: divergent; semantic review needed |  |
| `origin/feat/v3-local-experiment-library` | `99814cfe151fe560cd8ebeddf6696f56be019ed1` | 281 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `fd8e968df974cc1b0879f6dcafca2bd5f82d3ba0` |
| `origin/feat/v3-local-projects` | `bb66421aee88d97ed9627db0ddc65efe9167b656` | 273 | 4 | 0 | 4 | D: divergent; semantic review needed |  |
| `origin/feat/v3-rgb-wifi-control-parity` | `aae5ddf050b4a95ec9434213b04ef2fd948eafc1` | 277 | 7 | 0 | 7 | S: aggregate patch matches base-history commit | `8165d2a4324af2841d1e2e07128dc5f0fde21128` |
| `origin/feat/v3-test-diagnostics-and-readback` | `c4abb6c8ee8bbd906763169f7a692313d3dd23aa` | 269 | 10 | 0 | 10 | D: divergent; semantic review needed |  |
| `origin/feat/v30-ble-environment-control-20261020` | `e6351c4b8c506f600b7339db7c0ef3e7581c0c67` | 107 | 12 | 0 | 12 | S: aggregate patch matches base-history commit | `59c199175c2236753b252d43f7be33ad81da0a96` |
| `origin/feat/v31-ble-sensor-snapshot-20261020` | `8e047b95d110df8328e5c3c56f649d90379dab1d` | 106 | 14 | 0 | 14 | S: aggregate patch matches base-history commit | `23cc449fb62d787818eb154f6fa73e7cb6e0c0eb` |
| `origin/feat/v32-ble-ldr-calibration-20261020` | `af478d6e49d01462d79a31a9b0d64c08e507f34e` | 106 | 23 | 0 | 23 | D: divergent; semantic review needed |  |
| `origin/feat/v32-ble-ldr-calibration-main-20261020` | `ee443fd920de766a5dbd49874c7fa7a8d10cc7d1` | 105 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `2d4008bd87f41f56400251a220d58b12c17190ce` |
| `origin/feat/v33-android-local-audio-library-20261020` | `cdd32924feb51756d29b16d99f4200f6422d78f0` | 103 | 7 | 0 | 6 | S: aggregate patch matches base-history commit | `002a472b005f2d5447167e5456679d6a633c99f2` |
| `origin/feat/v35-ble-sensor-gpio-cas-20261020` | `8b0eba4717967fe053ecad836e33d1278fc72c59` | 103 | 5 | 0 | 5 | D: divergent; semantic review needed |  |
| `origin/feat/v4-android-identity-readiness` | `c4b61ca0e723827a79c25d9cadfa3f2770ca6d76` | 261 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `108d4db646edfd56891b315c4fd755d60297e890` |
| `origin/feat/v4-android-rgb-script-execution` | `3d40bbf2134682f60f8e2bfb726a7038a1bb326e` | 252 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/feat/v4-arduino-cli-offline-build` | `eb9c946c40d1042029abee94f8587ccdc0b1774b` | 253 | 2 | 0 | 2 | D: divergent; semantic review needed |  |
| `origin/feat/v4-arduino-cli-usb-workflow` | `f5170836a7d1f643bfd551dfcee88c57746de08c` | 253 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `5a8f6db817d42812359f334c3e1db6c2d477270e` |
| `origin/feat/v4-autonomous-rgb-persistence` | `2849f80e2e1e9d10fa5ec40c519f0c02de5a3229` | 260 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `4340c5239370519b4b704b14d8ad7ead716b02ff` |
| `origin/feat/v4-ble-public-identity` | `b847b67d49eb6ff411571abf6e9f43085bd2d9d2` | 258 | 11 | 0 | 11 | S: aggregate patch matches base-history commit | `efef1c91f456ea5fbfad43367b6628d64acfc32e` |
| `origin/feat/v4-code-project-persistence` | `06395f1f3a5803ecbb183a677c4eda8952124f80` | 248 | 9 | 0 | 8 | S: aggregate patch matches base-history commit | `359f5b266a066ed854d8b1378a42ff55f44df71d` |
| `origin/feat/v4-device-identity-foundation` | `d5f1ef2dfd9c1981a84bee1de90c6e545ff9ade1` | 261 | 8 | 0 | 8 | S: aggregate patch matches base-history commit | `ea9b1954a12393f017c10eda8cc8f838627b7b0a` |
| `origin/feat/v4-editor-gpio-script-execution` | `dd94c483da0e6c5ffd84216adadb7174b3813128` | 254 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `4bc8bf449938b90406d5d76fbd6a7190018c438e` |
| `origin/feat/v4-import-private-lab-label` | `c4984bb1e75adf60f22e6ab9ada127db408a28a6` | 248 | 3 | 0 | 3 | D: divergent; semantic review needed |  |
| `origin/feat/v4-modular-led-code-studio` | `54779cb53395ec14587503601b90f325dc8db783` | 257 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `057d43d505f0ed4c6e78a3275fc7336f2f3c40c2` |
| `origin/feat/v4-physical-secure-provisioning` | `044ae000e0d6ddc678f240e9ca1dede57bec2648` | 252 | 14 | 0 | 14 | D: divergent; semantic review needed |  |
| `origin/feat/v4-program-editor-master-plan` | `c60da3ff3037fea5d057d1c1cfc815959c314982` | 260 | 7 | 0 | 7 | S: aggregate patch matches base-history commit | `d94f16e017a763ba030578d7176aedc6bd756e48` |
| `origin/feat/v4-rgb-script-runtime` | `45b2096f50ac2301bb50f67e20d2c32943517073` | 256 | 7 | 0 | 7 | S: aggregate patch matches base-history commit | `89921c90e8cd8b06fde1690b8d40c03197c8830e` |
| `origin/feat/v4-unified-code-projects` | `02f4cf821feb8cfe9d644ed22770ffcb82fbb9fa` | 249 | 7 | 0 | 7 | D: divergent; semantic review needed |  |
| `origin/feat/v4-unified-gpio-rgb-script-ui` | `cf5647f0d429ca8c7a6d377eb040c0786ee10c3c` | 252 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `f3419ae36afcca78d63c8efad8715afbd03b70c6` |
| `origin/feat/v5-arduino-cli-build-package` | `1702190dd0458c44aac1bcdf2730a6e9df639519` | 253 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `a569e6ca676af07709dcab92975a818501ba56e4` |
| `origin/feat/v5-code-studio-unsaved-guard` | `ed99fd93f990d6d56c58e733fcaced5750a68c4b` | 249 | 9 | 0 | 9 | D: divergent; semantic review needed |  |
| `origin/feat/v5-editor-library-userflow` | `63e23b7f1b3f80d4301e2e0f964c0b8e7b639b89` | 252 | 11 | 0 | 11 | D: divergent; semantic review needed |  |
| `origin/feat/v5-editor-project-library` | `5aa71ebe0f490867e2ece751d31eae4dc36171a2` | 252 | 3 | 0 | 3 | D: divergent; semantic review needed |  |
| `origin/feat/v5-project-library-ui` | `165559e4828e337dc34a5c870de54be814ac33d8` | 249 | 4 | 0 | 4 | D: divergent; semantic review needed |  |
| `origin/feature/rgb-controller-master-plan` | `39c78d4cbe86a591c0b713302130038b30d4950d` | 303 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/android-ci-ble-rgb-integration` | `4abbd91534f2c1a110625234dc4592653a904ee2` | 289 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/ble-connect-failure-recovery-20261022` | `9a2cbdcc11e84861afde0e32870125ccddc6973e` | 71 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `f18b9fef735ca79e7eedbf03a9727f90f08b2a51` |
| `origin/fix/ble-owner-hex-pairing-v24-20261019` | `b8f64c973abc289aa16ee34528ba86e697d80fc3` | 112 | 11 | 0 | 11 | S: aggregate patch matches base-history commit | `6e6e0a6721440f4e52c6652c429290430d92515a` |
| `origin/fix/ble-owner-rgb-authorization-20261018` | `0176b72052c1cf62234a7757d397db32f9177203` | 123 | 11 | 0 | 11 | S: aggregate patch matches base-history commit | `1905de4f53ea45208642a9f771e5aa0bd57dcc83` |
| `origin/fix/ble-owner-rgb-navigation-20261017` | `135120271d2add4f0ea0d1a34793ba1977846b6b` | 123 | 14 | 0 | 14 | D: divergent; semantic review needed |  |
| `origin/fix/ble-snapshot-busy-recovery-20261021` | `50677579c2d7df394a79b0fb5ff990600436ff3e` | 96 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `e7bf62a99e162920ce5b745792fbcd1d3e454f6b` |
| `origin/fix/compi-audio-controls-honest-state-20261022` | `4e60b55252ba47c6264cfd26c4f51a411dc6245d` | 17 | 2 | 0 | 2 | D: divergent; semantic review needed |  |
| `origin/fix/compi-audio-navigation-robustness-20261022` | `bf8fa515e25e99090cd6a4964525e932a3d65515` | 77 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `d8253cd5194e03d1d2b1e2cf1080a3ff75d5a3e3` |
| `origin/fix/compi-audio-playback-safety-20261022` | `508ae19f56ed5c355a07b7ddafe1064eec7688c8` | 76 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `cb4070c81c6ea6019c8b5551c8b9651d1224ec08` |
| `origin/fix/compi-audio-voice-switch-20261022` | `cbbce8b66de69515cf49543732227d9f0734c75c` | 7 | 5 | 0 | 5 | D: divergent; semantic review needed |  |
| `origin/fix/compi-bounded-queue-tests-20261022` | `8f0c45ff23a24212f6ed89bc52d22b6ec8b66380` | 75 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `2116540dbb4867c5c70ad6b27f6fd1d34ef4479b` |
| `origin/fix/compi-ci-uart-rgb-20261022` | `ae175bca27fd69dca28510eda08a8026a1279fa6` | 33 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `50fd880aaea1a8bad2a672f695c798bc1a9a4db9` |
| `origin/fix/compi-complete-module-discovery-20261021` | `627a994990d1f2915910045923c7da19bbf002aa` | 96 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `9f95c02a6df20297fa93cfd240e6163388d1498e` |
| `origin/fix/compi-connection-multiarea-audit-20261022` | `8c5f098f9cc1eb77e7e38f42d508a391074adee9` | 66 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `33933530107b641e19e18e8cb32162cd7a2e219e` |
| `origin/fix/compi-dfplayer-deterministic-mp3-folder-20261022` | `69ec69473ee2eacc840dfe4ae91f7231c72f20c8` | 81 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `04f1946e825a7501cbf1cc883f3d5f115b550405` |
| `origin/fix/compi-emulator-navigation-current-ui-20261022` | `20fb82f058b3c52306888e92d06fab2f5b099d5e` | 15 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-full-firmware-five-temp-bands-20261022` | `3e6599b43c61cf9741b4ae97495b817946f52fae` | 63 | 3 | 0 | 3 | D: divergent; semantic review needed |  |
| `origin/fix/compi-home-navigation-20261011` | `8454b4055ab733427c9e0a767e2fb4ed5063eb8e` | 240 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `cafd6f619b78c1a20bd2e7d49ac80378920201db` |
| `origin/fix/compi-home-navigation-root-state-20261022` | `a859b740be1621b91390bfb4fece6a3e3580b998` | 16 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-inflight-results-20261022` | `3e774d8a4b76784dc08fc2b5c549ae8c5827cf52` | 64 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `cd4af1ab78ddcc57502054f7af17afac25787b70` |
| `origin/fix/compi-ldr-gpio32-20261021` | `3b3cc44ccda2aba7a71cc2317319a298090ad521` | 99 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `a22145e0786b39347e0a4aae174453c3ebe4261a` |
| `origin/fix/compi-ldr-wiring-ui-20261021` | `fd7c01ac6ba67a5adb3063b1d4a05fc07cc39601` | 95 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `693bbed18b5edac39e42e00829dc1c96e8af1c89` |
| `origin/fix/compi-led-nested-scroll-crash` | `6310fe0bf34629fe65613196cf90d9a5c74977e4` | 215 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `6d1fc491ba44a96cd0714da78672532f669923d4` |
| `origin/fix/compi-legacy-led-route-20261022` | `cc909a0a8c9071aab747cd4a7f00ce93d34c9785` | 59 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `14ab3e25690beaf16f8f0f81aa8f938d1b6fd8f7` |
| `origin/fix/compi-live-dht-after-ui-merge-20261022` | `2e3907ae187a5e5ec61ff8ec2f575b6c44b514e8` | 69 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `85ea513dce8326d8cfe9142a16c92323245b3285` |
| `origin/fix/compi-modules-oled-consolidation-20261022` | `85063caeb797b0768e0dd8fe162d777cb8842a62` | 77 | 7 | 0 | 7 | D: divergent; semantic review needed |  |
| `origin/fix/compi-navigation-module-cleanup-20261022` | `9934c111c2c87e82a2f5f434c85b3201b2d85f6b` | 78 | 2 | 0 | 2 | D: divergent; semantic review needed |  |
| `origin/fix/compi-navigation-regression-20261022` | `a2b58ec4d7833fb855620b325188899ea0f0249d` | 77 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `63d591baaa58a64c62e5d2bb4764c0f31279fae9` |
| `origin/fix/compi-oled-ble-load-20261022` | `59c376ebca889541eab71b3bdc99d73ab6a81454` | 83 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `eed532d030eb15d4e57c0521b01ca8be459e8c9a` |
| `origin/fix/compi-oled-contrast-contract-20261022` | `48bac937d1f89334bd2199278a6418e0d0000467` | 54 | 3 | 0 | 3 | D: divergent; semantic review needed |  |
| `origin/fix/compi-oled-dance-http-contract-20261022` | `d4ea37959c824d4f02e86da3bf5c9b5c29c27a0a` | 47 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `3b72f87ebd82a705f2e892a63efa759fe6de0394` |
| `origin/fix/compi-oled-detail-cleanup-20261022` | `04de09481d63c94e4c273dcb94d482cb30f99687` | 61 | 2 | 1 | 1 | M: mixed; inspect unmatched patches |  |
| `origin/fix/compi-oled-freshness-voice-timing-20261022` | `083614ae271b11128ab671f08e02621281436401` | 67 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `675db01ff51be5ca0be85cc2ea1968a5ec81ce06` |
| `origin/fix/compi-oled-no-banner-20261022` | `d22412bbf6779ef59f4ab9aac684caf82d4bc037` | 59 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `e0d03019011ca86b1d132bd9c115c40e3dea0573` |
| `origin/fix/compi-operations-invalidation-20261022` | `e4be360b0f93003d2095df2a0c810d09e5e15e68` | 65 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `fbd790eaf2efeddfeb5a2e53c515f6283f004552` |
| `origin/fix/compi-premium-rebase-20261022` | `ad462bbe12cc91c2ffd7407340c7325d7f35d2a6` | 57 | 3 | 0 | 3 | D: divergent; semantic review needed |  |
| `origin/fix/compi-qa-ux-logic-20261021` | `b30e0983f5e62fadad64e0fdbabae26f85a2e13c` | 102 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `d19f97b38ac6cb84f2ff576283e2b196cbc1981c` |
| `origin/fix/compi-rgb-hysteresis-regression-20261022` | `aead388c7c6a50efe41485db92b4d9fc14f9549c` | 40 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/fix/compi-sensor-freshness-ux-20261022` | `c7478fabc6664456a81d0b730e7f2d96d98e1db7` | 70 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/fix/compi-session-epoch-reliability-20261022` | `f38238b006993919e901b19ba39c19fae96a8bdb` | 69 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `c215540ba4218347ba82ffc2f84a9c2efa7dd538` |
| `origin/fix/compi-stale-queued-commands-20261022` | `b6a15a374d900cc7d29d3e8d11dd83c33d9887ef` | 67 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `87f754fd7d70154827fe7932b04f1f39fea4d513` |
| `origin/fix/compi-station-cleanup-controls-20261022` | `0866891a381ba33583c98a90dcfde01fa37267a4` | 53 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `fee1fbaf6bd0355f53053673819490f619fd309c` |
| `origin/fix/compi-station-fresh-telemetry-20261022` | `5369c96100b7ae38b89980f00ac206a928b035e0` | 51 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `f430c14ac4c1ce5b9ce7406ed7c44bba516009f2` |
| `origin/fix/compi-station-ux-telemetry-20261023` | `808f2c8ff7a77e2351de788b42c859d28140a777` | 2 | 8 | 0 | 8 | D: divergent; semantic review needed |  |
| `origin/fix/compi-status-poll-provenance-20261022` | `745d101b42e239f46cfe170197176f734d7b3fda` | 93 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `233e0af4e7bbea7592a8b652003cc0ccecd0ac7a` |
| `origin/fix/compi-telemetry-freshness-model-20261021` | `5fa718766773a7be1ab2f03ffc0311d699e44c2f` | 94 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `9e1b220917cdedfafeab27458aaac973588c4933` |
| `origin/fix/compi-v13-firmware-runtime-stability` | `8f2701afb1cc879be90f74f00c4d41a17ad1453f` | 233 | 10 | 0 | 10 | S: aggregate patch matches base-history commit | `53052544735d0f64963c58c8a191ae3c6c31e7e0` |
| `origin/fix/compi-v14-android-network-navigation` | `6b38ed6cf9d0fc80f57a2f28c1a3bf9302dc410b` | 210 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-v14-real-stability` | `6c9038821b0b3c39e47f07d66b804465a93927a3` | 212 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-v14-runtime-and-security` | `63e41fef7c5bca7acc815671778b0db7bf0d6cef` | 208 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-v15-arbiter-identity-reconciliation` | `fb0edf30951bd2bd7ee56bf9073b63592a8e8229` | 199 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-v15-ble-auth-reconciliation` | `86616388b75266411901d29e76f6e95ad5bdacd9` | 195 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-v15-dynamic-ble-auth-ci` | `32c68179c1a9a52ab0cc927a4cb7bc801c867799` | 197 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-v15-strict-ble-auth-mapping` | `9ce882eee6a0817b891bd57d8d1e48a713426bb4` | 193 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-v15-typed-errors-runtime` | `988a5e03cd9698433aaee7c568ee9642a09ccac5` | 205 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-v18-ble-advertising-preferred-interval` | `fc1883ccf12b95993de0dbd49d06e0e36c71f847` | 141 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `5d9a1c556d6f8db38a0c67487bcaac21ff348e2a` |
| `origin/fix/compi-v18-ci-coverage-docs-reconciled` | `c4f1706bfef92a6d8abaef42979a0c0142b0a8c3` | 141 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `e75ddb73524fb49b13542c82fb2176ba41e9b1b7` |
| `origin/fix/compi-v18-ci-host-coverage-docs-20261017` | `d7566677c05e2d7b1b08452f3a7c464c111af7a1` | 142 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `e75ddb73524fb49b13542c82fb2176ba41e9b1b7` |
| `origin/fix/compi-v18-rgb-effect-overflow-rebased` | `76906b8c7718427ced27d7a135b02e09e21d80cb` | 143 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `9201432748e289da431469f2ce99188a2588fec5` |
| `origin/fix/compi-v18-rgb-effect-uptime-overflow` | `68e5753628313c3fd5e40d42974972a6249bc416` | 144 | 1 | 0 | 1 | D: divergent; semantic review needed |  |
| `origin/fix/compi-v18-wifi-key-comparison` | `f4bb2499bc5d5c0b0901e0d60f4d56b6693a470f` | 135 | 2 | 0 | 2 | D: divergent; semantic review needed |  |
| `origin/fix/compi-v18-wifi-key-comparison-reconciled` | `02ef4e024f9b1e3489cbe844f11be9f2b47b7c72` | 134 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `767448c7792c66e563039736497a071807783672` |
| `origin/fix/compi-v8-reliability-audit` | `465639a7703912f7a4345050d304518f9ddb3425` | 238 | 41 | 0 | 41 | S: aggregate patch matches base-history commit | `18ecf4bb3efc14bea2ba5a43403f2c187dd8b494` |
| `origin/fix/compi-v9-home-density-navigation` | `cb829dfa649d11304d822f5a361f0d0e6d2fa9b9` | 214 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `9fd5d171c5719aef4aa150eceece85b0279b4059` |
| `origin/fix/compi-v9-navigation-studio-gpio-telemetry` | `4c77e8af929b9d5d1a94d324a7681584ce16a99e` | 237 | 35 | 0 | 35 | S: aggregate patch matches base-history commit | `6309235c8651a6c5494035551552f110068c682a` |
| `origin/fix/compi-voice-3s-timing-20261022` | `99cf5e876d5fa8b3675380b35dfdc9a90b6142ac` | 47 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/compi-voice-sensor-validation-20261022` | `9cde51e70089f82b3429618dd71f5e90e62bf9e3` | 24 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/dfplayer-transport-result-20261022` | `538e6ec16f21aba836f336d7bb1755ebd9ff14a6` | 16 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/dht11-gpio16-signal-and-truthful-module-states-20261018` | `4545727408993f95413b0f53de9d4ddedd50dc72` | 116 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `429ded931869f66c2ce5e9bc609cd17ced202eb1` |
| `origin/fix/dht11-last-valid-reading-20261023` | `f76452ee357236ca38041d503c5edb135ebabe10` | 1 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/dht11-reuse-proven-probe-full-firmware-20261018` | `f698990149779c3efac141daaf8595b3495d6097` | 114 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `0b254944268ca498dbb155b6ea6262fe078e3193` |
| `origin/fix/estacion-compi-voice-cycle-20261022` | `e40806e2eb62e707e3c3d7f5bd056e28161546cf` | 81 | 3 | 0 | 3 | D: divergent; semantic review needed |  |
| `origin/fix/hardware-lab-scroll-and-gpio-ux-20261016` | `6073983a01645b612290b936bf7eac8ae3e9892f` | 159 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `7351b05f989b18cc4d0fa5b5ad40e609c5f39185` |
| `origin/fix/ldr-gpio32-boot-20261021` | `5d3b69e693833ed10ab56ddfaaa9ff3f9fbd19cb` | 90 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/recover-main-project-tree-20261022` | `3e2c616a54558919d7ea4371d64206e3a946f6af` | 42 | 11 | 0 | 11 | D: divergent; semantic review needed |  |
| `origin/fix/restore-compi-project-20261022` | `ec4eec3007b2b7945d0117bb96fbc2006735d5d0` | 41 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/fix/softap-first-boot-nvs-20261017` | `b723f8fde1934cefbe6e28cb60d9db2d429c614f` | 136 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `d377c3eb5368fd0b4811d46ec866ec49f293e0b3` |
| `origin/fix/softap-startup-stability-20261017` | `21d5163d21a6da9accc76b0383c5f904f58aff70` | 125 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `c0cc954c7bc433e06e8daab62c1eec57afc100d8` |
| `origin/fix/station-live-badge-accuracy-20261022` | `a5cd6967949459e5539bb6942cea1b7134f7a5ed` | 49 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `2a5f8b38a0179b27252285c4e06a1b1979c7a595` |
| `origin/fix/temperature-rgb-five-bands-20261022` | `161796340115e36cdd5f12a57d43c88f2088409f` | 42 | 4 | 0 | 4 | D: divergent; semantic review needed |  |
| `origin/fix/v16-ble-discovery-build-20261017` | `225e20c0e143e91fb57a1d8eb74ba46f60cd45b1` | 149 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `44e48d9e2cfddc48a148a006ffad00bc6a9a6861` |
| `origin/fix/v17-activate-inactive-module-cas-20261017` | `d201206437d2478e3131e8319ef19d123aa0f4aa` | 136 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `57c47f10985f69e57fc866712d8d1b00982194e0` |
| `origin/fix/v17-authoritative-empty-inactive-gpio-20261017` | `7898b4807cc3ba676cd4296183ca6de712f6be6b` | 129 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `b1861371b8146364e217e7371a0dd0fc7f13f908` |
| `origin/fix/v17-f09-board-pin-remap-and-compatibility-20261016` | `8165066feef265af4ef336e8ada621eb41a3b0ab` | 157 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `9c6752b30ca8c7dce04f5a9ccd7a4bf9e9331c19` |
| `origin/fix/v17-module-compose-balanced-conditions-20261017` | `092f22691a241ce994c90e3310ba905a95bdc4fa` | 153 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `96c8c6bcb5d5c33c8f91b33922cd7d05c860afb8` |
| `origin/fix/v17-optional-modules-dont-block-softap-20261017` | `009f003cf498c1d1ea6f2802bb8e2e8b3f53d02c` | 135 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `ef32e4d8a6399fd9457b93b8abd5196c4d490526` |
| `origin/fix/v17-soil-live-calibration-guard-20261017` | `5a9f7c637aa3036f452ca55ee919684d61963063` | 151 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `41e8264eca2c81767ff7b9fe97e24bc7d97cef91` |
| `origin/fix/v18-catalog-presence-authority-20261017` | `6a6e3a621033f011eadb36408d6a76ba515b6a97` | 145 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `a3372d5e0e23b9434d3ed5e81a9a84d283d515e9` |
| `origin/fix/v18-gpio-unconfirmed-response-20261017` | `31d490109299e8365957d6c25f54aa8bf6eb90d4` | 138 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `3bff0bc78a036e6dcd0f8a4d5000ff90dc7dd64a` |
| `origin/fix/v18-soil-calibration-on-rewire-20261017` | `6d6ff4d1297eb0e3c463bde42891ef2d8ee220aa` | 141 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `728c9d9aa20159df1a8831f7848670b018e63f45` |
| `origin/fix/v19-ble-discovery-rebased-20261017` | `a1cceba8f14e6f81ce2269d1f3da87f9bec7785b` | 133 | 10 | 0 | 10 | D: divergent; semantic review needed |  |
| `origin/fix/v19-ble-discovery-recovery-20261017` | `6e3f71166700071db9de0a6abb4816470cd9f6fa` | 136 | 5 | 0 | 5 | D: divergent; semantic review needed |  |
| `origin/fix/v23-module-truthful-states-and-clean-telemetry-ui-20261018` | `f780c0df857e6e47d681e04040b4ed49a195b412` | 113 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `de936a011012f13d20e141d193caffc546a83af9` |
| `origin/fix/v3-ble-write-watchdog` | `1e15535f6cd4ddf6861224725f5c6a87c09fbd6a` | 269 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `a77484d97a40702ba76112ff2ae57049288ef37a` |
| `origin/fix/v3-experiment-time-accuracy` | `83653c4ad71a545f17773e302737ac3c85d59370` | 266 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `a848d621b82c0b9bc6bbdb7090d3456b44912360` |
| `origin/fix/v3-softap-wifi-routing` | `bd508199f50ec5abdb1cec4270e416d5ba48a8e6` | 269 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `566f9d75ab0c9b133d6d0b7c31604e79eb3fe460` |
| `origin/fix/v3-strict-android-lint` | `956b5cc4cbbb604e6c89d7ad5f460d2acb173332` | 273 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `67e4bdfa37cebba8cdfb88a15738fa9695d957c5` |
| `origin/fix/v3-unified-board-profile` | `426732a02a936b2775627db519c3a12bbf78502e` | 281 | 7 | 0 | 7 | S: aggregate patch matches base-history commit | `86298d2985fbb644e249de0c43f3691a61f2b8b9` |
| `origin/fix/v33-audio-playback-state-audit-20261021` | `20d74e94d74596e47c68ef5e6f539a3f993112fb` | 102 | 1 | 1 | 0 | S: aggregate patch matches base-history commit | `60e2a89a55d0ea3884f90708ab21a7700e37b966` |
| `origin/fix/v34-ldr-dht11-gpio-idempotency-20261020` | `62b05efb5550b8df49a60f29cc47be338b31e4eb` | 104 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `5e081b29f1f5a92a17c4ffc8003bc0d6ed629f26` |
| `origin/fix/v5-code-studio-final-consolidation` | `33f7673492be1305875585ec1c53d42b1d2e1823` | 247 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `4dbdedc9e3a259a41ace8e0bef4c832a31db0a78` |
| `origin/fix/v5-unsaved-guard-on-current-main` | `03b2caa171df148599a4e7fd675f72f5c3f25403` | 247 | 2 | 0 | 2 | S: aggregate patch matches base-history commit | `4dbdedc9e3a259a41ace8e0bef4c832a31db0a78` |
| `origin/fix/windows-arduino-cli-discovery` | `8e69f42d5c3746c32209fed40c5e48e6638c0177` | 262 | 6 | 0 | 6 | S: aggregate patch matches base-history commit | `e175bddfb3319f1511fc135368d3e8a6a9db3f89` |
| `origin/fix/windows-board-profile-digit-keys` | `eaa13cf7263ebb9ebb6f198a94d9a755b9a9e40f` | 263 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `82d296337028a95ec84a6de1f062dd07bde3e2fc` |
| `origin/main` | `eccdd22fa049fea65fc31254cd5c28c508241e46` | 0 | 0 | 0 | 0 | A: ancestor/included |  |
| `origin/refactor/v3-rgb-command-codec` | `c6b7b4c5d20fe479ca89445f6da4d7bf10fb326d` | 281 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `b0e124109002e5ab766b2f2c1bf8627f3882121f` |
| `origin/security/v3-disable-unverified-release` | `753750cb32b779a79c38d885dab67ad927486aa2` | 276 | 4 | 0 | 4 | S: aggregate patch matches base-history commit | `f7c3251e05462801cef44e7586dd0ab29782bfad` |
| `origin/style/v19-clear-module-studio-hardware-20261017` | `22e29d1ba4c6f914aa28817c961acd0d90b51690` | 133 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `e399683501ca723382fee6796326bd69d3efe43f` |
| `origin/test/v17-mobile-ux-api30-api35-20261017` | `9b832b97f13f3f3308de9f0a2ce7dced10e3782e` | 151 | 3 | 0 | 3 | S: aggregate patch matches base-history commit | `0d7fa0767238f00e9738d1b4442ab22d488e5541` |
| `origin/test/v17-real-android-emulator-regression-20261016` | `3951533aeb277e97effa8bd64bb1254e6235923a` | 155 | 13 | 0 | 13 | D: divergent; semantic review needed |  |
| `origin/test/v4-provisioning-compatibility-probe` | `85ec8f6ecf1fd4276c2285fa66c1281e579686ad` | 259 | 5 | 0 | 5 | S: aggregate patch matches base-history commit | `0609453f41fcbbb45068efed8a113d2a9ffa8106` |

## Unmatched patch details

For branches with unmatched patches, these are all cherry `+` commits (SHA and subject); ancestral branches have none. They remain candidates until checked against current source and later integrations.

### origin/chore/compi-cleanup-active-paths-20261022

- `656473c5a78749075e456e8f753accd83b770fe4` chore(firmware): remove unused DFPlayer driver include from active sketch
- `55afc268bac27403f448dd517b5d73541c54a899` docs(compi): audit active drivers, pin map and deduplication acceptance gates

### origin/ci/v3-firmware-size-budget

- `afce23a532355b3cc55d6c1c63c36c896472c68e` test(firmware): enforce dual OTA slot space and safety margin
- `6882d0da3787c997b606eba914bb309650d388ea` ci: gate 4 MB firmware build on real binary OTA budget
- `5a9134fa6dfccdea9aa3b5676acdb77f52440920` docs: explain 4 MB OTA slot size and safe build headroom gate

### origin/ci/v3-hardware-feedback-artifacts

- `2e121611f5afc43650e536687b854dad908f9123` ci(qa): publish reproducible debug APK and ESP32 firmware artifacts with checksums
- `4797cac7b77ee700928b2db7a9b05f92be32bdbf` docs(qa): executable Android/ESP32 test matrix and safe feedback format

### origin/docs/v3-field-testing-handoff

- `852b41e9d338dc6957f4427ff62522fe6125d99f` docs(qa): record PR34–38 and exact physical test handoff
- `d3ca187e5775f20e884c243d24c93483d8703d36` docs(readme): expose real test download and feedback workflow

### origin/docs/v3-verified-integration-status

- `41789c2d4adc34dc6ace8e0ee7040fcae66f78f0` docs(v3): record merged lab features and precise F00–F16 blockers
- `14fbda6e5dc1a43d5ac4bfe726b422a37aa27d6f` docs(readme): link current V3 integrated lab status and outstanding security gates

### origin/download/compi-arduino-full-lab-20261022

- `a1adb79278a7ef2556f2c8054f94d2b28d3d083c` chore(arduino): enable complete Compi Station laboratory profile in downloadable source branch

### origin/download/compi-full-arduino-20261022-final

- `20036a3abf822b00ac9df9b110dc3e80d7f41220` build(arduino): package latest full Compi laboratory firmware with enabled sensors OLED and DFPlayer

### origin/feat/compi-automation-scheduler

- `059466f4fcaaedb0059b90a469f633f5f44a5ce3` feat(compi): add bounded RGB automation clock and host test
- `c091023be10927047acf27b0322731a5970166b4` feat(compi): add bounded RGB automation clock and host test

### origin/feat/compi-dfplayer-environmental-voice-20261021

- `86efd0f0bdc13c249fe8ecc866aef65dd47e9100` feat(audio): hysteresis and cooldown policy for real sensor voice cues
- `b8c253a2f0099a66d2c96a4b685d922ce199cd3e` feat(firmware): opt-in UART2 DFPlayer using live DHT11 and LDR cues
- `cf8da18df25e5e75c897aaf3555f0a00ef2ef8d6` fix(dfplayer): declare serial transport before setup initialization
- `28ebeeb664b3e02406ae7cd5d16f7b9c64e8e893` docs(audio): safe DFPlayer wiring, SD tracks and physical QA

### origin/feat/compi-dfplayer-visible-module-20261022

- `898ef09fd36cccedf30f5d99dd9ca85f1ee35e89` feat(android): visible dedicated DFPlayer Mini hardware module and honest playback status
- `cbadb9756ad17db8169711776fa6a927500d7f74` fix(dfplayer): hide unsupported GPIO editor for read-only hardware information

### origin/feat/compi-humidity-aware-rgb-20261022

- `9282de6dacd24ab49c200219e3374d9ec6cc8e40` feat(rgb): add bounded fresh-humidity tint while preserving temperature bands and LDR brightness
- `ab4fbb18ce7d413859784a124598062a5e50dc57` feat(firmware): feed measured DHT11 humidity into autonomous RGB policy
- `51226abcd6423a99ae013fbb7cab3d75c4f8616a` test(rgb): verify humidity tint, stale samples and safe fallback
- `9ecda1bd94f6ac6f57eadbc114323ac379a5b7e8` fix(rgb): keep fallback DHT22 temperature and humidity from the same sensor
- `36a542aa265d499ae8f0cd7a7d05492a3006374f` ui(station): explain real temperature, humidity and light RGB behavior
- `b5c9ef74a2a11650795bad3195a2c69bdfe97375` test(nav): use current modules_connection card after UI refactor

### origin/feat/compi-ldr-persistent-calibration-20261022

- `1d1f2989739f6d3dd4bd219dd6ebc1f2456ccad4` feat(firmware): save and restore LDR calibration using NVS
- `70b61819307bc0f412c85d53a162b265d18c969c` fix(android): show durable LDR calibration acknowledgment

### origin/feat/compi-ldr-visual-calibration-20261022

- `0beb0c8f37aba045c9aa9cf15b3a78081d490a64` ui(ldr): add calibrated light meter and contextual calibration guidance
- `12ce333b6a3b6534e7454e36df18f89c68571afb` fix(ldr): distinguish disconnected from uncalibrated readout
- `6a7a51f5fae8b02589e333d09acc09ddb03d232b` ui(rgb): add restrained visual temperature-band legend without fake live state

### origin/feat/compi-modules-clean-hub-20261022

- `68bbf3dfa2192c93356675efa77c5b272d5ec246` refactor(ui): simplify Modules landing and remove redundant connection instructions

### origin/feat/compi-oled-boot-dht11-20261021

- `5759e6f4f3abde43ed6c586dc702bb99852a1343` feat(firmware): enable optional SSD1306 at boot with I2C probe and DHT11 display
- `dbe6ee0dccfa9a0267bda4a29e095529a99679a1` fix(firmware): fail compile rather than silently omitting enabled OLED
- `13ede43d5f84b52d3088bf1b31b99c2e81aef78c` ci(firmware): install SSD1306 dependencies before Arduino build
- `4ebcb82fc7cfb003bbd38e6b3a7389e7aa4c150b` docs(firmware): correct OLED ZIP wiring and BLE enrollment instructions
- `3164bb2e8454ea7569a7add128126250cad016d0` docs(firmware): add OLED/DHT11 setup, Bluetooth pairing and diagnostics
- `b56e873f1be434bc80c7f8604012ed9566d424e6` fix(firmware): make Arduino CLI discover SSD1306 library through explicit include

### origin/feat/compi-oled-cat-dance-20261022

- `e0d87d5f48cbe9476a073f94a81803da2c55de7f` feat(oled): nonblocking dancing cats animation on physical SSD1306 for 30 seconds
- `c37ebe486ed04326726418eae03c4ae0a1d8a270` feat(android): connect dancing cats OLED action and split expression controls into tidy rows

### origin/feat/compi-oled-faces-aligned-20261022

- `03ec742026ce1de09f3b134f1fc8398ddad5a3fd` feat(oled): add pumpkin and cat faces with actual SSD1306 rendering
- `afb4dc7e6292e0f9c81c4562ad066ba631fd49bc` feat(android): expose real pumpkin and cat OLED commands in Station and LDR
- `cc81dde57a03df8d3e22695ba44e000fefa01a80` fix(ui): use compact two-row OLED face controls for narrow Android screens

### origin/feat/compi-oled-live-controls-20261021

- `7a33b5e7577c8fd51b6e1f861728f3dd7cebaedf` feat(oled): authenticated bounded display command endpoint
- `1dec8c501c6b67c4ac6e71c7b3de094d8fdf1805` feat(oled): initialize SSD1306 and draw four faces or text
- `bbeaa1a65faa98319d6ee9a2d1c853e33d0bffd4` fix(oled): declare command renderer after module instance
- `dd4efbe6d5d18b89b0cd4c593f1a1991788c5302` feat(android): typed OLED command client with ACK validation
- `59cb1046069479f1dbd55b055483f1dedd778f77` feat(ui): OLED text and face controls in Hardware Lab with acknowledged Wi-Fi state
- `f2f6837f675bd719ce420a72434adfce2198102d` feat(oled): show startup greeting after optional display initializes
- `2f026105dc5a1adec5b717484f4d5cef8a62d944` docs(oled): wiring, required libraries and physical validation gates
- `fcf8eb1b9ee7a0d979a66aa6e2de9a89be3b72e5` fix(oled): correct C++ JSON response string escaping

### origin/feat/compi-oled-live-preview-integration-20261021

- `25a68acbf00e748bbef421c1496e2cff95ee84b6` feat(android): authorized OLED controls in Hardware Lab with live status
- `6757cbe4836a20c1272a3c1ab0c13a8043a5f806` feat(android): submit OLED commands through authenticated local lab channel
- `1012a9fbe881754ffb9a11a9ccc89e08fa9a860b` feat(firmware): authenticated OLED command endpoint and callback
- `7cea0767edf82146a5451a8afaeac97c7ffb0ad2` feat(firmware): render OLED faces via authorized Wi-Fi while retaining sensors and DFPlayer
- `a03ce13a5ba15aedb22aebc6fa8552ced939e802` fix(oled): guard face renderer behind detected SSD1306 build
- `8506b8f4bf741c19be94ed3598eadbd0703c47af` feat(ui): show last OLED command ACK preview without claiming live pixel stream
- `755c565ed3be6790685b43a69fc37d116bab0251` feat(firmware): single opt-in full-lab switch for LDR and DFPlayer hardware tests
- `9fe32d570cc707723c08febfc6a0c0a947d05ce6` docs: firmware download, full-lab switch and OLED preview limitations
- `cd787686df9a35146efdc678c41841d89cb36c6c` fix(oled): preserve user text and faces for 30 seconds before telemetry refresh
- `8420b5a7f158e7342e5f9947650d2a34b389e516` fix(dfplayer): reserve UART2 pins during full-lab hardware profile
- `aee4affa51679a3179f3a2c958d2f016d8b3f014` fix(oled): actually register authenticated POST display route
- `5b468918aedc1f1991548e12f02d145a324c4c6d` ci(firmware): build and package fully-enabled DHT11 LDR OLED DFPlayer test binary
- `57681f9acb987ee22b1f380460e7bcb7b6ca64b4` fix(ci): preserve ESP32 BLE build flags in full-lab compilation

### origin/feat/compi-oled-scenes-ui-20261022

- `b5fd7cbdd3b57026165691601fda12b5f9868d8c` feat(android): compact OLED dashboard and expose pumpkin/cat scenes
- `c2261f68150e22db45d969c05df69752dd0377a2` feat(firmware): render pumpkin and cat OLED scenes

### origin/feat/compi-ota-preflight

- `1ee5b9c2747c775bd93f26036dc35c6046b81d7f` feat(compi): validate OTA compatibility without enabling unsafe flashing
- `d72d7068a10782d9987c2ca2a011542ad8a1646f` feat(compi): validate OTA compatibility without enabling unsafe flashing

### origin/feat/compi-real-station-ux-20261022

- `7ba6129a810c6fecbb2937da35a730f247e945dc` feat(ui): make Estación Compi a dedicated sensor and automation workspace, declutter modules landing
- `0e0d36c01828cc6c7b69d26013e90a12275bdb67` feat(station): place working OLED expression controls in dedicated station panel

### origin/feat/compi-station-compact-live-overview-20261022

- `103617c7ba630c6f930f40f9719cadb777a8503b` feat(station): show compact confirmed DHT11 LDR readings in redesigned Modules
- `bcc6851a6f4d25bfc114d495bd99b6ba9198d401` feat(station): reuse authenticated environmental RGB control on Modules overview

### origin/feat/compi-station-overview-20261022

- `247a642ede7a69c174938ba52acb0d676ac272ed` feat(station): show live DHT11 and LDR snapshot in Modules without simulated values

### origin/feat/compi-station-voice-cycle-20261022

- `901f04a60f3033e1a886595c2e7201604eeaaa68` feat(station): greeting after 3 seconds and bounded temperature reminders
- `94a57a3fe3ca03c09d1641de96331146997a5c34` test(station): greeting and periodic cold hot voice requests
- `090ecd114607898823efe16557d63e990833c166` ci: execute environmental station voice regression tests
- `9d1204f9503cb8bbdbc1d68f268dd7058115c7f7` docs(station): correct LDR GPIO32 and voice timing contract

### origin/feat/compi-ux-master-v7

- `b915863c328b18273a94cd6588220e6d49074cc7` feat(ui): introduce accessible shared Compi cards and contextual messages
- `1feeafae3c76c527a66616bc14083d8594019e5d` feat(ux): make Home and Center compact, contextual and actionable
- `e0eb5d20dfbdb56b3dfce3e98404f5ec44411819` refactor(nav): wire live BLE device state into Compi Home
- `b37a9995589a30dcb5b406eee6f7ce3dcc2e19b6` fix(studio): protect edits against import/paste replacement and show contextual feedback
- `1644b208d0cd5af31e4247e603c05510421dc626` feat(hardware): organize lab by tasks with contextual results and preserved operation states
- `26479d9c2142922cdbe2cc17bf2f1049d6722290` test(ux): preserve navigation checks after Compi dashboard refactor
- `f3570f291e67ccd7eeaaa3e59083f502ed602482` test(ux): preserve navigation checks after Compi dashboard refactor
- `03a983ab35b0740bb73ea59e000bb8ed8ad9e22f` test(ux): preserve navigation checks after Compi dashboard refactor
- `0b812153477297cad229ed923949c1e4de66db44` feat(led): compact connection layout and reject stale Wi-Fi connect results
- `de4cc3eb2d86a2f9c4181c50b364c6ba677b9f30` test(ux): align device picker regression with compact LED controls
- `ede9a7ba022e13560f8c47112b3380efa24cc0f6` fix(hardware-ux): surface operation results in a Snackbar and guide missing lab keys
- `5e24fa7e106d50812778b9305b9f3f261b27c3eb` feat(led-ux): distinguish selected color from physical state and improve wheel accessibility
- `e6d9898e19cf3c909600caaa3eab99f5ef3c2811` test(studio): define pure policy for replacement confirmation
- `5375d971d7d3ae33aba44f352f9a39feecd5290d` test(studio): cover empty, identical and destructive replacement
- `7aeebbf11647eed295afe3c93c1a0c48f9e5b706` refactor(studio): reuse tested replacement guard for imports and paste
- `2d6a677cccb64cb5b4341a0c2b638400eeac163e` fix(home): describe observed Bluetooth state without implying selected Wi-Fi transport
- `fb8ad2c523447b83b3f3e4f2150c3fe44c0c1925` test(hardware-ux): cover navigation across lab sections without ESP32
- `ed1260f81c89dea90f29475d25be1be4894a08c3` feat(settings): align appearance, connection details and help with Compi UX system
- `0c041fc241f9eeb77183b831b8c0e90c26f3d006` fix(nav): route Settings to Studio with top-level back-stack policy
- `de00138d2ea5c4dbddcb3fdf949d827dba113e80` docs(qa): add reproducible V7 UI regression matrix and Windows APK instructions
- `9c22a40b789acff99af29f479fb50cb0bad41088` test(android): click merged accessible GPIO tab semantics

### origin/feat/compi-v10-device-operations-and-studio-recovery

- `d86bfca34ba49c43f7290487705695eb8efce35f` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `e01d821191d596a43b74554d5bf7a96f8f7584b8` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `dd423c853ef219942d88757e98336c271754a185` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `ad247d6f8fb07d02623f2e42944a9e6025ffa2bf` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `96007b05b2fba5d28076f7ea8f4936c9982e971f` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `b460b8125e6d6dc9b86065eb13609ff601a7a988` feat(studio): atomic crash-recoverable private drafts for all three engines and safe project opening
- `eebada0648b61327ec76916b4fca82d52d1ce8cb` feat(lab): coordinate user commands with observable queued/running/confirmed/error states
- `8db60c497e693090bcb211838f05af98ae8afae7` feat(v10): wire observable command states into Studio and Hardware Lab; enforce debug-only HTTP
- `1c824a56cb4996d72692d96a4f43d55b278ca23b` feat(v10): wire observable command states into Studio and Hardware Lab; enforce debug-only HTTP
- `67965f988b1af431028e056a3f3cceee6ff683b9` feat(v10): wire observable command states into Studio and Hardware Lab; enforce debug-only HTTP
- `d0ff28becdda358a7f50b1136267cca1c85c689b` test(v10): assert accurate operation progress/failure transitions without hardware
- `934629b6d351d96aefba9833d4f8dd5f397154ff` chore(v10): tag new lab Android build and clarify operation feedback
- `00af1f211a038755723a1cdb912200d5fc9063be` chore(v10): tag new lab Android build and clarify operation feedback
- `9918ac6fe3694f5d57fff3392136936f4103d01e` chore(v10): tag new lab Android build and clarify operation feedback
- `3d5fb95721d3616e74869fc5e3ab87c7ed63a343` docs(v10): master implementation tracking, device capability matrix and field QA gates
- `a43b796903e6d3cc0a394ffd60af34388286ac1b` fix(studio): avoid cold-start autosave racing local draft recovery
- `21fae0a58f03dc7210c2305e540f244a32322960` security(v10): never grant fictional BLE owner permissions without firmware cryptographic verifier
- `aade4faf0a4a27341411c8b411254ce4776995e8` security(v10): never grant fictional BLE owner permissions without firmware cryptographic verifier
- `9ca12df6dca0bcd495c6d25725336cbd94096152` docs(v10): explicitly block hypothetical BLE-owner state until real firmware authentication

### origin/feat/compi-v12-bounded-automation-main

- `42c7768f498ad773d5c8c313909be6f75fe0f793` feat(automation): migrate bounded SoftAP automation onto merged Compi V10
- `bf6f0b79aa1426516e66f5800055499add778b66` feat(automation): migrate bounded SoftAP automation onto merged Compi V10
- `2165d96ee5bf63f185d1a734f11bb9eb7c13429a` feat(automation): migrate bounded SoftAP automation onto merged Compi V10
- `d2283cacccd670fc1273abc60246e97c8aec9760` feat(automation): migrate bounded SoftAP automation onto merged Compi V10
- `3a4d3f30109ece7abb4986735ed43944a3315ef6` feat(automation): migrate bounded SoftAP automation onto merged Compi V10
- `c39ec148e8738c8596e2ac4a36e58d63ae021a55` feat(automation): add pure bounded interval contract and tests
- `9e7c997647390b069ea0aafdbf1918ef42742edd` feat(automation): add pure bounded interval contract and tests
- `d839573006b77b421f8d9530fd127bd9ae11bce3` test(protocol): guard automation bounds, auth and safe command priority in CI
- `7ce934c6dc8014acae792d71a80134e73af1d4fc` docs(automation): bounded motor contract, API, safety and physical test checklist

### origin/feat/compi-v12-safe-rgb-automation-lab

- `d86bfca34ba49c43f7290487705695eb8efce35f` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `e01d821191d596a43b74554d5bf7a96f8f7584b8` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `dd423c853ef219942d88757e98336c271754a185` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `ad247d6f8fb07d02623f2e42944a9e6025ffa2bf` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `96007b05b2fba5d28076f7ea8f4936c9982e971f` feat(v10): add safe Studio draft recovery and explicit transport capability contracts
- `b460b8125e6d6dc9b86065eb13609ff601a7a988` feat(studio): atomic crash-recoverable private drafts for all three engines and safe project opening
- `eebada0648b61327ec76916b4fca82d52d1ce8cb` feat(lab): coordinate user commands with observable queued/running/confirmed/error states
- `8db60c497e693090bcb211838f05af98ae8afae7` feat(v10): wire observable command states into Studio and Hardware Lab; enforce debug-only HTTP
- `1c824a56cb4996d72692d96a4f43d55b278ca23b` feat(v10): wire observable command states into Studio and Hardware Lab; enforce debug-only HTTP
- `67965f988b1af431028e056a3f3cceee6ff683b9` feat(v10): wire observable command states into Studio and Hardware Lab; enforce debug-only HTTP
- `d0ff28becdda358a7f50b1136267cca1c85c689b` test(v10): assert accurate operation progress/failure transitions without hardware
- `934629b6d351d96aefba9833d4f8dd5f397154ff` chore(v10): tag new lab Android build and clarify operation feedback
- `00af1f211a038755723a1cdb912200d5fc9063be` chore(v10): tag new lab Android build and clarify operation feedback
- `9918ac6fe3694f5d57fff3392136936f4103d01e` chore(v10): tag new lab Android build and clarify operation feedback
- `3d5fb95721d3616e74869fc5e3ab87c7ed63a343` docs(v10): master implementation tracking, device capability matrix and field QA gates
- `a43b796903e6d3cc0a394ffd60af34388286ac1b` fix(studio): avoid cold-start autosave racing local draft recovery
- `21fae0a58f03dc7210c2305e540f244a32322960` security(v10): never grant fictional BLE owner permissions without firmware cryptographic verifier
- `aade4faf0a4a27341411c8b411254ce4776995e8` security(v10): never grant fictional BLE owner permissions without firmware cryptographic verifier
- `9ca12df6dca0bcd495c6d25725336cbd94096152` docs(v10): explicitly block hypothetical BLE-owner state until real firmware authentication
- `21de254797b09d036581a744943da670473525d7` feat(firmware): integrate bounded 1–10 cycle RGB automation with validated priority and SoftAP auth
- `da2d1a8c359043c047f0d879507f0330b8097954` test(automation): bounded lab interval and cycle policies aligned with ESP32
- `8ca3122a25426a6993056726f5efda0f175e2d13` test(automation): bounded lab interval and cycle policies aligned with ESP32
- `cf0aee9c2a901f7a8da1f8404ee4a88065c12924` feat(android): expose bounded authenticated SoftAP automation endpoints
- `5a87bbeaaf20a3b8bd9f3d362a4f8630068ffe66` feat(hardware): optional bounded RGB automation lab panel gated by authenticated SoftAP
- `86c4bf5cae6580d1cf6877766bc93f837b2a6b70` test(policy): gate temporary automation by capability and debug SoftAP, never BLE V1
- `9e87d62bf49fcde116fd19e160c4546d55de3b9d` test(policy): gate temporary automation by capability and debug SoftAP, never BLE V1

### origin/feat/compi-v15-hardware-component-studio

- `a6d756a0c2d6b24e4a63bd316e392d613d6a189c` feat(compi): add modular hardware component catalog and Compose widgets
- `39fb1604738bec7a1d53449d29a46e576053a5e3` fix(compi): correct ComponentRenderer syntax and imports
- `9e997956b5b62c21099dd8ea9802c32263d64f80` feat(firmware): add modular sensor modules, component catalog and telemetry endpoints
- `f869a80f176fc17bf01d5b7a9b824856df222b0f` feat(android): connect Hardware Lab components to live ESP32 telemetry
- `7cf742658e42ebe3f9516fd4a4aeeced7d161733` fix(firmware): keep GPIO BLE characteristic args on one line for contract check
- `59369e7bdf742e9e80607f16a202c0441e21f674` fix(firmware): restore MTU negotiation and canonical registry/tick calls
- `e7427c13042b09e10d26100495a2701377162f84` fix(firmware): restore fail-closed identity and module init guards

### origin/feat/compi-v15-modular-sensors-ui

- `ed35caaef498d657ab9fe4d95b84f1c4459c8ca8` feat(v15): add bounded module contract and calibrated soil model
- `b9ee7199deef91f33829a85327b3f8cb3122b852` feat(v15): add bounded module contract and calibrated soil model
- `1df2117a0ec0f0e214288035a9ab41a792f8f4b0` feat(v15): add bounded module contract and calibrated soil model
- `7b6419029fedb883cfc48704b6ed4dc9ac798ff3` feat(firmware): publish authenticated read-only module discovery and ADC raw telemetry
- `15e9f6bb318b7686ea8fd78d1819a4d86e9f659b` feat(android): implement discoverable modules screen and live calibrated ADC panel
- `9920cec4118b764e3ba27b9aef16a56f15dbb45a` feat(android): add authenticated V15 module discovery transport
- `b0277fa6180a82f156953e4e7e87052d3e385de9` feat(ui): expose secondary module library without changing primary navigation
- `af1c0ba66d0594aad8617974d9f8c30134304f37` feat(ui): expose secondary module library without changing primary navigation
- `5319b0ec36c98d17bc9a887000fbb3c943b6230b` fix(v15): avoid shadowing Kotlin error function in ADC monitor
- `97b0067f57652b09ef905115fe5012056948edaa` feat(ui): extract reusable sensor metric and ADC trend components
- `b931ef6e794cca8039fa8f4430cc27709047de3e` refactor(v15): reuse modular metric and chart UI widgets
- `f3180608cff047836e9afeb02d4e0ab54aea4393` feat(studio): add safe non-executing module suggestions for known sources
- `946276c9540c812bb9bd1484fb912de65039f314` feat(studio): add safe non-executing module suggestions for known sources
- `6f44ac57807917ca74bfce15e2c4f61ccb884a65` feat(studio): show bounded component hints and open module library
- `8aacad6c939fd2630cc81f84f12942c52e631d9a` feat(studio): show bounded component hints and open module library
- `bcb34d7e7b13e09a8c58403eb2ad1f980e0eb5e9` test(v15): cover module manifest compatibility, duplicates and spoofed pins
- `d8976e35ef03d61d868410850a5a58269fa5926d` test(android): provide JVM JSONObject implementation for V15 manifest tests
- `b034a6a790a328621ed892fc51a6fb17db84b861` test(ui): cover module navigation and update stale V9 route assertions
- `f375e9679994a722ad256dbe9ed5ce66b7f6d5d6` test(ui): cover module navigation and update stale V9 route assertions
- `420ee85f29374588e37cbb5f34da81e36179fa60` docs(v15): record pilot integration, exact commands, safety and hardware QA gates

### origin/feat/compi-v4-capability-contract

- `22b65a135d572f7ee19611600f41fe16afe81071` feat(compi): add versioned capabilities and fail-closed permission contract
- `3716f04f6d10df4569fe81eadcb6dd8c24a72f82` test(compi): cover legacy compatibility and sensitive permission gates
- `c5f1c75ed6f4c5b34a1b00ec7bd488b0de34ebb1` test(compi): add Windows host runner for capability policy tests

### origin/feat/compi-v6-rgb-clock-runtime

- `34e5c3351d2c8230eb7e0c417972bee9d3bb2b29` feat(compi): wire bounded RGB automation into authenticated SoftAP runtime
- `88044c2326daafaa442bb0280daed36ec49942f2` docs(compi): specify bounded RGB automation API and hardware acceptance

### origin/feat/compi-visual-merge-rebased-20261022

- `7bcdebbab90a4b09197ac0175065875cee1e46d8` feat(ui): rebase visual module redesign onto current main
- `d3a6157f5ce6fb592f625949fe533bed6e777800` feat(audio-ui): polished player and library

### origin/feat/compi-visual-sensors-20261022

- `4ed1733e6bcb25945bd1aac41db8b08d77f475c5` ui(charts): prioritize live traces and concise sensor quality
- `556175c58daf5219c34502c92b405f1fb093da2d` ui(sensors): move diagnostics behind explicit control and prioritize charts
- `2e9de4d5088a3c93de0dafacc4c936a59917cbf2` ui(environment): refresh DHT summary with large metrics and clear freshness
- `aa10aee2685aafb733e6a6d42772744bc7ff36c8` ui(voice): compact environmental speech controls and visual affordance
- `7ee2f743c46d56ae8abed9e67e0287b98353fadf` test(android): scroll LazyColumn to DHT22 before interacting on API30

### origin/feat/compi-voice-card-ui-20261022

- `3134e620a25b9dee40ec2fae45e876d498d0341b` feat(ui): compact animated voice card and distinguish phone TTS from DFPlayer
- `7259809cec273556af2388f934321d9d8b3e9950` chore(ui): remove unused material import

### origin/feat/dfplayer-clean-player-ui-20261022

- `d47b0c7049e327e7b0293c748aec7a6403feb1eb` feat(android): simplify DFPlayer screen and move wiring into expandable details
- `f1c1389b35c8cb29d92680dbee75860dfb0f0843` feat(audio-ui): build compact player with now-playing, primary controls and clean library
- `69f5f84ed7f5ff0220adf9f732dea6b0c9f4e093` style(modules): replace hardware text walls with compact visual navigation cards
- `278481dfcd3dd4eff82903db4f8c1cd3095af317` style(environment): prioritize visual live sensor section over large audio library

### origin/feat/dht11-dht22-gpio16-integration-20261018

- `e5091d31d488de3141e34069f14353f298affaee` feat(firmware): DHT11 GPIO16 and DHT22 selectable temperature-humidity drivers
- `9ce4e653ed2621367a37a438edf46527736ffefc` feat(android): expose DHT11 and DHT22 pin selection and valid telemetry UI
- `e91d2a8f8bfdbd3b72f9e02da72051ae2c9bf2d7` test(dht): avoid local fixture/model name collision in host C++ test
- `a533ef9404b6e028cb145bf3ced1721720263a6b` fix(sensors): distinguish live measurements from GPIO driver activation for both DHT models
- `88324810af8f54952ef81c3a0e3a4ace0fb418b9` fix(sensors): distinguish live measurements from GPIO driver activation for both DHT models
- `7e5685f136f82cceeccb42e374ac4a12bec7b398` fix(sensors): distinguish live measurements from GPIO driver activation for both DHT models
- `9e7b2dc0adae69635c869616164ba0e7acb3b80d` fix(navigation): reconcile non-overlapping PR #158 tab/back behavior and UI tests

### origin/feat/esp32-control-center-v2-foundation

- `d5f0475f96100470d87551306afdd299419f32c4` feat(firmware): add conservative ESP32 pin leases and modular RGB lifecycle
- `2cdb424aa8d5a096ab45b0dab738e11ad0e73a60` test(firmware): add core pin lease and lifecycle host CI coverage
- `c01732c524e0282697c87c0a0c9c90af63a1c510` test(protocol): guard BLE RGB V1 compatibility and modular wiring
- `6328811e0e8ebe282f373a94383b286d3e875867` docs(v2): track phased roadmap, F1 constraints and hardware checks

### origin/feat/esp32-v2-declarative-lab

- `965f88bd7538ab70ec113a804e0cd90f0798630f` feat(lab): bounded declarative GPIO step interpreter with safe cancellation
- `50b812825239053be922c079b0b113ef095c789b` feat(lab): wire validated GPIO step sequences into Android and ESP32 API
- `9290a5c8511dd48500a722d620f84c980be25473` test(lab): cover bounded step execution and rollback in host CI

### origin/feat/esp32-v2-local-wifi-gpio-lab

- `2bc9f1ed230f990e281fc7ef2897c6452f936cc7` feat(esp32): add AP-only authenticated lab API, GPIO leases and bounded experiments
- `e7a4e1677e827d0e28841ae62584fbf1ee47dec3` feat(wifi): wire protected SoftAP lab and share RGB commands with BLE
- `36b3970f618b9bd08bdb8887a1b255e55ec2f246` feat(android): add local-only authorized ESP32 lab API client
- `9777814f5afdfec4801ca13ea25684e6126ca7e9` feat(android): add lab dashboard for WiFi GPIO ADC and RGB over SoftAP
- `87e395d953d43eaa4ec3bc430a06339dbe61ee11` feat(app): navigate to lab modules; debug network permission and v1.2 lab version
- `a0b21d7c9158cb77861a3924c28b5335630af7db` docs(security): document lab-only Wi-Fi operation, BLE limitations and pending TLS OTA
- `1cf498dbba717117f066f6b241233c187cd13940` fix(firmware): close experiment namespace for ESP32 compilation
- `8ab8dc8b1b77cf166551679633af5cd765662471` fix(build): use 1.9MB dual-slot partition profile for BLE WiFi firmware

### origin/feat/esp32-v2-ota-preflight-diagnostics

- `164f3b8ac358c198f99b13dce464053d70038ba6` feat(diag): read actual ESP32 OTA partition sizes; restrict lab GPIO whitelist
- `5981351dcf449d81e764bd926ba75f5763d7ea8c` fix(wifi): restore saved station credentials on reboot with SoftAP recovery

### origin/feat/modules-real-charts-dht11-ux-20261018

- `78b65d5e6c0f391590845e70358dbfca3175c546` fix(telemetry): attribute environmental history by active DHT11 or DHT22 firmware lease
- `3e380f630d91cc74e82768d24804de00ad02fb03` feat(ux): real DHT sensor metrics, 5/15/30-minute charts and read diagnostics
- `6941546d1f24edd0b3108c0e97775f35116bf743` feat(modules): place live DHT metrics and history ahead of GPIO diagrams, add soil chart
- `7ac1d3e7c50f5fe13264d8d8c10c5a3696ada269` docs(modules): DHT11 physical-test protocol and truth-preserving chart contracts
- `680927b58722cf695e6368abffa90c678766bc53` test(telemetry): require firmware-confirmed DHT model before alerts or historical attribution
- `9279d5fc39a2fb6f78e764bfc7b790e4557878f4` test(telemetry): require firmware-confirmed DHT model before alerts or historical attribution
- `c92e88a3a9a1a4cd2c32e19c3e741858124a35d9` test(telemetry): require firmware-confirmed DHT model before alerts or historical attribution
- `14eb77d1b5ada3c500856d41255e4e0c04686cfa` test(telemetry): require firmware-confirmed DHT model before alerts or historical attribution

### origin/feat/rgb-controller-v1-1-polish

- `84beb6c2a6b4592c70184f25cd31b73390fc79ad` fix(ble): validate initial state and protect MTU, write and connection timeouts
- `5404d7ab58df10f532ca970bfdef9fb8f810e6b5` feat(ui): matte theme, compact navigation, device empty states and persisted appearance
- `2ea887a3d007a999cd29da77cbf36c3939180b40` feat(rgb): refine control wheel, effects and presets; negotiate ESP32 MTU
- `ee9e97f852153771a67aa0084b8c879aac54167e` test(audit): validate BLE contracts, fix continuous RGB drags and document QA gaps
- `5b4754912a96fc4f4226fd4a64d57598579ae63c` fix(ci): use installed Android SDK instead of obsolete setup-android tools package
- `6403b77fac0137817d7c57b8c347761af57c7684` fix(android): import Compose collectAsState for persisted theme

### origin/feat/ux-guided-gpio-connections-20261018

- `ba341de3d11589a181b95d58a77be7bfb1148b96` feat(ux): guide GPIO assignment through validation, wiring and explicit apply states
- `c87534d72534a2e9d987cfd22473d62c91fb8418` feat(ux): guide BLE and Wi-Fi setup without implying successful authorization
- `668bad33af3abbadb1ac398dc3f8dea48e5aa210` style(ux): centralize matte surface container and outline tones for light/dark Compi

### origin/feat/ux-navigation-hardware-studio-20261017

- `df3ce6d32434c16276237d33e3956510c3885964` fix(ux): correctly label legacy LED route and select its module tab
- `76857779053c25b4f3fa784a7f0eca0ceda43b85` feat(ux): keep Hardware section selector visible while scrolling
- `650313c43c89fcdf87851f955bba22683d59137b` test(ux): verify Hardware tabs remain available after long scrolling
- `a5b6cf239887d5eaa58ed9e3e76eaab1f5797bac` fix(ux): prevent Studio mode selectors from clipping on narrow screens

### origin/feat/v17-dynamic-module-workspace-20261016

- `88c28babc5cbf6f05cd61d65fed0f3fbde009d80` feat(v17): model real per-signal GPIO assignment and conflicts
- `a95d6801d59457391f3b65e5f71a876a05e3390b` feat(v17): reuse validated signal assignments across hardware editors
- `afb79f6698582598fec74205e813462db9a09638` feat(v17): reuse validated signal assignments across hardware editors
- `6f0459b17b4dda71007c07220bee879a68ab30e0` feat(v17): reuse validated signal assignments across hardware editors
- `f7e7119f13a179f73b16f11ba334d4017f31cadf` feat(v17): dynamic module workspace with catalog, details, wiring wizard and real telemetry
- `d5de1361e207c6af020ee14a482a193ab3581fa6` feat(v17): Módulos replaces LED tab with safe legacy route
- `a88bc50b109750e1b8079271099ab847cbe87de3` feat(v17): update Compi Home for device status and modular quick actions
- `12e62e177bc51200c0d391df01699bb58281edfd` feat(v17): show authoritative Wi-Fi connection on Compi Home
- `df64ad75f64428675c6f0ddd4693e3fa64933c4f` test(v17): pin profiles, ADC1, PWM, I2C, ownership and collisions
- `140b222322fc4dea2b6369841190769267c7e7ae` test(v17): cover Módulos navigation, catalog and pin editor
- `e26e4a532a5bcbd82306e8809cd7e49acad3db80` feat(v17): genuine BLE and lab Wi-Fi connection center with USB limitations
- `9707545725a6b9aef047a190bccd4e418cb5bc37` fix(v17): avoid error name shadow and constrain connection center scrolling
- `2a9d4fecc752859aa633cd32b58747d906504266` feat(v17): wire shared BLE Wi-Fi connection center into navigation
- `794619604102cbed5ada6ffac915134120295dec` fix(v17): match firmware PinManager and protect RGB reserved pins
- `c74c6f2a76b274da6c612d963d6330ab87a217ed` fix(v17): harden connection states and module layout, expand pin safety tests
- `ccda54ea8f33d81f04f4e91a2850ebfd38e08218` fix(v17): harden connection states and module layout, expand pin safety tests
- `7bee5dbc66291b5e51b194ffcd0bdcecc30caa2c` fix(v17): harden connection states and module layout, expand pin safety tests
- `b40219246e5f8c9f48d68ac0e4eb329ee0e9c1d7` fix(v17): harden connection states and module layout, expand pin safety tests
- `b9d2438fc0762fa2a449e0ffaa516fc320d09785` feat(v17): expose verified BLE RGB module without inventing GPIO leases
- `0c1cdeb75d14d8b582bec0cb5072ff50b5bd7aae` test(v17): respect permanently reserved RGB pins even without an active lease
- `dcdb3fceca69f6007fee708d6f695f1b1e88389e` fix(v17): serialize firmware polling with RGB writes and reject stale session data
- `800d1807f975af7b7be6dee8a13bfd1a58618915` feat(v17): foreground-only real telemetry refresh with explicit stale/error labels

### origin/feat/v17-dynamic-wiring-preview-compose-20261016

- `44d8989c9e3f8b2f40ec627eabd3059c4d3502ba` feat(v17): Compose generated, provenance-labelled ESP32 wiring diagrams
- `1e96272d3cab8436177969c28241ecb2fff63e66` feat(v17): show computed wiring diagram in module detail and GPIO wizard

### origin/feat/v17-firmware-authorized-wiring-transaction-20261017

- `7b92facf553c7be9a73c3ce4bbff2114d6fdf89a` feat(firmware): host-testable, fail-closed GPIO reconfiguration transaction
- `6894a826159a380b879d3b020cab7f2f616c4f81` feat(firmware): allow validated pin reassignment only while sensor stopped
- `43640920a610108a301bd6d45dad8092ad8eee0a` feat(firmware): allow validated pin reassignment only while sensor stopped
- `32d11d34db73335083a3adc4ec478bc8a77c0fa9` feat(firmware): authorized idempotent wiring apply API with optimistic pin check and acknowledgement
- `811ca8c0bb995ab6d1ea96785f85fba53663e79b` feat(firmware): wire DHT22/ADC1 reconfiguration into ESP32 runtime and telemetry
- `5059730aa39d6be75e581bf373b9e80e0d0118b2` test(firmware): pin authority, stale writes, idempotence, sensor rewire, rollback
- `1f70cd5fac90b5eea9b8caa5f61f335fe5ec57b3` ci(firmware): gate all pin reassignments through host rollback tests
- `70ce8bfb8e48b65e980dd22972e04ee09a72bf99` feat(android): strict wiring receipt and firmware pin readback contract
- `24f5cc86e452fa9994bf675c4b4485a2c15fc005` feat(android): authorized wiring apply with bounded request, explicit receipt and status readback
- `21a9b2677daec62af58b6a749ccb0b19aa7e52f5` feat(android): guarded physical GPIO apply with confirmation, readback and visible outcome
- `031d4e0d73bfe4019dbc6264c6376fd5dd2f0e79` test(android): enforce firmware authority, request binding and active GPIO readback
- `6cc93d925a68c216cf2707dfcff394aff1d0198e` feat(ui): segmented module tabs and accurate firmware-vs-local status surfaces
- `ad258946434b2e3b2b560c504d6108653806cb45` feat(ui): add honest current-module dashboard card and distinguish BLE vs Wi-Fi activity
- `03a2bbb971d42eaaa690d563254fb784d8ef0d25` fix(ui): correct Spanish catalog names, default ADC1 signal and unsupported module guidance
- `517ec67d72c73a56fa490b402a39d8e4ff9fdee8` ci(firmware): compile sensor-enabled DHT22/ADC1 variant and enforce OTA slot budget
- `1e4e9e0d7d7d8750ea05a16959d59e94ae4e8449` feat(ui): aggregate DHT22 and soil into logical environmental module without mixing GPIO ownership
- `cca5b27ea67e46e26ae16cd437b9c7fcc4290206` feat(ui): composed Sensor Ambiental workspace driven by DHT22 and soil firmware state
- `18be5daefcdcce98f9b791dd9b797f5098d8698c` test(ui): validate composite environmental group without inventing firmware module IDs
- `5fd801e855620ca04cad3fafe982001ab7b5cf42` fix(android): opt in to Material3 segmented buttons API for modules navigation
- `cb5038efebbf39ca8fc821bec9d76da73d45a234` docs(v17): exact GPIO apply/ack/rollback contract and physical validation protocol
- `96391335479d7ca1c102c2f480d9859465dc3008` fix(ui): distinguish firmware GPIO leases from actual physical sensor detection
- `46f6c24efd2021932038e11ba35c6052561a6710` ci(firmware): deliver sensor-enabled ESP32 bench image with hash and safety manifest
- `a124eddd739426ad6f2fc811803d74533b6d1d4a` fix(ci): build opt-in DHT22/ADC firmware from isolated sketch without overriding core compiler flags
- `222f53e81d895e279811833015edc0f5104c2638` fix(ci): prepend both sensor flags deterministically with separate sed commands
- `3376f021456200a5ac8f6b3f62d886f8aebf063d` test(android): scroll Home card into view and await actual Hardware navigation before assertions

### origin/feat/v17-hardware-dashboard-sections-20261016

- `04256db63a66985bfce43585870857c47530e687` feat(v17): sectional Hardware Lab dashboard with stable lazy keys
- `0adb9b655376b5c88a461d4a741d46fb38d14131` test(v17): reach bottom of Hardware Lab across all sections
- `0fad611579ca1b87fe10111e4a0c4d074e611676` test(v17): align navigation assertions with new Hardware tabs and Home routes
- `2c84b5fdece4b68d97c0ad2198c0c09ea6fba5c8` fix(v17): independently remember Hardware sensor and GPIO detail preferences
- `6ada94cfda02d11e802f885c5a8491b7ee9a1b77` test(v17): scroll horizontal Hardware section tabs before tapping

### origin/feat/v17-home-reference-cleanup-20261017

- `0ffaa52711352e57ee0eaf1c749e3610bf3187c7` feat(ux): remove duplicate Home tool cards and filler; retain tagged quick access

### origin/feat/v17-real-wifi-scan-ui-20261017

- `42b151b2439b784e50992fea0394d25d05bc5710` feat(wifi): deterministic presentation of real Android scan results, prioritizing verified lab SSID name
- `0d904fe1be0c9b7856b8f2e44c24447f25c62f6f` feat(android): opt-in Wi-Fi scan permissions on Android 11-15
- `018f9190f105d598f0b7c24c6185ef83f796dc61` feat(android): explicit-permission real Wi-Fi scan and grounded nearby SSID list
- `172a050882fa0e4659475a50eaa10d38be6535f9` test(wifi): real scan ordering, dedupe, and absence without fictitious devices

### origin/feat/v17-restore-advanced-esp32-lab-20261016

- `d5433c11c3d38785657709b8a1425e5ce82e2ed7` feat(v17): restore advanced ESP32 Lab without replacing modular Hardware dashboard
- `31962f4ecb84ad7a366356cccdedcba512dacdc8` feat(v17): restore advanced ESP32 Lab without replacing modular Hardware dashboard
- `a8e250842bad00f636a22846321fd397447dc611` test(v17): preserve advanced Hardware diagnostics and experiments navigation

### origin/feat/v17-verified-rgb-gpio-reassignment-20261017

- `b324ca13e194072e22acffcceaf645d88fb53165` feat(firmware): permit validated stopped-only RGB PWM pin remapping
- `17ae90c90a504573ed4bf03ab3b76be1af4c6da4` feat(firmware): extend idempotency replay guard for third RGB GPIO signal
- `1de52c94bb81d8278acdc33398f3a83aced0dc12` feat(ESP32): authorize and validate 3-channel RGB GPIO transactions over existing apply API
- `2816493f6bc974b6a27309bf89f74b0885daab18` feat(ESP32): transactional RGB PWM pin rebind and rollback with live-render source synchronization
- `8552a9438e6fa29fc34685db0714e159e9867441` feat(android): support strict three-signal RGB compare-and-swap expectations
- `032965ce4b3ddcfaafef0d75058c3196ea0fda39` feat(android): apply and read back three RGB PWM channels via authenticated firmware API
- `7148e94b9643e9758ba6a3ddd3d3da07b775943b` feat(v17): enable verified RGB reassignment in module GPIO assistant over authorized Wi-Fi
- `db23740801a62d9e0e653e7eeaabce54eafa6976` test(firmware): RGB idempotency ledger rejects third PWM channel replay conflicts
- `bfbf27d06261216174e8b80b20bad7764e1e43e0` test(ESP32): validate RGB PWM pin remap, conflict rejection, failed attach cleanup and rollback
- `711524741c18781857f5e0597debf723f928d9b0` ci(firmware): test safe RGB runtime PWM remapping without physical hardware
- `b598e2a1a9b662012eb202e431690e9cd1bf3226` test(android): cover three-channel RGB CAS and strict firmware pin expectations
- `507a885d352116dd58482df55ddd6aa4beed9566` test(firmware): distinct request IDs for OLED and RGB idempotency fixtures
- `594ae224fe11485d02f68247b04681f3e27ae64f` fix(firmware): fail closed on missing RGB callbacks and set partial PWM pins input after attach failure
- `63348818a3e2b69fa11566705572ae0514415316` test(firmware): verify failed RGB attach tri-states previously driven pins

### origin/feat/v18-authorized-runtime-gpio-apply-20261017

- `a3939d379fcd234be076d63f0420fe6275f8c040` feat(firmware): allow safe stopped-only module pin reconfiguration
- `c8967568c67739f0ca345478a12c7114a16cace4` feat(firmware): allow safe stopped-only module pin reconfiguration
- `81984cb187b7719d5018d8b35f47e7d3169e8856` feat(firmware): allow safe stopped-only module pin reconfiguration
- `135276486da428d6324b037585df2159cf380027` feat(firmware): authorize runtime GPIO apply with compare-and-swap and readback contract
- `e8cb42fd91a09c517cd6f0ebe555ccf87c985e7b` feat(firmware): transactional runtime DHT22/soil/OLED rewiring with live DHT driver and rollback
- `47f20539e496363715ac7e0ba6918a1337f72ad7` feat(android): authorized compare-and-swap GPIO apply with independent firmware readback
- `3cb1ed48392a7cd8f3d7adc94c91c0d5798f58f4` feat(android): GUI runtime GPIO apply only with firmware readback and authenticated Wi-Fi
- `8de08d4eb9b57c14f8c8a9c5338e001689b1712d` test(firmware): exercised live DHT/ADC1/OLED reconfiguration safety and leases

### origin/feat/v18-compi-ui-connection-modules-polish-20261017

- `1e9066f0dfd2e1fadb413552cb90948d547b422b` ux(connection): responsive one-line transport tabs for compact displays
- `f2f9cb3475a197ed27514b68613d977fe20d4a9a` ux(devices): single-scroll discovery with compact status and honest BLE authorization
- `66848ef2c135822e14fb4550a5e2212411ba0345` ux(modules): recognizable compact ESP32-to-component wiring preview with truthful pin labels
- `4a9662de41cbd7e0c129353706bb5647fd28d801` ux(modules): grounded status cards and responsive pin-editor actions
- `e4ff5389e0c2194cb7dcded5368408b719671640` test(android): cover compact connection transport switching and Home navigation
- `c951361a5596fd29cd268543195f38fc56ab8c68` ux(home): remove duplicated tools and create compact high-clarity quick-action tiles
- `0eb5cdc14b66aa89c928c6838897c757ab6ba364` fix(ux): preserve public BLE identity non-authorization warning required by safety gate
- `42855034b83101b3105ab5bd0ea8d9ee220461de` fix(android): import Alignment for compact transport tabs
- `3e315b6463a8aefcb64d5f0dbbf18982937c6707` refactor(ux): de-clutter BLE discovery and surface real scan evidence without long warnings
- `f7ca525aa3389b8ef5657a2f048fe08994aaf93f` fix(ui): preserve balanced Compose groups in conditional wiring diagram preview

### origin/feat/v18-home-module-experience-20261017

- `ab1222bf1274078bcd9448ce658d308b20e81320` feat(ui): real Home active-module summary and sensor readings with strict provenance
- `9ea7714b0a63bee0d7b20176dcd37691c398056a` test(ui): assert device-grounded active module panel exists in Compi Home
- `6a3ca500cf0ab11b23392dd7c23a5c7189c6ddf9` feat(ui): real Wi-Fi diagnostics signal latency firmware and memory in Compi Home
- `a823181065b13b95d998f6c6db527b0ea96cf760` fix(ui): nullable firmware version parsed without invalid ifBlank return
- `8eb95596a40eeac07c02460b9997ded38812ca83` fix(ui): invalidate home diagnostics on lab session revision changes
- `956ee349100869130732c387eaf6f4fb3556404f` test(ui): wait for asynchronous NavHost destination before interacting with Studio and Hardware
- `31a961bc1d93504dc656e8ed131b594efa0b2ad9` test(ui): scroll Home action cards into viewport before performing navigation clicks

### origin/feat/v18-modular-ux-composed-panels-20261017

- `71562801f6a49c014f2f8975fe8ce0216b0e22d4` feat(v18): compose DHT22 and ADC1 as reusable environmental workspace
- `68b8128e9440afd7fa02043308adf757a3c0496e` feat(v18 UX): compose environmental DHT22+soil screen with independent real GPIO editors
- `db6b3a237050e7c8beb28ad0b18cec1a6c9b2a62` test(v18): DHT/ADC environmental composite only when both modules configured

### origin/feat/v18-module-cards-state-ux-20261017

- `f0ea56f97a1aae0d3298328861fa4f3d2e2d7f22` feat(ux): component-aware visual tiles and firmware provenance on module cards
- `fdccb2500e4066ffd0e6c36a55a922958367bfae` test(ui): verify dynamic module icon identity in library and configured cards
- `8a21173193617e999530883858b1ef613c7f89df` fix(ux): repair Spanish catalog encoding and clarify hardware firmware requirements
- `7058466997a07b9375f0b48755d7d9bd4d1af6ba` test(ui): assert nested module icon tags in unmerged semantics tree

### origin/feat/v18-replay-safe-component-apply-20261017

- `e77d508623545de80d3f99cb7fcfdb92349198f3` feat(firmware): fixed-size transaction deduplication for authorized GPIO apply
- `66af08580307358871f4826caa6100d96b28c10a` feat(firmware): require a transaction nonce and reject replay with altered GPIO assignments
- `af50108160056a9c9235501714772991ca523789` feat(android): bind firmware apply receipt to a fresh transaction ID before reading GPIO state
- `2437aa91c180c5cab8dd6142525c72c998a93b8b` test(firmware): replay guard host tests for conflict, invalid nonce, idempotency and eviction
- `706db23cc6fd060c231d90e0a1570bc62fbff863` ci: require replay guard adversarial host tests for every GPIO firmware change

### origin/feat/v18-runtime-module-activation-20261017

- `d663deda572be7839b4033a6d358fdbb38835860` feat(firmware): compile optional module drivers without claiming GPIO; tick runtime-activated modules
- `6c84d891f1c5ab89bfef9cb3d01231972ba524d5` feat(firmware): activate unstarted sensor/OLED modules through authorized compare-and-swap
- `169e48564b8ee03a73ae14c1d819c8179615f25e` feat(android): include inactive-but-supported sensor modules in authenticated GPIO activation
- `25ed1b06b171e0001f3ea7a245a3dfe9366e4afb` feat(android): allow authorized activation of inactive firmware-supported sensors
- `015a0bb50f27fbbfcc895d21e27858eb8c764f6e` fix(firmware): qualify ESP32 PinManager methods in enabled sensor runtime paths
- `1bd7e2350586f0f2a91a3de262d387c2312edd5f` fix(firmware): inactive optional drivers are catalog-only, not detected or erroneous
- `744728c884ceadd21f39dedb8f7fa3ddca0c91a5` fix(v18): do not infer installed sensors from firmware catalog-only descriptors
- `66cfb013f61c979a976290852f45447fb0083b80` fix(ui): distinguish compiled driver suggestions from actual active firmware pin leases

### origin/feat/v26-ldr-light-sensor

- `ebd8622e55918ebf532b63d0da829a92fc1f3777` feat(v26): add LdrLightModule for ESP32 ADC1 photoresistor and Android catalog telemetry
- `f452fa37b9466a9842ec0f2f882fcb8d3d472f0d` fix(ldr): reject ADC read errors and invalid calibration, unblock host build
- `90a112e70b7f1d3c763ea28e0b6d984d223af855` feat(ldr): wire ADC1 light module into ESP32 runtime telemetry and GPIO leases
- `c6e0cfb389a286ccd67a40695059070c7382a838` feat(ldr): allow authorized ADC1 component apply requests
- `4fe052a24da482dea729066b54a339d9a675f7c1` feat(ldr): add GPIO36 ADC1 wiring profile and safe pin validation
- `510032d05cfee1c3e0fac81a72900ec6697a1f6e` fix(telemetry): preserve uncalibrated and stale states without upgrading validity
- `4034d6b1998b17e5c37d180b4018db44ad14599a` fix(ldr): identify ADC raw readings without mixing soil and light sensors
- `d47c7bee0e6db726962b1c0b2201662d4dafb16e` fix(ldr): publish own raw telemetry channel
- `8075d8014a604f0b638d968cf9aa9400cc790a59` fix(android): prevent ADC readings from unrelated modules leaking into LDR view
- `e0da4f8f1effc934e946761f0e1c21b3bc8b4899` fix(telemetry): prevent truncation when all optional sensors report
- `f293fd707d6e7bf51d106208d7e1bf16f17d82b1` feat(android): display real LDR ADC1 reading and calibrated history
- `db43826f591c59637d74f0435a46deacf81d28e5` test(android): distinguish LDR and soil analog telemetry in real module states
- `b67624a9671bcb732487f026dd6cec848bd67871` test(ldr): cover invalid ADC, calibration guard and preserved diagnostic status
- `75b9df5b794025b38aa5028d1c375deaf9d9c810` feat(ldr): authenticated runtime calibration and larger catalog response
- `1d3c048bd77e5c583048af88c86eb445e314b488` feat(ldr): enable calibrated relative light levels through registered firmware callback
- `a5a498dad7132be074d896f90907e296de53bba0` feat(android): LDR calibration and confirmed GPIO application using existing SoftAP auth
- `d8789a04f2253cef832aa547dcd14775661782ba` feat(ldr): capture dark and light ADC calibration in Compi modules UI
- `851f88c42444bbcdb95893cb0c7871638a6c68e1` fix(ldr): enable approved module wiring in GPIO wizard
- `64a98ecddd06b725f7c769568cff1248caaf2a7a` docs(ldr): electrical wiring, authorized calibration, test plan and hardware gates

### origin/feat/v27-android-environmental-speech-20261020

- `7b988fec4229986f9c663f08e5f52e7f8c7ed1cb` feat(v27): format Spanish environmental announcements from verified fresh telemetry
- `dfa9ac2629428a5763c430b4426d70806fc6c160` feat(v27): lifecycle-bounded Android TTS backend for Compi environmental voice
- `c8cb951e94eb58ec9eacd6dd58bcd1dbff99ba44` feat(v27): add in-app Spanish voice announcement for live DHT11 and LDR data
- `62641aa3d5b8663f3a810b46c96068b9b422b6e0` feat(v27): surface environmental readout button in real sensor module screens
- `8aa5f2afa0824bca6a73f4e2df229614f37a388c` test(v27): verify speech never invents stale or invalid environmental readings
- `3dcb96492e6900c53d13d6cdbc067679395a5610` test(v27): accept locale-correct Spanish decimal separators

### origin/feat/v29-ambient-rgb-policy-foundation-20261020

- `31b9bd0b4026d03b66a5f1f1e037329027bdcb88` feat(v29): add pure RGB temperature and light policy with stale-data guards
- `062e69d8094e0d5e39cb138e2c32890c322bdf25` test(v29): cover temperature thresholds, hysteresis and invalid sensor fallbacks
- `daac44c4c3a7a1cf23f8e981877d08f28cf3ca66` ci(v29): require environmental RGB safety policy host tests

### origin/feat/v29-ambient-rgb-runtime-20261020

- `7da5bf035a7bd6d604d3b6472fa6fe2effeae25b` feat(v29): add authorized runtime-only environmental RGB mode with script exclusion
- `eb36dc9b46a632c0748c72edf0cf05394294d873` feat(v29): drive existing RGB PWM from fresh DHT/LDR rules with manual override
- `500ae7e39cbd75acd9b9078b65bb580f51094b88` fix(v29): prevent GPIO rewire and LDR calibration races during ambient mode
- `c02e71fabcf43e2c0b8503a6ec52dcd3c9a4d0a3` feat(android): authenticated environmental RGB start stop and status API
- `f8b11e93651a7f344a273652448b74f844b19c3b` feat(v29): add firmware-backed RGB ambient mode toggle to Compi LED
- `0cfb30389b330dbdb6d7d3a1bdede56f22db08da` feat(v29): expose real autonomous temperature/light mode on LED screen
- `0edf3a744271a2f4a8c4f9493bbbdc71fa4dad48` fix(v29): refresh ambient mode from firmware after manual BLE or Wi-Fi overrides
- `570059bc7a8251678070ae610a4ef7829bc4548b` docs(v29): firmware runtime arbitration, sensor fail-safe and physical QA protocol

### origin/feat/v3-adc-telemetry

- `08d060b70de5a06c9267368293128e93f37b3dad` feat(sensors): bounded local ADC1 telemetry buffer and CSV export
- `c8a5608de7a4c68f89166e23cca60d8e8659a2b4` test(sensors): cover ring-buffer limits and uncalibrated CSV
- `1a5f09edaea77a93af664d23f36c2764579dfe15` feat(ui): foreground ADC1 chart with stop and bounded CSV export

### origin/feat/v3-board-pinout-and-safe-gpio

- `fe1f01b2c1cf135b1fbcbef49057bd06285fbfd4` feat(hardware): document safe classic ESP32 laboratory GPIO profile
- `c1a59aacf989782b9b5aefa7b1f405a8a3155bcd` refactor(android): use one audited GPIO catalog in client guards
- `7addf8099cab6f51f676965bfdeb359a02ac30d1` feat(ui): add safe pinout and remove overlapping GPIO choice row
- `4f7b7194dc4f4f0b9ce5eabaf69af665339fbedb` test(hardware): enforce reserved RGB and flash pin invariants

### origin/feat/v3-device-diagnostics

- `b665363dbb5e63b2562b0ea0b15fa35eefe910e5` feat(esp32): expose read-only authenticated resource diagnostics
- `dbda0f54462512845f04a1588c0a22a21a9db881` feat(android): add typed diagnostics fetch through local restricted client
- `8745f3c5f3d1c202e720d2eb6c1995c8db23d7cc` docs: describe privacy-aware diagnostics response and access rules
- `a8a8e54e6a957f9da2270af0d6cba8e305dee71c` test(diag): assert telemetry endpoint remains AP-only and excludes keys
- `ef9e55dbe17e20650f448cd4320dbb484ac016fd` ci: test no diagnostic secrets or unauthenticated LAN endpoint
- `30336cbca06e69bc234408071f3a0a3d3fdc0e73` feat(ui): integrate authenticated chip/heap/reset diagnostics in lab screen

### origin/feat/v3-local-experiment-library

- `5fd8e48e0a681c927ab0c9ee3f3b4979f2be2fcd` feat(lab): add validated reusable GPIO experiment templates and step model
- `0582d042c60819a24cc4692cdac82f42428928c3` feat(data): persist validated lab draft locally without storing device secrets
- `f8cf847b3337290a679f9da067c1c022b9e13abc` refactor(lab): reuse one strict parser for UI and Wi-Fi commands
- `90155a05faf85bb8c8f9bed91656c31f89ad444a` test(lab): cover visual round-trips templates and limits
- `aabb56f4a1afa1e2b1bc71819e7003752efca34d` feat(ui): visual bounded step editing templates and save/load draft
- `99814cfe151fe560cd8ebeddf6696f56be019ed1` test(lab): reject undersized step duration

### origin/feat/v3-local-projects

- `0299d6e21afaf9903238586c2cdc003a885909b3` feat(projects): validated offline-only multi-project library codec
- `e18cbe3e3b51f9ce35d0601f1b277810831559e5` test(projects): round-trip overwrite capacity and corrupted data
- `958cdef63ecee2893b6cfcd1a5667cedd492c3e5` feat(data): persist up to eight validated experiment projects in DataStore
- `bb66421aee88d97ed9627db0ddc65efe9167b656` feat(ui): save, open and delete offline laboratory projects

### origin/feat/v3-rgb-wifi-control-parity

- `a70dfbadb3c181424fd370a5255a7250cb8249f7` feat(protocol): validate full typed RGB V1 commands in Android
- `389858467cdb5a8d85f0c902e486cdc6b027bcea` feat(wifi): expose complete validated RGB SET/FX commands over local lab
- `3b270bdb8c7ed794ec2f2380ea170c3bd97b39ac` refactor(ble): use shared validated FX command contract
- `948c178c159abef37aaefaa35d7643ae58a72de2` test(protocol): cover full RGB Wi-Fi parity and malformed command rejection
- `b3803f5d6e2779b5925bb9fce3c31002f5859783` feat(ui): apply full RGB color and effects by Wi-Fi with state confirmation
- `8ec83810bcb682e92ef721716164546546397c32` test(protocol): verify new shared Android FX codec rather than old inline string
- `aae5ddf050b4a95ec9434213b04ef2fd948eafc1` fix(protocol): align Android numeric ASCII grammar with firmware

### origin/feat/v3-test-diagnostics-and-readback

- `7d9ccdf98323886f837939b811b74e9cc82517d1` fix(wifi): define explicit ESP32 SoftAP route requirements
- `d447e55386d8d5bae5b120146f52541133eda6c3` fix(wifi): bind SoftAP lab HTTP to explicit Wi-Fi network, never cellular
- `9bd519c968be260771741250a23dc54035935530` feat(ui): route laboratory requests via actual ESP32 SoftAP connection
- `047ef82bad495233753ae7dd9cb39c903ed3555f` fix(android): declare access to Wi-Fi transport route discovery
- `bd508199f50ec5abdb1cec4270e416d5ba48a8e6` test(wifi): disallow mobile-data fallback and incorrect AP gateways
- `39d64bdb631e7ff495edec57f6e59d343ce86997` feat(qa): strict redacted API self-check with explicit physical-test limits
- `6e901528e80e9b7bc50461cfd490da83fa0813b1` feat(rgb): verify Wi-Fi SET via authoritative STATE readback
- `381232df8e6b46c41eb8b98c425b110eaae06057` test(qa): prove readback precision and fail on invalid hardware metadata
- `76c22a531884291b9b50bb3991164388df1e6c15` fix(wifi): require actual ESP32 STATE matching SET before claiming success
- `c4abb6c8ee8bbd906763169f7a692313d3dd23aa` feat(qa): read-only self-check, redacted share report and honest RGB readback

### origin/feat/v30-ble-environment-control-20261020

- `e6496f5e954eea324ec94d51b4d1b8ab16c58c9a` feat(ble): define strict environmental control frames on existing GATT characteristic
- `43ae3f68b07291ad2ebf5301518fbe262533e210` refactor(ble): unify environmental RGB arbitration for authenticated BLE and Wi-Fi
- `fed28fb6db4506b64423bcb568fffd828689627f` feat(ble): accept authorized ENV GET/START/STOP with actual firmware acknowledgments
- `813b664e94cada7eb1237dea1dfae3bf98096a4b` feat(android): parse exact BLE environmental mode acknowledgments
- `585496fb2afe35d93863643d30c0d789676a277b` feat(android): send owner-authorized BLE environment commands and track firmware replies
- `89b6927183d3650c898ed0500d69e24c71462d1c` feat(ui): control ambient RGB by owner-authorized Bluetooth or Wi-Fi with firmware readback
- `c0939fdc8c9d8dff857880590cb654120286dc9c` feat(ui): show RGB ambient controls on both Bluetooth and Wi-Fi
- `b4ecb0ce2c00a762a480c3162d3ece45c944120a` test(ble): reject malformed ENV frames and require owner authorization
- `ff56a47252ee8b3071265aaaf0863c59c51f1b5c` test(android): require exact firmware BLE environment acknowledgments
- `c9032973136f1451b7f78350384c93eacc12dcfd` ci(ble): gate ENV command parser and owner policy with C++ host tests
- `253a30a041729e7894251a26af037b6b58af81c4` docs(ble): inventory transport gaps and staged path to full sensor and GPIO parity
- `e6351c4b8c506f600b7339db7c0ef3e7581c0c67` fix(ble): keep confirmed environmental state when queue full and coalesce status reads

### origin/feat/v31-ble-sensor-snapshot-20261020

- `c380c98fbac312fe32b99621be0da80c97e90aaa` feat(ble): bounded nonblocking GATT snapshot framing for status and sensors
- `59e22a3e69a717dee622ebfc6e9b79021a2833d4` feat(ble): stream owner-only DHT/LDR and component status snapshots through GATT
- `e89a0a186e2184ca545998a873974ad1fa7db84f` fix(ble): bind each snapshot to strict client request sequence
- `f22d018fd946e203ef2a78adaa2bdc63b761c9a0` feat(ble): echo client nonce in read-only snapshot frames and reject malformed requests
- `a2586b267aa0168d60c6f9b91d8ce33312d6c28a` feat(android): bounded BLE snapshot reassembly with sequence, order and timeout checks
- `a6fa6ac04689117a75a49a2f6c8e6357739921c1` feat(android): authenticated GATT sensor/status snapshot requests with strict readback
- `f7519e6356c53a47f24b16c89b32c2b80a30b35d` feat(android): feed existing graphs and modules from BLE owner-authenticated snapshots
- `0171b05d1628ed92dd2603a8dac77ede8ac84d91` feat(ui): refresh BLE sensor graphs after owner auth and display actual transport capabilities
- `f438f28b211b892ff561bfaedb6ac4eabf9b8e4c` fix(voice): explain authorized Bluetooth as an environmental speech source
- `e1778b4ae3fe58f2398e8d68d612ca264eec76a8` test(ble): validate bounded snapshot chunks, reconstruction and owner gate
- `541d272157f4df3e82e6a755d5bc1102d078c8b6` test(android): reject incomplete, stale and cross-session BLE sensor fragments
- `31c4afd7202de2e14194e81411fe751926f58a93` ci(ble): require bounded sensor frame safety tests in every PR
- `eb9271c01e2f15eb6e7674a097100fead551ce2b` docs(ble): snapshot protocol, auth, mobile tests and remaining GPIO/audio gates
- `8e047b95d110df8328e5c3c56f649d90379dab1d` fix(ble): use snapshot active() guard in portable framer

### origin/feat/v32-ble-ldr-calibration-20261020

- `c380c98fbac312fe32b99621be0da80c97e90aaa` feat(ble): bounded nonblocking GATT snapshot framing for status and sensors
- `59e22a3e69a717dee622ebfc6e9b79021a2833d4` feat(ble): stream owner-only DHT/LDR and component status snapshots through GATT
- `e89a0a186e2184ca545998a873974ad1fa7db84f` fix(ble): bind each snapshot to strict client request sequence
- `f22d018fd946e203ef2a78adaa2bdc63b761c9a0` feat(ble): echo client nonce in read-only snapshot frames and reject malformed requests
- `a2586b267aa0168d60c6f9b91d8ce33312d6c28a` feat(android): bounded BLE snapshot reassembly with sequence, order and timeout checks
- `a6fa6ac04689117a75a49a2f6c8e6357739921c1` feat(android): authenticated GATT sensor/status snapshot requests with strict readback
- `f7519e6356c53a47f24b16c89b32c2b80a30b35d` feat(android): feed existing graphs and modules from BLE owner-authenticated snapshots
- `0171b05d1628ed92dd2603a8dac77ede8ac84d91` feat(ui): refresh BLE sensor graphs after owner auth and display actual transport capabilities
- `f438f28b211b892ff561bfaedb6ac4eabf9b8e4c` fix(voice): explain authorized Bluetooth as an environmental speech source
- `e1778b4ae3fe58f2398e8d68d612ca264eec76a8` test(ble): validate bounded snapshot chunks, reconstruction and owner gate
- `541d272157f4df3e82e6a755d5bc1102d078c8b6` test(android): reject incomplete, stale and cross-session BLE sensor fragments
- `31c4afd7202de2e14194e81411fe751926f58a93` ci(ble): require bounded sensor frame safety tests in every PR
- `eb9271c01e2f15eb6e7674a097100fead551ce2b` docs(ble): snapshot protocol, auth, mobile tests and remaining GPIO/audio gates
- `8e047b95d110df8328e5c3c56f649d90379dab1d` fix(ble): use snapshot active() guard in portable framer
- `b86e3184b987ee8fc62c156ef1c896d3f7948537` feat(ble): strictly parse bounded owner-only LDR calibration requests
- `607ea01f9def7e51312938b7fddff56aa02d42d1` refactor(ldr): share safety and busy checks between Wi-Fi and BLE calibration
- `4cb53baa2e939cd1c52346222f0845ea9dcf2833` feat(ble): authenticated LDR calibration ACK via shared firmware validator
- `03076fe7f9aed53510ae15bf62c1be0ea02c6992` feat(android): strict nonce-confirmed BLE calibration command codec
- `03a26c3e4b61472bf1cdcaea6a44c3a41ff41232` feat(android): confirm LDR calibration over encrypted owner GATT before success
- `6616cebb302a990bf77b4014f1a4ffdfa39f3a80` feat(ui): calibrate LDR over owner BLE or authorized Wi-Fi with strict readback
- `3051e05e623057647b6708fc872d6d4fcbc85999` test(ble): reject malformed/unsafe LDR calibration commands and unauthenticated owner
- `623f9e7193d0e6197f048f7edf533814d24ce04b` test(android): enforce exact BLE calibration request/ACK semantics
- `af478d6e49d01462d79a31a9b0d64c08e507f34e` ci(ble): block merge on invalid LDR owner-calibration frames

### origin/feat/v33-android-local-audio-library-20261020

- `e1ff3776ea08d460f2e943e63568abc939527832` feat(audio): implement bounded local playback queue without cloud services
- `7aa69b2dddc17c0c4aea16be13378fd0c94b4806` feat(audio): lifecycle-safe local Android MediaPlayer for user-selected tracks
- `ff05adf75aec72cb3adb2067ec19c9a0f32d4cc3` feat(audio): SAF-imported playback with local queue, controls and truthful phone output
- `eee9f2745f0c172cd8d89055a8c66d5379cf5221` test(audio): playlist capacity, SAF-only inputs, navigation and deletion
- `bad6b70e749309cc6d8e37efc234be576ce88d07` feat(audio): surface real local audio player in environmental station
- `b0cc9aa6b40d575c27427c8d575525d029b28ee4` docs(audio): local playback QA and safety blockers for 8Ω/0.5W speaker

### origin/feat/v35-ble-sensor-gpio-cas-20261020

- `dfa7a23d6ca2516744cb18f12f1a3ad8b78f2d97` feat(ble): strictly bound physical sensor GPIO CAS command to DHT11/LDR
- `d09b5c106c1851bba255d67628549a1b0d2b9b18` feat(ble): reuse firmware GPIO leases, CAS and idempotency for single-pin sensors
- `46ff7a9629949db4ae14c7eb1ba0e9ef032830c6` feat(ble): process owner-only DHT11/LDR GPIO CAS with explicit receipt
- `7d9f9a69f95718c896e87edf61cd0b471c83f57d` feat(android): strict BLE DHT11/LDR GPIO transaction frames and receipts
- `8b0eba4717967fe053ecad836e33d1278fc72c59` feat(android): verify DHT11/LDR BLE GPIO CAS with owner ACK and new component snapshot

### origin/feat/v4-android-identity-readiness

- `02b0dc770585be0e3a73ca3370074f9064594f74` feat(v4): DeviceIdentityInfo.kt identity readiness contract
- `05b09190ac595c362c1ab94ea5d5b1011f306b80` feat(v4): DeviceIdentityInfoTest.kt identity readiness contract
- `b54d64a932e13e19dd94f1be1b72311f2dae46ae` feat(v4): fetch authenticated read-only public identity via local lab
- `49c7787132b159208c19614462aba097cacab31a` feat(v4): present public device identity and honest pairing readiness in existing LabHub
- `a12c4997415fa8720cfcbcdf7b9d6fe036882f59` feat(v4): expose identity readiness from existing Devices UI, no duplicate screen
- `c4b61ca0e723827a79c25d9cadfa3f2770ca6d76` feat(nav): connect device readiness action to the existing ESP32 LabHub route

### origin/feat/v4-android-rgb-script-execution

- `3d40bbf2134682f60f8e2bfb726a7038a1bb326e` feat(v4): integrate RGB and GPIO script execution in shared Programas editor

### origin/feat/v4-arduino-cli-offline-build

- `039f98f71e22635fb469a463bb9879ba9fba037d` feat(v4): compile user Arduino sketches offline with pinned profile and no flash
- `eb9c946c40d1042029abee94f8587ccdc0b1774b` docs(v4): user sketch build workflow and firmware replacement caveats

### origin/feat/v4-arduino-cli-usb-workflow

- `1bbb530b5b754dbb06ef2f31d9f9fb227df7f4ce` feat(v4): compile exported Arduino sketches via free CLI with explicit USB replacement gate
- `940a90393dae625fbf2e73e63d5a567be9f0fcf3` docs(v4): document free offline Arduino compilation and explicit USB risks
- `b6dc18ee1994e1ab60159857274ff3061d953561` test(v4): assert USB upload cannot run without explicit acknowledgement
- `e17bfd438452356f9b37057332d5f48b34a7a9aa` ci(v4): test user sketch upload safety guards on Windows
- `f5170836a7d1f643bfd551dfcee88c57746de08c` fix(v4): keep PowerShell source ASCII for Windows PowerShell 5.1 CI

### origin/feat/v4-autonomous-rgb-persistence

- `b4af55c70531bc49c9df1147acaeada4244a9e48` feat(v4): versioned CRC-protected RGB persistence codec
- `f52977227006421d5b0481af3463fdcbe1d0e36d` feat(v4): separate NVS storage for autonomous RGB preferences
- `f7849f747c42e574d0800bde8b16f90a13a5c984` test(v4): RGB NVS codec rejects partial, corrupt and invalid snapshots
- `6391619fa539fb781ccb241bc1dd27c8ebdba5dd` feat(v4): restore RGB settings on boot; debounce atomic NVS saves during control
- `7c95372f69e574a32af42ec7a2aa07dd026a6138` ci(v4): verify versioned autonomous RGB save format under host C++
- `2849f80e2e1e9d10fa5ec40c519f0c02de5a3229` docs(v4): autonomy boundaries, NVS debounce and physical reboot QA

### origin/feat/v4-ble-public-identity

- `424ca2ad2cdd99b592a14eb1d5d83bef5c49d498` feat(v4): expose nonsecret stable identity on read-only BLE characteristic
- `7cb1cb01c08148e7058bee8f7a30c796857c451b` feat(v4): DeviceIdCodec.kt for public BLE device ID validation
- `e0a5f9d8e546bead55b67c03264eaa872c7ac9ae` feat(v4): DeviceIdCodecTest.kt for public BLE device ID validation
- `041b9ab543c6856fa7cfa1d0affeb76d02e107bf` feat(v4): read optional public BLE identity before Ready without breaking legacy RGB
- `da95360366b009e75041ff9774bf633e0065e63a` fix(v4): handle nullable BLE service during optional public ID lookup
- `f73fa037b0f6df2abdd913985f7f670d3d78521a` feat(v4): show stable public identity immediately after BLE connect, no Wi-Fi needed
- `3cf169052d77d961d03979a53378b8645057a956` test(v4): ensure BLE identity is read-only and cannot authorize controls
- `1e876d388a4ac3d7ceed230bc2adcfec6803cb4d` ci(v4): regression gate for nonsecret BLE ID readback and legacy fallback
- `47f99d314754e4e00fa629b4f612287bec70f59d` docs(v4): BLE public ID without PC, backward compatibility and security limits
- `ceca9cd2beb6675a68138f8547cd02e6262becf6` test(v4): update BLE GATT contract gate for optional fourth read-only characteristic
- `b847b67d49eb6ff411571abf6e9f43085bd2d9d2` docs(v4): register public identity UUID and GATT readiness ordering

### origin/feat/v4-code-project-persistence

- `f274365393999755cb51a02166315dffe9931de6` feat(v4): add versioned lossless local code project codec
- `6ac251833616878c76370cdd637b69decee3211a` feat(v4): atomically persist private code projects without keys
- `583c42c698574f96a18f0a4fc3ef4179542047b5` test(v4): project codec roundtrip limits corruption and upsert
- `3ef74923689299d30e440c789f860b261c1267a5` docs(v4): scope and migration gates for private code project storage
- `9e83530a0b038d1f006e775b925efc2eab912728` fix(v4): recover AtomicFile backup instead of treating interrupted write as empty
- `8d7670a7ab2cd50b74ebecbf33e63810731f3316` feat(v4): add reusable Compose project save/open/delete panel
- `5f25e675e35a93c46e4def9ffaad358a5bce2aa4` feat(v4): connect versioned local projects panel to unified GPIO/RGB/Arduino editor
- `544804395cec9d9e9082eea5f3ba1820176a2581` docs(v4): include project save/open/delete integration and physical QA gates

### origin/feat/v4-device-identity-foundation

- `e2b2916bf6a0d55e8566ea118b2fe8380ea33914` feat(v4): DeviceIdentityCodec.h non-secret identity foundation
- `2033b97a2ec33ccf2ba02d230d96518a55404527` feat(v4): DeviceIdentity.h non-secret identity foundation
- `3785d73b00dba776531abc817a9e7b801133af7f` feat(v4): device_identity_test.cpp non-secret identity foundation
- `d699a7168cd2b0705ec98d385dac77a63f4c6832` feat(v4): initialize stable NVS device ID without changing legacy RGB transport
- `12c2ee781dd92eb77db1c8ae577fb26b85d2f501` feat(v4): authenticated read-only identity endpoint with explicit security capability flags
- `01c87e5df3d18aa156332048fcc9bfed6061a06b` docs(v4): provisioning feasibility constraints and non-secret identity migration
- `0b8a273ef7e398e3be7c434ed43a485edb2df878` test(v4): enforce read-only authenticated identity and truthful capability flags
- `d5f1ef2dfd9c1981a84bee1de90c6e545ff9ade1` ci(v4): compile host identity tests and enforce truthful provisioning flags

### origin/feat/v4-editor-gpio-script-execution

- `ef8411370653b52dbf17b048ba01079f783d49f8` feat(v4): validate bounded GPIO scripts against existing V3 sequencer
- `01edc28626053084dba03f8a171a9b3c2dc69d28` test(v4): cover GPIO script safety limits and V3 contract
- `3a31dec14ad01a495a275c5a15cccd7eae08781c` feat(v4): run bounded GPIO scripts from code editor over protected debug SoftAP
- `dd94c483da0e6c5ffd84216adadb7174b3813128` docs(v4): document bounded GPIO editor execution and hardware gates

### origin/feat/v4-import-private-lab-label

- `62698ac7e697e3f9c271a2c0690a8ae795d9477c` feat(v4): FactoryLabCredentials.kt private lab label validation
- `ead541ca3dca17f0ac3ae51658481bca05df7dd6` feat(v4): FactoryLabCredentialsTest.kt private lab label validation
- `c4984bb1e75adf60f22e6ab9ada127db408a28a6` test(v4): exercise factory JSON import with JVM org.json on Android unit tests

### origin/feat/v4-physical-secure-provisioning

- `ac0aaf5ad75feea767cd8e401242a617dea87a13` feat(v4): create-v4-provisioning-kit.py unique factory provisioning kit
- `7a12691861fe82b7121ee027183cad9b31df4708` feat(v4): test-v4-provisioning-kit.py unique factory provisioning kit
- `f1c4e456e8749493d0964280270b86214088f9f8` feat(v4): physically invoked factory PoP WiFiProv mode isolated from RGB BLE
- `29e22e098dc63d5d59d0497c6afd4f521b7b100a` feat(v4): integrate optional physical Wi-Fi enrollment without replacing RGB stack
- `2ddae94c6e58fe589bf10c34952eac04d219f782` feat(v4): harden personalized provisioning CI and prevent committing device secrets
- `89047610625ef609f57306cf73d176edcb67ae5f` feat(v4): harden personalized provisioning CI and prevent committing device secrets
- `ebeb34fceed62a9fc727cf770828ae1e9647f947` feat(v4): harden personalized provisioning CI and prevent committing device secrets
- `fb946335140651d6b865e796c19f7355e5e036d6` docs(v4): practical one-time USB setup, private QR, physical Wi-Fi recovery and no BLE duplication
- `7976e3a5dca4f8f30869112254ae1967c3b095aa` feat(v4): add per-device private lab AP and control credentials to factory kit
- `952f8d202821810c9a678bbbaba2a5dc720b154f` feat(v4): rotate lab AP/key only after physical recovery gesture, no serial credential leak
- `6c467b21bd5b93d1bb4fcfaa7eb439a0cb77a3e2` feat(v4): bootstrap SoftAP credentials from private factory label, keep V3 NVS until recovery
- `73e0ec7568e5b7fdb26480292554febcefcec8f4` test(v4): verify independent private AP, key and WiFiProv secrets
- `8760627514f77f5f5b2df5ef55f6b8dcb5e3252a` security(v4): ignore local lab Wi-Fi QR secrets everywhere in repository
- `044ae000e0d6ddc678f240e9ca1dede57bec2648` docs(v4): distinguish provisioning PoP from private lab AP and explicit old-NVS migration

### origin/feat/v4-program-editor-master-plan

- `28ce7719013f72c850ef9fdd9b165a5467859916` docs(v4): master roadmap for dual programming, free tooling and safe staged merges
- `0a64d274c6ff667b258cc01167b130c07255eaf5` feat(v4): add bounded UTF-8 source import and language identification policy
- `dbaf2b4ac55d21746f491b207a9d31b54966d9e0` test(v4): cover UTF-8 limits, source recognition, and no-execution claims
- `6e4d81034b431a6274dfeac7c5013f83e95e4988` feat(v4): add offline Compose program editor with SAF import, paste, inspection and export
- `4126968312eefe556c3223d7633ab2667a0191a4` feat(v4): wire offline program editor into navigation
- `afc6bf679b958938d0fa25052d76416a7ebb0d7b` feat(v4): expose program editor from Settings
- `c60da3ff3037fea5d057d1c1cfc815959c314982` docs(v4): define editor smoke tests, evidence and non-execution gate

### origin/feat/v4-rgb-script-runtime

- `306c1b992e52d3133d5895c1dfce2301495806a5` feat(v4): add bounded, cooperative RGB script parser and runtime
- `7a36bdaac11cfb0ed05538c933ce0faca4f9ae94` test(v4): validate RGB scripts, bounds, cancellation and clock wrap
- `8c0c313b5d75d014e4da363d8c368443fcf1cda0` feat(v4): add authenticated bounded RGB scripting API without OTA
- `8d793c99f30572b6b3137721be09980ce96fe6c4` feat(v4): ensure manual BLE RGB writes interrupt scripts
- `e13ed851497cc7c919a9ea2f58894e8575ba4134` test(v4): correct invalid test case for C string terminator
- `38bbf8d1f7c5052250d5d7f1a37092ee23a74551` ci(v4): require script runtime host tests
- `45b2096f50ac2301bb50f67e20d2c32943517073` docs(v4): define declarative RGB script API, safety bounds and test gates

### origin/feat/v4-unified-code-projects

- `7af897a165e9b8c85a8548314dcb66eb259f2721` feat(v4): integrate versioned private code projects storage
- `617f18646258b042e1f2824f9abc61027cbb5f6a` feat(v4): integrate versioned private code projects storage
- `aeb5a9ed13ecc5ffe2390d86962064d61ebf5f08` feat(v4): integrate versioned private code projects storage
- `8e7391958ab3dac20f2160095f8e97eb59935c44` feat(v4): integrate versioned private code projects storage
- `48bd2bb0c460e87b9b23e4ae1749f6034750f9d2` feat(v4): integrate versioned private code projects storage
- `18d5a4d22f8815e4074b5930e17204d82ea92eb1` feat(v4): expose local project save, open and delete in unified code editor
- `02f4cf821feb8cfe9d644ed22770ffcb82fbb9fa` fix(v4): detect RGB script header after opening stored project

### origin/feat/v4-unified-gpio-rgb-script-ui

- `46d6cd3b212881f4664a40ad2ddef7f3dacf6f71` feat(v4): bring validated RGB DSL into unified Programas
- `c4e9140fc700bd062600fc67d60faca95894431c` feat(v4): bring validated RGB DSL into unified Programas
- `5c707de0b7dd493acd626b0dfd85312b1b5f11ee` feat(v4): add authenticated RGB script run/status/stop client
- `6994005e069fcbe927348736ea00819de3842908` feat(v4): unify GPIO and RGB script modes without overwriting existing editor
- `cf5647f0d429ca8c7a6d377eb040c0786ee10c3c` docs(v4): validate coexistence of GPIO and RGB script modes

### origin/feat/v5-arduino-cli-build-package

- `fb2eebc88f7043b0f8534d5bd1ae491de4e0a425` feat(v5): add offline Arduino CLI sketch package builder with strict board gate
- `48c0aafa5a534275a0a2ba01bf1c3a74a12513b3` test(v5): cover profile, bounds, manifests and fail-closed build packaging
- `63e78ec49f5f3d5dd12ddb5203a12c30ee724824` docs(v5): reproducible Windows Arduino sketch build and test instructions
- `f8e1dd7d2eec927ad831c43511e1349c3c08724e` ci(v5): run offline Arduino CLI builder regression before integration
- `1702190dd0458c44aac1bcdf2730a6e9df639519` docs(v5): fix Markdown code spans and Windows command blocks

### origin/feat/v5-code-studio-unsaved-guard

- `7af897a165e9b8c85a8548314dcb66eb259f2721` feat(v4): integrate versioned private code projects storage
- `617f18646258b042e1f2824f9abc61027cbb5f6a` feat(v4): integrate versioned private code projects storage
- `aeb5a9ed13ecc5ffe2390d86962064d61ebf5f08` feat(v4): integrate versioned private code projects storage
- `8e7391958ab3dac20f2160095f8e97eb59935c44` feat(v4): integrate versioned private code projects storage
- `48bd2bb0c460e87b9b23e4ae1749f6034750f9d2` feat(v4): integrate versioned private code projects storage
- `18d5a4d22f8815e4074b5930e17204d82ea92eb1` feat(v4): expose local project save, open and delete in unified code editor
- `02f4cf821feb8cfe9d644ed22770ffcb82fbb9fa` fix(v4): detect RGB script header after opening stored project
- `1b55b8535eff5fe06f5d69af014416168255ff62` fix(v5): confirm unsaved editor replacement and guard unreadable project library
- `ed99fd93f990d6d56c58e733fcaced5750a68c4b` fix(v5): separate Arduino native firmware instructions from GPIO runtime UI

### origin/feat/v5-editor-library-userflow

- `f274365393999755cb51a02166315dffe9931de6` feat(v4): add versioned lossless local code project codec
- `6ac251833616878c76370cdd637b69decee3211a` feat(v4): atomically persist private code projects without keys
- `583c42c698574f96a18f0a4fc3ef4179542047b5` test(v4): project codec roundtrip limits corruption and upsert
- `3ef74923689299d30e440c789f860b261c1267a5` docs(v4): scope and migration gates for private code project storage
- `9e83530a0b038d1f006e775b925efc2eab912728` fix(v4): recover AtomicFile backup instead of treating interrupted write as empty
- `8d7670a7ab2cd50b74ebecbf33e63810731f3316` feat(v4): add reusable Compose project save/open/delete panel
- `5f25e675e35a93c46e4def9ffaad358a5bce2aa4` feat(v4): connect versioned local projects panel to unified GPIO/RGB/Arduino editor
- `544804395cec9d9e9082eea5f3ba1820176a2581` docs(v4): include project save/open/delete integration and physical QA gates
- `64e9f795afde5978b819fc5c43b46b6a0861e6d8` feat(v5): add reusable Compose library screen with safe edit transitions
- `fcccd1120d863dd03123d64843b021703d8e2b2d` feat(v5): integrate project library with unified GPIO/RGB editor
- `63e23b7f1b3f80d4301e2e0f964c0b8e7b639b89` docs(v5): add project library hands-on QA plan

### origin/feat/v5-editor-project-library

- `60eaa64988dc5492725c165df2c5f40f2ec0dea9` feat(v5): bounded local editor project storage with strict UTF-8 codec
- `8f7783fcec36f9a91536fae5fd2b0f5f49e65e1d` test(v5): project library roundtrip, corruption, bounds and replacement
- `5aa71ebe0f490867e2ece751d31eae4dc36171a2` feat(v5): expose local code editor projects through existing DataStore

### origin/feat/v5-project-library-ui

- `e1202d571fd43b2cdb88aefdc3bac018981ba8c8` feat(v5): Compose project library with save/open/delete and unsaved-change confirmation
- `f4d300942921bc7fded6574b1cb81ced24e1be8c` feat(v5): connect code project UI and separate Arduino/GPIO execution guidance
- `24bc85150323badeaed44fb1d5da25f425815f67` docs(v5): editor library integration and device smoke test matrix
- `165559e4828e337dc34a5c870de54be814ac33d8` fix(v5): allow retry after validation errors while blocking writes on corrupted storage

### origin/fix/ble-owner-hex-pairing-v24-20261019

- `2f527c11518b3fd86dad9f5bebdd03dc420ffafd` fix(ble): compare owner one-time hex codes case-insensitively
- `e8b62167e58d4247aee28932aa9c06cf9872e1f3` fix(android): send canonical lowercase hex owner pairing code
- `81053c7693acb4ee2c2266536d061057a0e7ee69` fix(android): normalize one-time recovery code before BLE transmission
- `4ed4658ce7e2da4563d8a4a39660c54f81fabe38` feat(ble): present actionable pairing and recovery rejection messages
- `cb5b37ef9d5a8cc6369a02a43f910446aed17a1b` fix(ble): map firmware bad_code and window errors to clear user guidance
- `7fc64a814454e88e51aca5cc4db228676d44eede` fix(ui): keep BLE code on rejection and clear only after confirmed success
- `0f61af1b028a337b0d521b5eb1bdcbb9aeb5799d` test(ble): verify mixed-case pairing and uppercase recovery codes
- `c1d3dafb6b73d51e6512559bee6c8a853f283614` test(ble): exercise uppercase code through the firmware session protocol
- `88fb1f31279accd83619435703abc5592d380fd0` test(android): require lowercase canonical BLE code for uppercase UI input
- `b79385489a79621e0f0fbdf726fa8e557c398804` test(android): cover actionable owner authorization rejections
- `b8f64c973abc289aa16ee34528ba86e697d80fc3` docs(ble): record uppercase-code root cause, physical QA and next module gates

### origin/fix/ble-owner-rgb-authorization-20261018

- `7648228578a94b4698b51e9962fbd0189bbce299` feat(ble): persist per-device owner identity and monotonic counters securely
- `5c8908102cae59a19972a25c8b58e052ed056ca6` fix(ble): wire physical owner enrollment and cryptographic session authorization into GATT
- `8d98142f7048a0b7bc38985ac6b5f6193f2ef604` fix(ble): clear secured owner record only after firmware RESET acknowledgment
- `ac8da9b16ab05e4c30cab6fcac56e6dd3a3ac8a1` fix(rgb): enable LED controls only after firmware confirms BLE ownership
- `1a4d6e3b02c9bc974cb0056fa31310774fef2b14` feat(ble UX): physical owner code pairing, recovery and honest BLE status
- `5fb7bebb810002a518140607d9b8bfd594aba411` fix(rgb UX): distinguish GATT connected from authorized and block direct controls
- `147f984c9409c0ef42262308f51e24119ecd8e97` testable BLE RGB control gate: require transport and firmware owner authorization
- `4a0ed8e8082892fe4a044addf637da09094820e3` fix(ble): use regression-tested authorization gate for RGB writes
- `bcd5c4110d61384a810a4b6386a4f5e3d89ce318` test(ble): reject RGB until firmware authorizes owner and after disconnect
- `b8203d69a49e2bfe3e3498abd76913f34930701c` fix(ci): preserve BLE command codec contract with strict owner authorization gate
- `0176b72052c1cf62234a7757d397db32f9177203` fix(ble): bound owner enrollment reply waits and revoke stalled handshakes

### origin/fix/ble-owner-rgb-navigation-20261017

- `c36af726308dc5b11b534d3638c77938da3505d0` feat(ble): persist owner identity and monotonic AUTH counter with Android Keystore
- `61c27d22bc0a9df3880392c5cf705e866a944d06` fix(ble): complete owner challenge response and gate RGB writes on firmware authorization
- `3da982c831f08b75b53c7288951654e358f3f1ef` fix(rgb): distinguish BLE transport connection from authorized LED control
- `6b2c7ed493218921a3f10d71c6bb59a234111981` fix(rgb): explain BLE owner authorization instead of reporting fake RGB readiness
- `c8bed70d7f8fa0b6beb17d0484d69c0f09fa3c9b` feat(ble): expose physical BOOT owner enrollment and authorization diagnostics in device UI
- `7c7ddd8f4d724695c3fba951e1641f0e5e66c58f` fix(navigation): stop restoring stale nested tabs and dedupe secondary routes
- `b317a86c7ccf767c7609ffc0474a6915352a25d7` fix(modules): hardware Back closes inner module screens and reports BLE privilege correctly
- `bef255fd398ed19d752baee68f1ac66d2ea63120` fix(navigation): preserve Studio edit buffer without reviving stale nested module routes
- `b0c03c2d4932835a0cf45f01e0fe01ba0d8785b7` refactor(ble): explicit fail-closed RGB write permission predicate
- `b7f70020afd6aedaf0d1b3eff06ccf179bbb786f` testable BLE RGB authorization gate
- `b6f9757e71a8aebe9b5553c33b6bcb5856da9350` test(ble): transport ready is never privileged RGB write permission
- `0740406381a06fb6f4c49d486d9a515b1f5e4091` test(navigation): cover tab switching and returning from nested module details
- `f243323b69b7d8b3958adf758a6db4c81f2e73ec` fix(ci): preserve BLE codec validation contract alongside owner authorization gate
- `135120271d2add4f0ea0d1a34793ba1977846b6b` fix(ble): never allow legacy AUTH frames through public RGB command API

### origin/fix/ble-snapshot-busy-recovery-20261021

- `155f725543567937d79c76d51ff3162afbd0a017` fix(ble): retry transient busy snapshots with fresh sequence and bounded backoff
- `50677579c2d7df394a79b0fb5ff990600436ff3e` fix(hardware): derive authorization from active BLE or Wi-Fi transport and show confirmed DHT model

### origin/fix/compi-audio-controls-honest-state-20261022

- `34f29498c5591cb42e3cc8f892e6a05c0f8a03a1` fix(android): label unavailable environmental voice and audio feedback accurately
- `4e60b55252ba47c6264cfd26c4f51a411dc6245d` fix(android): disable DFPlayer placeholder track actions until firmware playback is wired

### origin/fix/compi-audio-navigation-robustness-20261022

- `1baf9cc2b575e3fa02570a3f2bbf5388ee1795bd` fix(nav): open shared music screen instead of embedding player in sensors
- `bf8fa515e25e99090cd6a4964525e932a3d65515` fix(nav): restore prior sensor detail when returning from music

### origin/fix/compi-audio-voice-switch-20261022

- `477700afb1aa7f86aa196e5435c7a974d0c1425c` fix(dfplayer): stop reporting UART configuration as confirmed module presence
- `bed87462b290c6eb47c116b8a91892dba0b3b4ed` fix(dfplayer): parse checksum-validated UART replies before reporting module active
- `bf159271e9fa0fa12ed8078cd2d1b186ecd24058` fix(dfplayer): declare UART response state before status collector
- `8769afae773614e9f1dc7a742f3336880504320f` fix(dfplayer): bound serial receive work to protect main-loop responsiveness
- `cbbce8b66de69515cf49543732227d9f0734c75c` fix(dfplayer): discard timed-out partial UART frames before accepting new replies

### origin/fix/compi-bounded-queue-tests-20261022

- `cb4b2da9a218eda119e895c20ad68c8ed385b8bb` fix(audio): add nonwrapping queue advancement
- `bb7e21eca99213f03c1ecdaba3e23d0c57ef485d` fix(audio): use nonwrapping queue advancement
- `8f0c45ff23a24212f6ed89bc52d22b6ec8b66380` fix(audio): cover bounded automatic queue progression

### origin/fix/compi-ci-uart-rgb-20261022

- `e44199f0d0a4298d7329d701ad6862a745c0f50e` fix(firmware): register UART hardware interface for DFPlayer catalog
- `ae175bca27fd69dca28510eda08a8026a1279fa6` test(rgb): respect hysteresis before entering hot band

### origin/fix/compi-complete-module-discovery-20261021

- `3407c393b43292549678608a8e6401c50dcf6804` fix(ui): clarify actual LDR pin and speaker playback capabilities
- `f9926b1a4a6b991f44b9e300a3eaed864ba3e825` fix(ui): clarify actual LDR pin and speaker playback capabilities
- `627a994990d1f2915910045923c7da19bbf002aa` feat(ui): discoverable Compi integrated station shortcut in Modules

### origin/fix/compi-connection-multiarea-audit-20261022

- `ba24ff2274eec0196b222ee41b28062b21bac98f` fix(ble): revoke old GATT state when radio or permissions disappear on reconnect
- `f80cd4f412900d31f809df6b1e0234b2b470768e` fix(wifi): exit VERIFYING on rejected handshake without revoking valid sessions
- `8c5f098f9cc1eb77e7e38f42d508a391074adee9` test(wifi): initial 400/409 handshake rejection is never left verifying

### origin/fix/compi-dfplayer-deterministic-mp3-folder-20261022

- `c673bb3f450dabf29181dc25df2095207a76e8d7` fix(dfplayer): address numbered /MP3 tracks deterministically
- `69ec69473ee2eacc840dfe4ae91f7231c72f20c8` docs(dfplayer): deterministic microSD folder and safe readiness checklist

### origin/fix/compi-full-firmware-five-temp-bands-20261022

- `a1adb79278a7ef2556f2c8054f94d2b28d3d083c` chore(arduino): enable complete Compi Station laboratory profile in downloadable source branch
- `5e4d27f697f9036014ae37e8593c7a23d7d6c276` fix(rgb): preserve complete firmware policy API and add five exact temperature bands
- `3e6599b43c61cf9741b4ae97495b817946f52fae` test(firmware): verify five bands, 28C yellow and stale sensor behavior

### origin/fix/compi-home-navigation-20261011

- `675566df251ca36d75e1af35fac40d3d786912f4` fix(android): make Home navigation pop to root from every module
- `c9fc87a1f56d4a1cf1ce6f89f84e748df379e3f1` test(android): cover Home navigation after LED, device picker and modules
- `8a71947d318219a8b0bd2fd5f66c8f1528aca8bc` test(android): expose stable navigation semantics tags for UI regression
- `6922158625033a7bf9e4677d7725e68651918f05` test(android): target primary navigation by stable semantics tag
- `1614caf8e8162fdbac79d5f5543f10afbe8f0a58` ci(android): compile navigation instrumentation tests on every PR
- `8454b4055ab733427c9e0a767e2fb4ed5063eb8e` fix(test): use SemanticsNodeInteraction.assertExists member without invalid import

### origin/fix/compi-inflight-results-20261022

- `c7db3f86c4816447bd2a31217b6d26a60590cbc8` fix(wifi): never return successful command result after session invalidation
- `3e774d8a4b76784dc08fc2b5c549ae8c5827cf52` test(wifi): assert in-flight commands fail closed after route loss

### origin/fix/compi-ldr-gpio32-20261021

- `bb985eb6bd8c635cdfbc1e08d2a4e12edfab5b8b` fix(ldr): default ADC1 light sensor input to available GPIO32
- `1d30db5e0a9b22a83e07b01c98ea7597f55c7c75` docs(ldr): GPIO32 ADC1 wiring on user's ESP32 board
- `8469cca9bf3c79db125b645f1083f85a595188b2` fix(android): align LDR wiring suggestion and test with GPIO32
- `3b3cc44ccda2aba7a71cc2317319a298090ad521` fix(android): align LDR wiring suggestion and test with GPIO32

### origin/fix/compi-ldr-wiring-ui-20261021

- `d125671bbd262b272174a5d9d83fbce802162d1a` fix(compi): document actual analog wiring and BLE LDR calibration
- `fd7c01ac6ba67a5adb3063b1d4a05fc07cc39601` fix(compi): document actual analog wiring and BLE LDR calibration

### origin/fix/compi-led-nested-scroll-crash

- `7ed514202caaf7c4569d49884dc0e2222855586e` fix(led): support non-scrollable embedded RGB controls
- `8298aec2b9d7257e35f6dde465533d121c600434` fix(led): remove nested effects scrolling in unified LED view
- `569f5ce73e910e108a555591c600129dcf9c4774` fix(led): replace unbounded favorites lazy grid with finite flow layout
- `6310fe0bf34629fe65613196cf90d9a5c74977e4` fix(led): compose unified RGB sections with one vertical scroll owner

### origin/fix/compi-modules-oled-consolidation-20261022

- `f350ffe02012b8c5366c038fc78597a1bd2ee8ae` fix(ui): consolidate compact Modules hub and OLED scene controls on latest main
- `827fe6e771f02e796627abbcbfa0564b2db88b8f` feat(firmware): consolidate pumpkin and cat OLED scenes on latest main
- `158a821662c8e4f86b9a123736232f7c0587d05b` test(android): route connection regressions through dedicated device entry
- `3a8a6bce4e68c62b75814f500fb5c20cd56a9727` feat(firmware): bounded SSD1306 contrast control through authorized OLED command
- `421a646e43e8431bdef93ce896cbfc5ec088c45d` fix(firmware): use unambiguous NUL sentinel for contrast input
- `4061c7baf58d722166d6a768d35a7babc9cfc2c6` feat(android): add authorized OLED contrast slider with apply acknowledgement
- `85063caeb797b0768e0dd8fe162d777cb8842a62` fix(api): authorize pumpkin/cat scenes and validate OLED contrast end-to-end

### origin/fix/compi-navigation-module-cleanup-20261022

- `93889f8d812bf784a268d6b6b44cd8b5c5270ede` fix(nav): route module connections and device inspection through navigation helpers
- `9934c111c2c87e82a2f5f434c85b3201b2d85f6b` ui(modules): reduce station shortcut clutter without changing callbacks

### origin/fix/compi-navigation-regression-20261022

- `1cfa0af7bfe9cb086d74162c9a31df8ee8c41f80` fix(nav): reuse safe nested and top-level navigation for module entry points
- `47c59a9d4f87609cfd00ed1eccee4a2c2f6cded6` test(android): cover repeated connection back navigation and module scrolling
- `a2b58ec4d7833fb855620b325188899ea0f0249d` test(android): scroll LazyColumn to DHT22 before interacting on API30

### origin/fix/compi-oled-ble-load-20261022

- `cc10086e197693adcaba978fe5a9e8c968555e19` perf(esp32): bound OLED refresh to 1 Hz to reduce BLE and Wi-Fi contention
- `59c376ebca889541eab71b3bdc99d73ab6a81454` fix(ldr): restore previous calibration if NVS persistence fails

### origin/fix/compi-oled-contrast-contract-20261022

- `c4e3d89dfecf16263417aba38c3b2b3dade0314f` fix(firmware): support validated OLED faces and contrast commands on current main
- `81637bf488eb7dbbe4e8cb2e5446c3ebb12d3e22` fix(firmware): support validated OLED faces and contrast commands on current main
- `48bac937d1f89334bd2199278a6418e0d0000467` feat(android): add SSD1306 contrast adjustment to existing station controls

### origin/fix/compi-oled-dance-http-contract-20261022

- `ebe669b2181a6395f0df929c50487e6755c7fdcb` fix(oled): allow authorized dance expression on firmware HTTP endpoint
- `d4ea37959c824d4f02e86da3bf5c9b5c29c27a0a` test(contracts): catch OLED command mismatches between Android firmware and HTTP routing

### origin/fix/compi-oled-detail-cleanup-20261022

- `04de09481d63c94e4c273dcb94d482cb30f99687` test(nav): use current modules_connection card after UI refactor

### origin/fix/compi-operations-invalidation-20261022

- `8a32ecdb1bbd9ccbe7c0364f9a8e219315d568fb` fix(wifi): revoke visible queued and running operation state on session invalidation
- `e4be360b0f93003d2095df2a0c810d09e5e15e68` test(wifi): verify running command status immediately revoked on route loss

### origin/fix/compi-premium-rebase-20261022

- `c803d735f0b148e34d4ba31c9a727a0bf88a771f` feat(ui): OLED scenes and contrast controls while retaining latest station UI
- `191916878a7c8c63860b94721549623668020c31` feat(firmware): bring OLED contrast and Halloween scene protocol onto latest main
- `ad462bbe12cc91c2ffd7407340c7325d7f35d2a6` feat(firmware): bring OLED contrast and Halloween scene protocol onto latest main

### origin/fix/compi-qa-ux-logic-20261021

- `ecd714b8f2eb59c97bfeb78f909d5a74924a33f6` fix(audio): reject malformed SAF content URIs and cover invalid inputs
- `9fd971ce72894fe32603f3c1fa8d82e9997878d5` fix(audio): reject malformed SAF content URIs and cover invalid inputs
- `3179571bc5230f603ed6ff549eff38680bde575e` fix(wifi): invalidate authorized session on route loss and reject stale readbacks
- `4077252fb743b3210901111659d6cbc7eb69d16d` test(wifi): protect against route loss and delayed authorization replies
- `b30e0983f5e62fadad64e0fdbabae26f85a2e13c` test(audio): use actual escaped newline in invalid URI regression

### origin/fix/compi-rgb-hysteresis-regression-20261022

- `aead388c7c6a50efe41485db92b4d9fc14f9549c` test(rgb): honor 0.5C hysteresis at warm-to-hot threshold

### origin/fix/compi-sensor-freshness-ux-20261022

- `c7478fabc6664456a81d0b730e7f2d96d98e1db7` fix(sensor-ui): prevent persisted telemetry from masquerading as live DHT

### origin/fix/compi-session-epoch-reliability-20261022

- `0efce16cdaf4b4f50edf4576f38d967c4b6afd75` fix(wifi): invalidate verification epoch when route drops and suppress stale operation confirmation
- `f38238b006993919e901b19ba39c19fae96a8bdb` test(wifi): cover route flap and stale command confirmations

### origin/fix/compi-stale-queued-commands-20261022

- `34bc62380d268b60ed580f63b8c8809fa22a0bc3` fix(wifi): reject queued commands created before session invalidation
- `b6a15a374d900cc7d29d3e8d11dd83c33d9887ef` test(wifi): reject commands queued before route loss

### origin/fix/compi-station-ux-telemetry-20261023

- `7685397744f6b8a540b7137f01289ec0d458000b` fix(android): keep station tabs readable and clarify live LDR readings
- `77d8edb4d75f6a990da793e960474cc762e6a0ae` fix(android): honor chart time ranges and timestamps in dashboard
- `50671eb7bf87b97379b4fdd65e06f79d0c16ebba` fix(android): add chart scroll import and time-aligned tap selection
- `4bc8ff7501f57029ae9c1afc475d83ffcc2bc5af` fix(compi): consolidate DFPlayer hardware and Music entry, show honest live status
- `ddecd9fb1608fc2ea7d5db7ccaf8bdadafada3c8` feat(audio): wire quick-track and voice actions through explicit hardware callbacks
- `25be0dfdc42c07f9d4ab51f3eb083ffd9301c07a` feat(audio): connect DFPlayer playback and voice API to station Music screen
- `569bc92224d539ef9b5ab29585bb287ed7c3141c` fix(audio): display voice switch only from ESP32 command response
- `808f2c8ff7a77e2351de788b42c859d28140a777` fix(audio): update voice control after accepted command

### origin/fix/compi-status-poll-provenance-20261022

- `44e300ff79f8b7660c03459abd427e2caade7240` fix(hardware): downgrade failed status polls from live to last known
- `5af4f79f0f57a8df8d7ce870df809b252ef32911` fix(hardware): downgrade failed status polls from live to last known
- `162c3a836c858f78d697796e39eb342823d70e5a` test(hardware): assert failed status polls cannot remain live
- `745d101b42e239f46cfe170197176f734d7b3fda` fix(ui): OLED operative only with live proof; separate cached soil telemetry

### origin/fix/compi-telemetry-freshness-model-20261021

- `24aebccc5888c9c5a595164cc0a366fffa28eec3` fix: keep telemetry provenance and DHT model consistent
- `a4baa407e83049ce6eec8ba25cc5363b2750e51e` fix: keep telemetry provenance and DHT model consistent
- `7f6ab44e9008e4d59e1f1ff209b677cd0c266b21` fix: keep telemetry provenance and DHT model consistent
- `5fa718766773a7be1ab2f03ffc0311d699e44c2f` test: regress stale telemetry on missing invalid and failed BLE updates

### origin/fix/compi-v13-firmware-runtime-stability

- `0c79dfb04947d78e7cf984c1c7a80a8816e54840` fix(automation): count elapsed intervals rather than rendered ticks after loop delays
- `00bdf23db211d27109d133b2d3a1720a02dee5ea` fix(rgb): persist only manual preferences; prevent temporary script/automation states from replacing NVS snapshot
- `bb4113aeef8cfd9dc001eacd5d0d777304efbed4` fix(automation): enforce duration under blocked-loop catchup and separate transient RGB commands
- `a5256b6d6cb8c11f735fa2805e07f5857bf7dc20` test(automation): verify missed-loop interval accounting, parity and timer rollover
- `4bd1515d49543778356bf39d20386e5db95616b4` test(protocol): prevent automation catchup and transient NVS regressions in static contract CI
- `a5ba0abf51a41f74cbefb461a2be7f39bcfbc028` feat(esp32): build reproducible complete Arduino source ZIP without provisioning secrets
- `878603e17dd76ca90f835ff478ccc34dbf2cd00f` feat(esp32): build reproducible complete Arduino source ZIP without provisioning secrets
- `5244020c9b2278c0fcc6d141dede5cc2524e1d50` ci(esp32): publish complete Arduino sketch ZIP with release checksums
- `948ba6653ddba8badb597076cf250ecdd09955b5` test(packaging): import hyphenated user-facing firmware packaging command correctly
- `8f2701afb1cc879be90f74f00c4d41a17ad1453f` docs(firmware): V13 Git master plan, physical QA and exact Arduino IDE upload instructions

### origin/fix/compi-v18-ci-coverage-docs-reconciled

- `e639615cf8c117bb04f5287931a32df939b829c9` ci(v18): cover remaining host capabilities OTA preflight and automation clock
- `c4f1706bfef92a6d8abaef42979a0c0142b0a8c3` docs(v18): correct BLE enrollment and current Android integration limitations

### origin/fix/compi-v18-ci-host-coverage-docs-20261017

- `15c8bbc17dfb2ccdda8e2af61e6f00a721679bd9` ci(compi): run omitted capabilities, OTA preflight and automation clock host tests
- `d7566677c05e2d7b1b08452f3a7c464c111af7a1` docs(ble): document current V16 handshake, Android integration blocker and SoftAP HTTP

### origin/fix/compi-v18-rgb-effect-uptime-overflow

- `68e5753628313c3fd5e40d42974972a6249bc416` fix(rgb): avoid 32-bit overflow in long-running animation phase

### origin/fix/compi-v18-wifi-key-comparison

- `62c5131a7b7b5f2e0aae258d68c364e383bac914` fix(wifi): constant-work lab key comparison and accurate trust-boundary comment
- `f4bb2499bc5d5c0b0901e0d60f4d56b6693a470f` test(ci): update HTTP key security gate for constant-work comparison

### origin/fix/compi-v18-wifi-key-comparison-reconciled

- `26859ce152e6291bf513c531ed97d8b1274bde98` fix(wifi): compare all HTTP key bytes and clarify isolated credentials
- `02ef4e024f9b1e3489cbe844f11be9f2b47b7c72` test(wifi): protect constant-work header validation in CI

### origin/fix/compi-v8-reliability-audit

- `fb145340b03cc8f575f90b8b84a28116efe0207f` fix(firmware): reject malformed RGB commands before interrupting scripts
- `7466d87c877110d949e31927e123bf73a47174ea` fix(firmware): reject malformed RGB commands before interrupting scripts
- `81867da19a3187d7ee7ba1d5c4ed8cf7acab0528` fix(ble): prevent scan-reconnect races, require MTU and publish confirmed device identity
- `6f7a8ceffe1d509825ab29bde29ba2b3f890d667` fix(ble): persist last device at app scope on verified READY
- `ff2c8950653bb94f0f8a1d40a47ca422809a7a7b` refactor(devices): remove screen-dependent last-connected persistence
- `334bb617ad0ad4d83691033fc95400497ae153f9` feat(lab): one volatile shared session with stale-response rejection
- `4d04c6e95fd77c43477e9f205ce5aa29b415d21f` refactor(led): share volatile lab session, bound queue and ignore stale network results
- `c441b267654ee70d972e26513c3a4f674cabcc0f` feat(lab): scope Wi-Fi control key to one app session across modules
- `9f8bd5af0706113fed3349dac51dd94a91f00e5e` feat(lab): scope Wi-Fi control key to one app session across modules
- `95a646d777d9c10aa4737fcbad2deecb9d2a67a0` feat(lab): scope Wi-Fi control key to one app session across modules
- `0b9363f27c67b18a14dfce7eb7b9a2b7bd8aa7b3` feat(lab): scope Wi-Fi control key to one app session across modules
- `6e41db7995f9dce97e7657ff6be0bc8975a1f503` fix(rgb): protect editor examples and never report timed-out RGB as synchronized
- `7595df59c25a77b05b430b6c4d99dacf589bbf08` fix(rgb): protect editor examples and never report timed-out RGB as synchronized
- `6f20882604e07469076a6fb4f5167538d9f53139` fix(rgb): accept late matching acknowledgment without restoring stale state
- `e187a7077d7d1478d5454ec6402a2ee6adea9e80` feat(firmware): bounded BLE command inbox with host contract tests
- `fa803baa10d8c7e644b45b0538b79e6b9854e4dd` feat(firmware): bounded BLE command inbox with host contract tests
- `dbf26f77b62a2b8a4261c115d92295554a80927f` fix(firmware): serialize BLE RGB commands on ESP32 loop with bounded inbox
- `a8b7727b429791888e94981fde21d19d3ff6d418` fix(lab): arbitrate HTTP requests from all Compi modules
- `ae81478e20f4073feeacc0303133ef8c9d122671` fix(lab): serialize Wi-Fi RGB writes and readbacks across screens
- `92a8ba72b5abe8d6690ab10d197c52037669b7bf` fix(lab): serialize experiments and Studio commands against Wi-Fi LED control
- `a14ec0c80fc7bc0fd6c687cd74bd853528d0eb08` fix(lab): serialize experiments and Studio commands against Wi-Fi LED control
- `f8f7cf6839b1afc8d4c78a1ff76107a6277e8891` test(lab): prove stale key/transport responses cannot restore an invalid session
- `b0cefa994fbf8bc795d316c7b73567eae8ec2b90` ci(firmware): verify bounded BLE inbox against malformed, overflow and FIFO cases
- `6f69e86945222ec141435e87a91bbc22d5e082a7` fix(rgb): track confirmed transport state revisions for causal readback
- `018c8b31f5dfda4f9c74bcf5b5d23f609803df9c` fix(rgb): track confirmed transport state revisions for causal readback
- `33a59b13186dc93584118efbf4069fe36933fa6c` fix(rgb): track confirmed transport state revisions for causal readback
- `0fdb7aeb1c2a552aa3817c41139b8f545a276301` fix(rgb): track confirmed transport state revisions for causal readback
- `146980d5e2ab02920661a37bda531387a0ea5ece` fix(led): acknowledge RGB only after a NEW matching device frame
- `45daf65501343fefc7d969fc9143ed512665e728` fix(led): acknowledge RGB only after a NEW matching device frame
- `0ca7d4bd0bf95c9dc5cec72d001dfb2b76fedff3` fix(android): avoid Kotlin property setter JVM signature collision
- `abde36160e826be60fc3c9e76a795bfc81e58482` fix(android): avoid Kotlin property setter JVM signature collision
- `059053636c85582bf69db7958366960a715d11b3` fix(android): avoid Kotlin property setter JVM signature collision
- `c3a76ffde14c12a4d19a02abb650d4e1801474e4` fix(android): avoid Kotlin property setter JVM signature collision
- `130bdb0273f1442d123a03a88848e3fcedfbe179` fix(android): avoid Kotlin property setter JVM signature collision
- `0b7e9bb27d86999820fa47495371a8abaa4b0d29` docs(compi): comprehensive V8 reliability plan, regression matrix and non-negotiable release gates
- `2db554104b0f5647e3d0b0bf12a1aea5c3540a36` fix(ble): preserve current connection when scanning cannot start due to permissions
- `4ee81fd58bbdc3cfd55cbdc94eb0925fa905424a` test(rgb): require fresh readback with matching state for success
- `978f2c507707ccce3a8cc9a54e377f386b58efd5` test(rgb): require fresh readback with matching state for success
- `633ccbe4ad65d88c2498cf2a34829827f5eea175` fix(rgb): require new full-state readback for LED synchronization
- `1baef4202f764bafba117604a621c7b0d76c6f8f` test(lab): prove cross-module HTTP mutex prevents overlapping network calls
- `465639a7703912f7a4345050d304518f9ddb3425` test(protocol): prevent regression to callback-side PWM and invalid script cancellation

### origin/fix/compi-v9-home-density-navigation

- `086b7d027bc8b5c516d7108daa1d8734a9b4ccb8` fix(ui): remove redundant top app bar and navigate settings from dashboard
- `cb829dfa649d11304d822f5a361f0d0e6d2fa9b9` fix(ui): compact Compi home and restore labeled quick-action icons

### origin/fix/compi-v9-navigation-studio-gpio-telemetry

- `c1077088ee981b7bbc5265071e56e98fdc070a1b` feat(nav): remove redundant Center tab, expose Home shortcuts and reliable nested back actions
- `5f4edd1f50892cdfcd47def09425bcc84e000b55` feat(home): consolidate devices and hardware tools into Home without losing capabilities
- `9fbaa3b2822c16ef942781ac40c57336d4e63e73` test(nav): assert all four tabs and nested Back from devices/hardware
- `6021afbe30ab13e655ab0dd3052d64d0bc53eef4` feat(firmware): report real leased GPIO owners and manual level over authenticated SoftAP
- `e0b1610cefedf391f6f93fa8f5a455fa50052483` feat(android): request authenticated runtime GPIO ownership from ESP32
- `2557556cd25ba8a9386f246e9537bcfde6dd6f33` feat(hardware): display live per-pin ownership and manual levels from ESP32 instead of static board map
- `13fea99b150ea4d5a53e9759e83fac8dd37cc2bb` fix(studio): separate GPIO/RGB/Arduino drafts, move mode selector above editor, add progress
- `ac2b77af8e43f9a9e1908bd08a7abae4160e905f` feat(devices): show BLE scan/handshake progress and honest transport capability limits
- `f7cece2540619894925769b6423f16d199b6245c` test(ui): add stable Studio editor and engine tags for draft regression
- `f75b97a5b65d16becc0afd9b475ae6a9245b8490` test(ui): verify GPIO and RGB drafts survive engine switching and Home navigation
- `58951e410328925b73035af08e8112f5198764c4` test(contract): guard runtime GPIO leases API and independent Studio drafts
- `3f873e75575ccbbdae5d817172ed531051e2d223` feat(ble): bounded read-only firmware pin snapshot codec and host tests
- `1f92477e696748f296fddc14411ff3bb46dcc556` feat(ble): bounded read-only firmware pin snapshot codec and host tests
- `070593ca41594de1795a47ed3e70bbd88778964e` feat(ble): share actual live leases with compact read-only Bluetooth codec
- `6fb14c72795a1e8230485bf46dd92cb2058d1b92` feat(firmware): advertise optional read-only BLE GPIO lease characteristic updated in main loop
- `6234bd9ad5f6b9e2d808cda45044a768f1a6730f` feat(ble): strictly validate optional read-only live GPIO lease frame
- `3467bb98744c494e3d2dd33f60b974a4942f7a81` feat(ble): strictly validate optional read-only live GPIO lease frame
- `d821ff0ef1f105cce0325c77514cacab0c827213` feat(ble): optional read-only GPIO occupancy polling serialized behind RGB GATT writes
- `495ddd85f3fcc21d91b44ee92b1a0300b596bd5c` feat(hardware): show live GPIO ownership from Bluetooth read-only or Wi-Fi protected API
- `de81e4ee6cc2989d42ec2b3a5edae8b80f14bb44` feat(hardware): show live GPIO ownership from Bluetooth read-only or Wi-Fi protected API
- `b84333b4b982f8024efaea1c294cdb35a6bb3bfc` ci: test read-only BLE GPIO snapshot framing on host
- `24a1d94598b3f2ae8e6a94fb816f919d2e1e3232` docs(test): update BLE contract for optional read-only GPIO characteristic
- `16de396bab3e7689cdc44eb9860428828addd6b4` docs(test): update BLE contract for optional read-only GPIO characteristic
- `234ffd83bed5b3a08477dc75e2e2f112e167277c` fix(android): explicitly opt in to Material3 nested TopAppBar API
- `a5680417fa0cd19218149b9c30d62de4fbcf0b32` feat(studio): run built-in BLE RGB breathing safely without pretending to upload scripts
- `2fe9f78f995a399891438efceaf38283efff61fd` feat(nav): supply confirmed BLE session to Studio for built-in RGB effects
- `5c5b8db989043c970c754f4e2efe094bd6deeb5d` fix(nav): migrate legacy V8 Center route to Home without reviving redundant tab
- `661d8efa1560064e614ca59f9b6441f1468ab5d7` docs(v9): physical QA checklist for navigation, Studio, live GPIO via Wi-Fi/BLE and security gates
- `231143db44da549467203b4a95c08bf7532ca96a` ci: cancel superseded branch builds while keeping latest Android/ESP32 verification
- `3e033e709ea3bf6e8bdc70c6c0199696ff2e10b5` fix(studio-ui): prevent Copiar button wrapping on phones and use readable monospaced source
- `cecb3cf5b587f07a0074b94e2aa5386ea1376728` test(protocol): fail CI if BLE GPIO telemetry gains WRITE or loses GATT serialization
- `94fb75be0744d7f429e43db25fbeaa63e61ec963` fix(nav-studio): preserve editor drafts through Home and clarify RGB/GPIO engine mismatch
- `a5b2eb714aabb368bc2ed241af5e1e047459c7c5` fix(nav-studio): preserve editor drafts through Home and clarify RGB/GPIO engine mismatch
- `d37fac25df9bbff547de6229d25fdf267d8249f2` fix(nav-studio): preserve editor drafts through Home and clarify RGB/GPIO engine mismatch
- `4c77e8af929b9d5d1a94d324a7681584ce16a99e` fix(nav-ui): avoid duplicate Hardware Lab headings in nested toolbar and test selectors

### origin/fix/dht11-gpio16-signal-and-truthful-module-states-20261018

- `121404dfbcf3e2933e9424a553ee9b8d76efae25` fix(dht11): edge-aligned one-wire capture, open-drain release and GPIO16 failure diagnostics
- `53890ff80b966600bd52ecf38a86a25bce0ad4da` fix(android): show DHT GPIO16 signal reason; stop claiming disconnected sensor or detected LED
- `9874fdce5f1721ff995807deb08251db915aa685` fix(esp32): enlarge authorized component status for DHT fault diagnostics
- `1076cf89c9923271b6be69f2b674c9c4586864ab` fix(board): protect GPIO16/17 when PSRAM is present and log safe pin capability
- `8137e5a52ea68fcba9032a13c2c22ec33dfd9615` fix(diagnostics): show actual selected DHT model in missing-response guidance
- `4545727408993f95413b0f53de9d4ddedd50dc72` docs(hardware): safe GPIO16 differential test, DHT fault codes and honest PWM state

### origin/fix/estacion-compi-voice-cycle-20261022

- `257f736e754c023cf19994330c167d77b5b6310f` fix(firmware): delay Compi greeting and repeat persistent temperature cues
- `da419b2af44dea1837d8a55791b36cd82c886439` test(firmware): cover delayed greeting, repeat cooldown and stale sensor suppression
- `e40806e2eb62e707e3c3d7f5bd056e28161546cf` ci: run environmental voice regression test

### origin/fix/hardware-lab-scroll-and-gpio-ux-20261016

- `a27c82a6c8ab981624af834e4db9809f99f0c6f2` fix(android): bound Hardware dashboard sections and add scroll regression hooks
- `162b06ff58e02a066efe51792145b71f7202ba02` fix(hardware): distinguish ADC1 alternatives from required pins and improve pin editor
- `677c4ea51fb9c9ac439c27235efc1ddbf2d3ef97` fix(hardware): distinguish ADC1 alternatives from required pins and improve pin editor
- `834a32489fe5bde0fd2af68f63ede4d57fe7ac09` fix(hardware): distinguish ADC1 alternatives from required pins and improve pin editor
- `6073983a01645b612290b936bf7eac8ae3e9892f` test(android): cover full Hardware dashboard scrolling with no ESP32

### origin/fix/recover-main-project-tree-20261022

- `82298111bfe4267a72bb96d49bc2073758b26e6b` restore: recover missing complete Android firmware tests and build project without reverting newest main files
- `3ad6a22578c9207e5f9156beabcd94e3400b3b44` fix(rgb): apply five requested temperature bands to recovered current main project
- `57c0dfa2da528dcb12a1104801628ef69b3e0193` test(rgb): add boundaries, stale data and 28 C yellow regression
- `6cdd37131e0dd7e7512df40aa59145d83722ba12` fix(firmware): preserve DHT humidity input contract used by current main
- `0bfde8bcc9dabf5fa79f8a5ba56a1f1738befa64` test(rgb): update legacy expected colors to agreed 5-band palette
- `caf36521a85614524b26d5391d48f6eaddd81915` fix(charts): expand short recording sessions without fabricating missing time
- `28d6d66cce27a5b82d04241f09f419df5f7867c6` feat(charts): show actual reading, visible time coverage and honest timeline labels
- `fc1c89128a3555f3d95976f5efd654ede4363d78` test(navigation): align stale module connect tag with current station entry
- `9c787d4a0ca8f164e24df8b170f7f5a3ff9e58f7` fix(charts): prevent 0.4 C and 0.7 percent drift from looking like large spikes
- `20afe7a41681f44cd111213adc0d5e80560110a6` test(charts): verify short-session zoom, honest sample count and conservative Y scale
- `3e2c616a54558919d7ea4371d64206e3a946f6af` fix(android): correct escaped Kotlin interpolation in chart caption

### origin/fix/softap-first-boot-nvs-20261017

- `94b234bf1aa13733c582b5922d90a9daa6688518` fix(esp32): initialize absent owner NVS namespace on first boot
- `aacaf5a03f0723d70ee164982b024bad33b6d9fb` test(esp32): add NVS namespace host double for first-boot reproduction
- `a19774f86078a9efb1bbed312534bc7a58f25382` test(esp32): reproduce first-boot NVS regression and fail-closed states
- `0d10b03c9daf8359fb1953906645325aa3fcc883` ci(esp32): prevent owner NVS first-install boot regression
- `9689cb84e88278ae62aac556ca30acbfdbbdb910` fix(wifi): prioritize SoftAP after STA restore and detect lost AP
- `b723f8fde1934cefbe6e28cb60d9db2d429c614f` diagnostics(esp32): expose boot stage and AP state over serial

### origin/fix/softap-startup-stability-20261017

- `24177c00ba0991b0b0faf9670d9b13ee83ac9d62` fix(wifi): avoid reinitializing STA after starting laboratory SoftAP
- `21d5163d21a6da9accc76b0383c5f904f58aff70` fix(android): disclose throttled Wi-Fi scans instead of implying fresh ESP32 discovery

### origin/fix/temperature-rgb-five-bands-20261022

- `e9253b523c3de155475f1685bef8a296c8665bb2` fix(firmware): implement five temperature RGB bands with stale-data guard and hysteresis
- `b2620337caac7a5a01a004fe3dd4b319ecc2434b` fix: correct downward temperature hysteresis boundary
- `504026c9d8bf8f7bfca0c8d4e4fd6a4193a60e5b` test: cover temperature bands, 28C yellow, hysteresis and stale readings
- `161796340115e36cdd5f12a57d43c88f2088409f` test: isolate exact thresholds from hysteresis continuity

### origin/fix/v16-ble-discovery-build-20261017

- `f5c74ecbde060d313cc407682668c026bb0616de` fix(esp32): configure BLE encryption on BLESecurity instance
- `fc2b6f857c37cc1dc1d10fa5581848175576248f` fix(android): tolerate ESP32 BLE advertisements without service UUID filter
- `225e20c0e143e91fb57a1d8eb74ba46f60cd45b1` fix(esp32): remove unsupported encryption-level API on Arduino core 3.0.7

### origin/fix/v17-activate-inactive-module-cas-20261017

- `aea475a5bee6d82f92f8618d8c8dd03f306e9b02` fix(android): permit initial CAS activation when inactive firmware module correctly reports no leases
- `d849e6382d140f21e315ebb48a6e97b00042e626` feat(android): share strict CAS rules for unleased vs active ESP32 modules
- `5d74e26fd666db3e499ad265c7fac277fdc7160d` refactor(android): use shared CAS policy for initial activation and live pin reassignment
- `d201206437d2478e3131e8319ef19d123aa0f4aa` test(android): cover unleased first activation, valid CAS and stale/invalid firmware reports

### origin/fix/v17-authoritative-empty-inactive-gpio-20261017

- `e16a4b35d07bcc08127882c5989d0d665f511091` fix(firmware): report only leased GPIO for active modules, none for inactive drivers
- `d1c3aae9c66923ec5466870ba2bb5d25973f3b0a` fix(protocol): enforce empty authoritative pin arrays for inactive components
- `7898b4807cc3ba676cd4296183ca6de712f6be6b` test(firmware): inactive modules must never claim default GPIO as leased

### origin/fix/v17-f09-board-pin-remap-and-compatibility-20261016

- `6ec3914f1e050677d5d08a6904c147759a5435bb` fix(f09): per-signal safe GPIO remapping aligned with firmware PinManager
- `72e8b9c3587be424b28c5b0c927caf79ecf22dde` test(f09): guard imported ADC1 RGB OLED conflicts and boot pins
- `8165066feef265af4ef336e8ada621eb41a3b0ab` test(f09): assert all safe import diagnostics independent of display order

### origin/fix/v17-module-compose-balanced-conditions-20261017

- `b092fcb91d38d00e0cf34ca4ea65fa9093a57f55` fix(v17): render invalid wiring gracefully instead of early returning from Compose
- `63c1d2384bd8e42c919efab297f8838df82fd28e` fix(v17): remove composable early return when module has no compatible profile
- `036d14763cf3030cced88744660696c8d0a2f504` test(android): exercise dynamic signal-count transitions without Compose slot corruption
- `092f22691a241ce994c90e3310ba905a95bdc4fa` test(android): use built-in semantics assertion without invalid import

### origin/fix/v17-optional-modules-dont-block-softap-20261017

- `383a74ae8a2210c839410dbd0f05b0c2c69dbfb9` fix(firmware): allow absent optional sensors without blocking required RGB or SoftAP boot
- `3e9fb0798e83998a4e34498ab6e07e2fd338c75a` fix(esp32): continue SoftAP/BLE boot when optional sensors/OLED are absent
- `b47d562f6fdf2d0f077a1006f0dd058a8f8b907a` test(firmware): absent OLED cannot block essential modules; late activation and fail-closed required boot
- `009f003cf498c1d1ea6f2802bb8e2e8b3f53d02c` test(contracts): require safe essential RGB boot with independent optional components

### origin/fix/v17-soil-live-calibration-guard-20261017

- `7e269753070b0fa700bd80ed1d76d73cca8c16c3` fix(sensors): refuse cached or unauthorized ADC1 readings in soil calibration wizard
- `4bc45eafefb3b2715e821fbcabb1c1884ca73e0e` fix(sensors): only enable soil calibration capture for fresh authorized ADC1 telemetry
- `5a9f7c637aa3036f452ca55ee919684d61963063` test(sensors): guard cached unauthorized invalid and wrong-device soil capture

### origin/fix/v18-catalog-presence-authority-20261017

- `cf20eca155daf6828ee53676d46ec5cf5d742ea9` fix(v18): exclude catalog-only drivers from detected component identity
- `6a6e3a621033f011eadb36408d6a76ba515b6a97` test(v18): no fabricated hardware detection from compiled catalog entries

### origin/fix/v18-gpio-unconfirmed-response-20261017

- `9c85f719ffc7bdbc9751a3c746629a7751625653` fix(android): preserve uncertain GPIO outcome when receipt/readback is lost after POST
- `17121bee6f27c312cba0648c964192c15693bbcf` fix(ui): distinguish refused, verified and unknown GPIO apply outcomes in wizard
- `31d490109299e8365957d6c25f54aa8bf6eb90d4` fix(ui): report session change after GPIO receipt as unconfirmed, not rejected

### origin/fix/v18-soil-calibration-on-rewire-20261017

- `5b729f07e0838b57c2f890ec0ee541a6922c90e2` fix(firmware): invalidate soil dry/wet calibration after ADC1 source rewiring
- `6d6ff4d1297eb0e3c463bde42891ef2d8ee220aa` test(firmware): changing ADC1 pin revokes dry/wet calibration and stale percentages

### origin/fix/v19-ble-discovery-rebased-20261017

- `796b28db981d7d14334edeceb607f83b66b6ea42` test(ble): portable matching policy and candidate regression
- `9ce9c80d3c9ff26c72c6a8b7222613d15ab9d259` test(ble): portable matching policy and candidate regression
- `1a03e92848d580bd57936b0032c480a8e1b16157` fix(ble): robust legacy service name scanning and evidence counters
- `77f0f82f2a695457467fd1037bfd08c06348fb0b` fix(ux): concise BLE results and safe conditional Compose wiring groups
- `c80601fb9f08e95822437b4b3fe777e2d82c4e94` fix(ux): concise BLE results and safe conditional Compose wiring groups
- `e42b6b049e5461a0ce50e9544af061091ec49a2e` fix(firmware): advertise explicit BLE name and restart safely after disconnect without undoing NVS/SoftAP repair
- `2d3095df5694e46e1b1867e47566e86520802c14` style(v19): eliminate duplicate connection title and shorten Wi-Fi/USB explanations
- `55fdfad52e5248692d10c16e7615d0dcb18bb9ee` fix(ci/ux): restore accessible identity disclaimer and import Compose testTag
- `dd1e0872297b776d88272373e280b688b4dd0b64` test(v19): assert compact BLE/USB destination semantics instead of removed filler headings
- `a1cceba8f14e6f81ce2269d1f3da87f9bec7785b` style(v19): name BLE diagnostic action for its actual Hardware destination

### origin/fix/v19-ble-discovery-recovery-20261017

- `375e5db2ff48581bc35e7004907bedeba81d84b3` fix(ble): centralize Compi/legacy advertisement matching and truthful scan statistics
- `68af2d43deb167b5d333d1b177e856b7b9675809` fix(ble): scan by name/service/remembered device, batch callbacks and actionable scan diagnostics
- `6ec8906895db41af9cd9fb7c0aa779f236439a22` test(ble): discovery against legacy/current service, nameless prior address and unrelated devices
- `b6ee9197b6c75d957a20c005752114bce1341443` fix(firmware): expose explicit BLE name scan response, start BLE before Wi-Fi, defer reconnect advertising
- `6e3f71166700071db9de0a6abb4816470cd9f6fa` fix(ble): include previously selected ESP32 address when scanning nameless advertisements

### origin/fix/v3-ble-write-watchdog

- `dd29c8d4dff1ae4778750996bf9062870e410ca4` fix(ble): prevent silent GATT write queue hangs and validate commands
- `f6f26d9e35b117c7c4b8c7db52882a46edeb0462` fix(ble): recover cleanly when Android rejects a GATT write
- `14258fc1e981033ce0a2d9c4386af67a94a7bc7e` test(ble): gate stale callback and write timeout regression
- `cdbc7e3c91d4875387028d0e69f646ad2e69ba96` ci: check fail-closed GATT write path in host tests
- `1e15535f6cd4ddf6861224725f5c6a87c09fbd6a` docs(qa): exact BLE command/readback and physical regression protocol

### origin/fix/v3-experiment-time-accuracy

- `c093a25277f46a7ceee5431ac6d9fc7d66a4c35f` fix(lab): preserve deadline-based sequence timing under Wi-Fi loop stalls
- `83653c4ad71a545f17773e302737ac3c85d59370` test(lab): simulate blocked loop and millis rollover with safe GPIO release

### origin/fix/v3-softap-wifi-routing

- `7d9ccdf98323886f837939b811b74e9cc82517d1` fix(wifi): define explicit ESP32 SoftAP route requirements
- `d447e55386d8d5bae5b120146f52541133eda6c3` fix(wifi): bind SoftAP lab HTTP to explicit Wi-Fi network, never cellular
- `9bd519c968be260771741250a23dc54035935530` feat(ui): route laboratory requests via actual ESP32 SoftAP connection
- `047ef82bad495233753ae7dd9cb39c903ed3555f` fix(android): declare access to Wi-Fi transport route discovery
- `bd508199f50ec5abdb1cec4270e416d5ba48a8e6` test(wifi): disallow mobile-data fallback and incorrect AP gateways

### origin/fix/v3-unified-board-profile

- `78d3048689af6d7887bef205f778ee0513dbce02` build: define verified 4 MB ESP32 board profile
- `bba6e750ebcb8603336c169a16a9390efd5d2f2a` build: enforce audited partition profile for local compile and flash
- `000e09193bf9b501d996968afb8f874725e8f1fa` ci: resolve ESP32 core and FQBN from single source
- `6c2ac1dda7eae5d2882d303ed38f64c83d118053` test: fail on mismatched 4 MB ESP32 partition definitions
- `7ea69bdb15cba079550d318e3b0b40c710ccff45` docs: link local and CI builds to audited firmware profile
- `116c42721a700a80ccbd3e0f2ac918e696f13a19` fix(build): repair profile parser and validate local FQBN
- `426732a02a936b2775627db519c3a12bbf78502e` fix(test): allow digits in ESP32 profile key names

### origin/fix/v34-ldr-dht11-gpio-idempotency-20261020

- `52d7bfee2889f81713006074d6b1a2b4ea96e23d` fix(gpio): align replay guard with supported DHT11 and LDR modules
- `62b05efb5550b8df49a60f29cc47be338b31e4eb` test(gpio): cover DHT11 and LDR replay safety without accepting unknown modules

### origin/fix/v5-code-studio-final-consolidation

- `43ffd4c1fd118bd5222cf22d37338ea8a6af9eaa` fix(editor): confirm discarding changes and fail closed on unreadable project library
- `33f7673492be1305875585ec1c53d42b1d2e1823` fix(editor): distinguish Arduino C++ from GPIO and restore saved RGB/GPIO mode

### origin/fix/v5-unsaved-guard-on-current-main

- `32cde09773ca0e485ce6badf4f43802c2337ca9d` fix(v5): warn before discarding editor drafts and block writes on unreadable library
- `03b2caa171df148599a4e7fd675f72f5c3f25403` fix(v5): show native C++ build separately from runnable GPIO and RGB scripts

### origin/fix/windows-arduino-cli-discovery

- `f988638cf12a881a6a178562851e42fee5e35839` fix(windows): discover Arduino CLI before Android build and support explicit path
- `58e2c7c72fc682189214b83a0dbc2c078bea685b` test(windows): verify CLI discovery, override and missing-tool fast failure
- `aebdac40839cd1bab7465cdc05906224f33edd04` ci(windows): validate CLI discovery with a local test-double, no firmware flashing
- `9582d89bc0cafed13007cd95f57dff655e1aa7bb` fix(windows): also detect Arduino IDE user-local bundled CLI
- `d9528a1bd54266ec0658d1541df1f483fa1f1d2c` docs(qa): recover missing Arduino CLI without rebuilding Android or flashing board
- `8e69f42d5c3746c32209fed40c5e48e6638c0177` docs: render Windows CLI commands as proper Markdown code blocks

### origin/fix/windows-board-profile-digit-keys

- `c402dd78c9ddbbb86dac984f0d69c251afb7c9a2` fix(windows): accept digits in ESP32 env keys and enable no-build profile validation
- `aebc0d5e59e194687d530dbca19b9501fa1b7c34` fix(windows): robust profile parser that accepts digits and profile-only validation
- `d82cd97b7fc0baed860a586391227e3ee07839be` test(windows): exercise actual profile parser and malformed input cases
- `9aed9a2ff766edd229217be98eddbe0becd66adc` ci(windows): run actual PowerShell board profile regression gate
- `eaa13cf7263ebb9ebb6f198a94d9a755b9a9e40f` test(profile): reject future regressions of PowerShell core version parsing

### origin/refactor/v3-rgb-command-codec

- `9709b171e8baf4d63acb7f6ed5103ff2875b1871` refactor(firmware): isolate V1 RGB command parsing from device state
- `05e891218954028dafd6fdb62929bae76860da7d` refactor(firmware): route legacy BLE and SoftAP RGB to one strict codec
- `2a93ab319df4e53676a08278bff43a5e6d9214c9` test(firmware): host regression for BLE/Wi-Fi RGB command format
- `0a89b5ee80ac877ac9b10d5c3b8bfa28b0d432ab` ci: require host regression of shared RGB command parser
- `c6b7b4c5d20fe479ca89445f6da4d7bf10fb326d` test: verify codec rather than duplicated inline FX parser

### origin/security/v3-disable-unverified-release

- `ae19c57eb06deb3a4d6e80ca14e5d07159bb8df6` security(android): disable production release variant while BLE remains open
- `9ffd730bd2afd39b75ee5e9512fe3d64778a799e` test(security): block insecure distributable release packaging
- `a27bb502e3a5491f6f05a619db3b9e023b8d7c2b` ci: assert unsafe release variants remain disabled
- `753750cb32b779a79c38d885dab67ad927486aa2` docs: define physical BLE security gates for production release

### origin/style/v19-clear-module-studio-hardware-20261017

- `522ca1cb2469e3a52045693b9e8fae261aa12720` style(v19): shorten high-noise screen copy while retaining safety/provenance states
- `d9ae7da495b7bd7d0906001022690474cf5498dc` style(v19): shorten high-noise screen copy while retaining safety/provenance states
- `22e29d1ba4c6f914aa28817c961acd0d90b51690` style(v19): shorten high-noise screen copy while retaining safety/provenance states

### origin/test/v17-mobile-ux-api30-api35-20261017

- `a359cf1b7b25e49af6b5685e11479a22b62d7720` test(v17): instrumented scroll, modular wiring and navigation on Android API 30 and 35
- `ac57975efaddaefaf3c4f63a402ae24b4f5ce3ac` test(v17): stable Settings screen semantics selector independent of duplicate labels
- `9b832b97f13f3f3308de9f0a2ce7dced10e3782e` test(v17): stable Settings screen semantics selector independent of duplicate labels

### origin/test/v17-real-android-emulator-regression-20261016

- `038c46a4224924315b1ec70f72c682a32d6ca7d1` test(android): execute Hardware Lab scroll regression on API 30 emulator
- `4f0e042e809b38b3501d231fcf602e571f09e49f` fix(ci): run emulator Gradle command from Android directory in one shell
- `43070d0c79d7984c756ef36bc8100f03fca2173c` fix(android): isolate Hardware LazyColumn composition by tab to prevent SlotTable crash
- `97f1c2d06f5501456191401bd3d6dcd34012266d` fix(android): remove remaining concurrent scroll after Hardware section switch
- `3640d87f8886c1dd9cddf13f5e1120765eaaf25e` fix(android): replace Hardware lazy composition with bounded scrollable Column to avoid SlotTable exception
- `b15e903a744a2fa15cae9b28f318808465961276` test(android): exercise non-lazy Hardware scroll and horizontal tabs with performScrollTo
- `6d16e7dce13a0127745dc7267b0070a45d12675a` test(android): exercise non-lazy Hardware scroll and horizontal tabs with performScrollTo
- `e14de407a72a8f27b626dbb82c8367934527652f` test(android): isolate initial composition vs scrolling and log exact crash checkpoint
- `6fd03872e04ab4c2550288724bdef665dcb0b407` fix(android): remove premature return@Column from empty telemetry history, restore bounded lazy UI
- `1ae17b14a931ed1027841b9b39a1ec46f84ef921` fix(android): remove early composable return in dynamic GPIO validation card
- `8c4e31c1ffcb6529972c78ee646791271e6fb34e` test(android): rerun original lazy scroll test with actual Compose early-return fixes
- `6fc0c813e8dd0f8ec1dc3d443597897a997720d1` test(android): keep original lazy navigation assertions after surgical Compose fix
- `3951533aeb277e97effa8bd64bb1254e6235923a` test(android): bring offscreen Hardware tab row into view before switching to Project

### origin/test/v4-provisioning-compatibility-probe

- `5a960cf13b01607fa059d9a6d130533a84c64e92` test(v4): prove pinned Arduino core exposes Security2 + BLE provisioning headers
- `7d71f977777cf9dc5c58dea7a4f5f43f674ff524` ci(v4): check Arduino 3.0.7 Security2 provisioning headers before integrating
- `19e56649d65300280790d1094b2a79b096f0daed` fix(ci): verify OTA budget for explicitly named standalone provisioning probe binary
- `860e94ea04f3ff094c43f093489700315942b0cd` ci(v4): use correct standalone sketch binary in OTA slot budget
- `85ec8f6ecf1fd4276c2285fa66c1281e579686ad` fix(ci): correct Python raw-regex for accepted Arduino binary filenames


## Targeted source review

### Consolidation follow-up (same work session)

The baseline inventory below deliberately stays pinned to `eccdd22` and the
234 fetched branch tips. The consolidation branch subsequently merged PR 259
and completed the useful V35 DHT11/LDR BLE GPIO prototype with a bounded parser,
16-hex nonce, isolated replay ledger, authenticated queue, actual UI caller and
status readback. Therefore the baseline's V35 deferral below no longer describes
the delivered implementation. The five-color RGB legend is corrected as well.
Audio/OLED transport parity and UART state verification are implemented in the
shared media backend described by `docs/COMPI_CONSOLIDATED_PROTOCOL.md`. No remote
branches were deleted; superseded history remains recoverable.

This section is maintained separately from the mechanical inventory and is preserved by the generator. Source conclusions below compare the pinned `eccdd22fa049fea65fc31254cd5c28c508241e46` baseline unless another revision is stated. An aggregate patch match proves historical inclusion, not that every old implementation should be restored.

### Sensor behavior retained and superseded

| Area / branches | Evidence in baseline | Disposition |
|---|---|---|
| `feat/compi-ldr-persistent-calibration-20261022` | `firmware/esp32_rgb/esp32_rgb.ino` has `saveLdrCalibration`, `restoreLdrCalibration`, a setup restore, `compi_ldr` / `cal_v1` NVS packing, ownership/raw-sample checks and explicit-write-only persistence. `ModuleWorkspaceScreen.kt` waits for firmware acknowledgement and describes persistent storage. Main commit `f0fe427` contains the historical squash. | Preserve current version. Unlike branch commit `1d1f298`, current `calibrateLdr` restores the preceding valid calibration when NVS persistence fails (`59c376e`), instead of simply clearing it. |
| `feat/compi-ldr-ux-calibration-guidance-20261022`, `feat/compi-ldr-visual-calibration-20261022` | Current `ModuleWorkspaceScreen.kt` contains an ADC meter, calibrated percentage, captured dark/bright references, span validation, contextual capture guidance and explicit uncalibrated ADC labeling. Main commits `01c4c40`, `a7ac997`, `f430c14` record this work and later refinements. | Already included / refined. Never substitute raw ADC for percentage or lux. |
| `feat/compi-humidity-aware-rgb-20261022` | `EnvironmentalRgbPolicy.h` uses valid finite 0..100% humidity with the sample-age limit, cool tint at >=70%, warm tint at <=30%, and independent calibrated LDR brightness. Firmware feeds humidity from the same DHT as temperature, including DHT22 fallback. `tests/environmental_rgb_policy_test.cpp` checks fresh, stale, NaN and absent humidity. Historical integration is `cb52ddf`. | Already included, then superseded from four bands to five. Merging the old tip would remove `hotMaxC` / `Extreme` and regress current five-band behavior. |
| `fix/temperature-rgb-five-bands-20261022`, `fix/compi-full-firmware-five-temp-bands-20261022`, `feat/compi-rgb-five-bands-reconciled-20261022` | Current policy defines Cold / Comfort / Warm / Hot / Extreme with boundaries 20/26/30/35 C, 0.5 C hysteresis and bounded brightness. Main `2d9296b` and `cdb59b8` reconcile five bands while retaining humidity/LDR. | Keep reconciled policy, not parallel older implementations in `core/tests`. Their standalone tests are superseded by active `tests/environmental_rgb_policy_test.cpp` and the CI host-test command. |
| `fix/compi-rgb-hysteresis-regression-20261022` | Current tests hold Warm at 30.0 C, enter Hot at 30.6 C, hold Hot at 35.0 C, enter Extreme at 35.6 C and hold Extreme at 34.8 C. | Historical test used 31/31.5 C. Reapply the intent, not the obsolete numeric threshold. No missing hysteresis fix found. |
| `feat/compi-station-live-charts-20261022`, `feat/modules-real-charts-dht11-ux-20261018`, `fix/dht11-last-valid-reading-20261023` | Station invokes the real model-scoped `SensorDashboard`; chart windows are 5/15/30 minutes; history is device/model scoped; invalid/stale samples create explicit gaps. Recent valid history may be displayed for 120 seconds with an age label, never as a live value. The last-valid fix is already an ancestor (`f76452e`, merged by PR258). | Preserve current charts and DHT display semantics. Chart time/selection refinements `77d8edb` and `50671eb` are already in the pinned baseline. |
| `fix/dht11-reuse-proven-probe-full-firmware-20261018`, `feat/dht11-isolated-probe-ci-20261018` | Production and standalone probe share `DhtProbeCore.h`; the capture is open drain, checks sync/frame/checksum/payload, and restores interrupts with a scope guard. CI compares the two headers and runs `tests/dht11_probe_test.cpp`. | Proven probe implementation and checks already preserved. Physical hardware success is not inferred from host tests. |

A concrete residual UI inconsistency was found in `EnvironmentalRgbControlCard.kt`: the pinned baseline still contains four visual color swatches and the text “azul, verde, ámbar o rojo” while the reconciled firmware has five bands. Consolidation should add the orange `255,125,20` swatch and use Extreme red `230,35,35`, with five-color copy. This is a consistency repair, not restoration of an entire historical branch.

### Pending integration versus historical variants

- PR259 source `origin/fix/compi-station-ux-telemetry-20261023` is divergent from pinned origin/main because its 8 branch-only commits are new. It changes `DashboardScreen.kt`, `CompiAudioPlayerCard.kt` and `ModuleWorkspaceScreen.kt` to connect station/Music actions and preserve chart access. During this audit it was integrated locally by root merge `7f072e8`; the mechanical table intentionally remains pinned to preintegration `eccdd22`.
- PR256 / `fix/compi-audio-voice-switch-20261022` remains a historical draft status; its useful audio/voice work was manually consolidated previously. Draft status alone is not evidence that all 5 commits are missing, or that the complete old firmware should be merged.
- Existing RGB overflow arithmetic uses `uint64_t` multiplication (`esp32_rgb.ino`), and constant-work credential comparison was reconciled by main `767448c`. Their earlier divergent branch names should not cause these old versions to be blindly reapplied.

### Remaining divergent branch triage and explicit limitations

All 50 D-class branches plus the 1 M-class branch were inspected for their merge-base-to-tip changed paths and branch-only production files. This is a narrower check than full semantic verification of every commit. The full table retains D/M labels honestly; none is silently promoted to “fully integrated.” The original branch refs are preserved.

| Branch-only file family found by the path audit | Current counterpart / assessment |
|---|---|
| V15 `core/modules/ModuleManifest.kt`, `ProjectModuleHints.kt`, `core/ui/modules/SensorWidgets.kt`, `feature/modules/ModulesScreen.kt` | Current component catalog, model/status/renderer packages, `core/programs/ProjectModuleHints.kt` and `ModuleWorkspaceScreen.kt` replace the earlier fixed RGB/ADC-only model. Old manifest parsing only allowed GPIO 27/25/26 RGB and GPIO 34 ADC. Restoring the old parallel screen/model is not a current integration requirement. |
| V17 `ComponentWiringTransaction.h`, `ComponentWiringProtocol.kt`, `ModuleGrouping.kt` | Current `applyComponentPins`, `ComponentApplyReplayGuard`, `ComponentApplyCasPolicy` and `ModuleWorkspaceGrouping` implement later CAS/readback/rollback/activation paths. Old helper only allowed active DHT22 and soil; it is not a substitute for today's broader module apply path. |
| V4 `FactoryLabCredentials.kt` | Earlier private factory-label prototype; current physical onboarding/enrollment flow and main `6496b8c` supersede this legacy fixed-SSID JSON model. No secret data was copied or printed in this audit. |
| V5 `EditorProjects.kt`, `ProjectLibraryPanel.kt` | Current `CodeProjectLibrary`, `CodeProjectStore`, `CodeProjectsPanel` and draft replacement policies supersede the duplicate 8-project library. The active library supports 12 projects, timestamps, bounded storage, strict source/name/UTF-8 validation and atomic persistence. Main `4dbdedc` explicitly consolidated the guard without duplicate project storage. |
| Alternate five-band tests under `firmware/esp32_rgb/core/tests/` | Current CI compiles `tests/environmental_rgb_policy_test.cpp`, covering reconciled five bands, hysteresis, humidity, LDR and invalid freshness. Absence of the duplicate file path is not absence of the feature. |
| V35 `BleSensorPinProtocol.kt`, `BleSensorPinRequest.h` | **Real unintegrated prototype**, not historical proof of missing current Wi-Fi GPIO behavior. Its 5 commits add owner BLE GPIO CAS for DHT11/LDR, but `git grep applySensorPinBle` finds only its method definition: no UI caller exists even at that tip, and the series adds no tests/CI. The parser indexes `p[10]` without first bounding the truncated frame after the nonce, so it must not be imported unchanged. Retain as a deferred feature candidate requiring parser bounds checks, transaction/session race tests, and actual UI wiring. Consolidation does not claim full BLE/Wi-Fi GPIO transport parity. |

Recent UI/OLED variants mainly overlap `ModuleWorkspaceScreen.kt`, firmware `esp32_rgb.ino` and `LocalWifiControl.h`; their unmatched patches include amended/split squash integrations and superseded layouts. The broad recovery branch `fix/recover-main-project-tree-20261022` touches 370 paths because it restores a previously missing tree; present-day file existence is not justification to replay its old versions over newer source. Detailed runtime/audio/OLED corrections are handled separately in the consolidation implementation and its verification.

This report is a complete branch inventory plus a focused source audit, not proof that every line of every abandoned prototype is desirable or integrated. Remaining D/M classifications, physical DHT/LDR calibration/reboot behavior, RGB colors and audio/OLED interaction require their stated source/hardware verification before stronger claims.
