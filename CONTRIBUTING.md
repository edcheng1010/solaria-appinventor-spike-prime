# Contributing

> **Unofficial integration.** Independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](NOTICE) for trademark and licensing details.

Thank you for your interest in contributing. This extension is part of the [Solaria](https://github.com/edcheng1010/solaria-hub) open-source robotics ecosystem — its purpose is to be the reference SPIKE Prime bridge for the [Solaria Standard Protocol (SSP)](https://github.com/edcheng1010/solaria-hub/tree/main/spec).

Before contributing, please read:
- [ARCHITECTURE.md](ARCHITECTURE.md) — the technical design, including the critical architectural rules (BLE UUIDs, RSSI staleness, null-safe callbacks)
- [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) — the five-phase roadmap and current SSP integration target
- [CLAUDE.md](CLAUDE.md) — concise summary of the same architectural rules

---

## Ways to Contribute

### 1. Report Issues

Found a crash, unexpected behaviour, or BLE reliability problem?

1. Check existing [issues](../../issues) for duplicates.
2. Open a new issue with:
   - Hardware: SPIKE Prime hub firmware version, Android device model, Android OS version
   - App Inventor extension version (or "built from main" + commit hash)
   - Minimal reproduction steps
   - Any logs from `adb logcat` filtered to the BLE / extension package

Bugs that block the classroom-reliability goals (multi-hub stability, reconnection, ghost-device filtering) get priority.

### 2. Improve Documentation

- Fix typos, clarify explanations, add code examples to block descriptions
- Translate user-facing docs into other languages
- Write tutorials for specific classroom use cases or robotics challenges

Small doc PRs are very welcome — they're the easiest way to get started.

### 3. Implement a Phase Item

The [IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) breaks the next ~12 months of work into five phases:

| Phase | Status | What's needed |
|---|---|---|
| 1 — Foundation | ✅ Complete | — |
| 2 — SSP v0.6 migration | ⏳ Next | Hub-side Python rewrite, Java SSP infrastructure, component migration |
| 3 — Post-MVP block expansion | After Phase 2 | New Sound / System / Music components, IMU blocks, threshold events |
| 4 — Client/bridge split | After Phase 3 | TransportProfile abstraction, bridge extraction |
| 5 — Multi-hub support | Long-term | LWP, EV3 (RFCOMM), Arduino bridges |

Each phase has explicit acceptance criteria. If you want to take on a section, open an issue first so we can coordinate — phase work needs to land in order (e.g., Phase 2 should not start until Phase 1 acceptance criteria are reverified).

### 4. Propose a New Block or Feature

Have an idea for a block the extension should expose? Open an issue describing:
- What the block does (from a user's / student's perspective)
- Which existing SPIKE Prime hardware capability it maps to
- How it relates to SSP v0.6's standard commands (or if a v0.7+ spec addition is needed first)

For SSP spec changes, the discussion happens at [solaria-hub](https://github.com/edcheng1010/solaria-hub) — see that repo's CONTRIBUTING for the wishlist process.

### 5. Test with New Hardware

Have a SPIKE Prime variant, firmware version, or Android device that isn't yet validated? File a "compatibility report" issue describing what works and what doesn't. This information directly feeds Phase 2's acceptance criteria.

---

## Development Setup

### Prerequisites

- Java JDK 8 or higher
- Apache Ant
- App Inventor source tree (only needed for full extension compilation — see [docs/COMPILATION_AND_DEBUGGING.md](docs/COMPILATION_AND_DEBUGGING.md))
- A physical SPIKE Prime 3.x hub for end-to-end testing
- An Android device with BLE support (API 18+) running MIT App Inventor's Companion app

### Build

```bash
ant build
```

Compiled `.aix` lands in `build/extensions/`. See [docs/COMPILATION_AND_DEBUGGING.md](docs/COMPILATION_AND_DEBUGGING.md) for the full build process including the App Inventor source tree configuration.

### Testing

Most behaviour requires a physical hub — the App Inventor emulator cannot test BLE extensions reliably. The IMPLEMENTATION_PLAN.md acceptance criteria for each phase document what must be verified before merging.

Unit tests run via:

```bash
ant test
```

---

## Pull Request Process

1. Fork the repository.
2. Create a feature branch (`feature/my-contribution` or `fix/short-description`).
3. Make focused commits with clear messages — one logical change per commit. Match the existing commit-message style (no `Co-Authored-By` trailers; lowercase `type:` prefix for conventional commit messages — e.g., `fix:`, `docs:`, `perf:`).
4. For code changes:
   - Run `ant build` and `ant test` to verify the build passes
   - Verify changed behaviour on a physical hub (or note in the PR description that physical-hub testing is pending)
   - Update relevant docs (ARCHITECTURE.md if rules change, IMPLEMENTATION_PLAN.md if phase scope shifts)
5. Open a PR with:
   - What changed and why
   - Which phase / IMPLEMENTATION_PLAN section the work belongs to (if applicable)
   - Test plan checklist (what was verified, what's outstanding)
6. Respond to review feedback. The maintainer (currently Edward Cheng) reviews PRs personally.

---

## Code Style and Conventions

- **Java:** Match the existing code style — no formal formatter enforced, but new code should be visually consistent with neighboring code. Use the existing `@SimpleFunction` / `@SimpleEvent` / `@SimpleProperty` / `@DesignerProperty` patterns from the 5 working components.
- **Python (hub-side):** MicroPython-compatible, no external dependencies beyond what SPIKE Prime FW 3.x ships with. The current hub-side program is embedded in Java as a string constant; Phase 4 will move it to its own repo.
- **Architectural rules in [CLAUDE.md](CLAUDE.md) (Rules 1–8):** these are not negotiable. UUIDs, RSSI staleness, null-safe BLE callbacks, scanning-state management, and reflection-parameter-type correctness must be preserved. PRs that violate these rules will be sent back for revision.

---

## Code of Conduct

Be respectful, constructive, and welcoming. This project follows the spirit of the [Contributor Covenant](https://www.contributor-covenant.org/). Harassment, personal attacks, or unwelcoming behaviour are not acceptable.

---

## License

By contributing, you agree that your contributions will be licensed under the project's [Apache License 2.0](LICENSE).

---

## Questions?

Open an issue with the `question` label, or — for SSP-spec or ecosystem-level questions — open a Discussion at [solaria-hub](https://github.com/edcheng1010/solaria-hub/discussions).
