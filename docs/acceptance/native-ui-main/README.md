# Native landscape UI integration

Base: current main `e6915b2df9b92e7be412f8c0847f6f2e930cbe30` (v0.64/code72).

This is a UI-only transplant from PR103, not a merge of its old v0.63 tree. It brings the original landscape journey cover, mountain-seal badges, grouped profile settings, leaf feedback and measured adaptive tab dock. The map dock now uses the current light/dark palette. Both map controls and v0.64 replay controls use the dock's measured clearance.

Preserved unchanged: single AMap engine, first-map consent and its explicit decline, historical replay engine, GPS, SQLite schema3, stored data, signing, package identity, version and official update behavior. PR104 was checked but remains independent and unmerged; its journal/postcard/onboarding proposals are not silently imported.

Excluded: PR103 preview workflow, KVM permissions, release scripts, debug preview label/version, update-page override and publication request files. No new dependency.

Validation is being completed before merge. Local build/test logs and native emulator evidence will be summarized here; previous PR103 APK screenshots do not count as validation of this integration. Formal APK signing/release is separate and not implied by merging UI source.
