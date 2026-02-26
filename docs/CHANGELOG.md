# PostureIQ — Changelog

---

## [2026-02-25] — Scanner UX Overhaul, Remediation Status Tracking, Unified UI, OpenClaw Integration

### Scanner — Data Table Redesign

**Before:** Scanner findings were rendered as individual Material cards in a vertical list. At typical screen sizes, only 2–3 findings were visible without scrolling.

**After:** Findings are now displayed in a **Material data table with expandable rows** (`mat-table` with `multiTemplateDataRows`). Clicking a row reveals the finding description inline. This provides **3–5x more findings per screen**, significantly improving triage speed for large scan results.

| Change | Details |
|--------|---------|
| Card → Table | Replaced `@for` card loop with `mat-table` and expandable detail rows |
| Actions | Inline buttons replaced with a `mat-menu` dropdown (more_vert icon) containing Remediate, Auto-Fix, and row-level actions |
| Column alignment | Fixed `display:flex` on action cells that was breaking table layout; actions column constrained to fixed width |
| Expand chevron | Added `keyboard_arrow_down`/`keyboard_arrow_up` column for row expansion |
| CRITICAL severity | Added CRITICAL severity badge styling to match correlation engine output |
| Keyboard accessibility | Table rows and expand toggles are keyboard-navigable |
| Tooltips | Added tooltips on severity badges and action menu items |
| OPEN badge | New status badge for unresolved findings |
| Subscription cleanup | Added `takeUntilDestroyed` to SSE and observable subscriptions in the scanner |

**Commits:** `f9717d7`, `658af92`

---

### Remediation Status Tracking (End-to-End)

Added persistent remediation status tracking from the backend entity layer through the correlation engine to the frontend UI.

#### Backend

| Change | Details |
|--------|---------|
| `Finding` entity | Added `remediationStatus` field (`REMEDIATED`, `FAILED`, `PARTIAL`, `null`) persisted to the database |
| Correlation engine | `CorrelationService` now **skips remediated CONFIG findings** — fixed vulnerabilities no longer produce correlated attack paths |
| Identity risk scoring | `IamRiskService` excludes remediated findings from risk score calculation, finding counts, and high-severity counts |

#### Frontend — Scanner

| Change | Details |
|--------|---------|
| Status badges | Remediated findings show a green `REMEDIATED` badge; failed remediations show red `FAILED`; partial shows amber `PARTIAL` |
| Visual de-emphasis | Remediated rows are visually muted (reduced opacity) to draw attention to open findings |
| Filter toggle | New "Remediated" toggle in the scanner filter bar to show/hide fixed findings |
| Fix button hidden | The Fix and Auto-Fix actions are hidden on already-remediated findings |

#### Frontend — PostureIQ

| Change | Details |
|--------|---------|
| Correlated finding cards | Remediation status badges and visual de-emphasis applied to PostureIQ correlated finding cards |

**Commits:** `82bb09c`, `8ac0aa0`, `06d4c71`, `5cac42d`

---

### SSE Spinner Fix

**Bug:** After an auto-fix completed, the "Live Progress" spinner continued spinning until the SSE connection timed out (~30s), because `isStreaming` was never set to `false` upon completion.

**Fix:** The SSE stream is now stopped and `isStreaming` set to `false` when the POST response arrives, so the spinner disappears immediately.

**Commit:** `8ac0aa0`

---

### Unified Page Headers & UX Polish

Applied consistent styling and layout improvements across all pages.

| Change | Scope | Details |
|--------|-------|---------|
| Shared page header | Global (`styles.scss`) | New `.page-header` CSS class with consistent spacing, typography, and alignment — migrated scanner, dashboard, and PostureIQ headers to use it |
| Scanner info bar | Scanner | Removed redundant scan results summary card (severity counts duplicated the filter bar); replaced with a compact info bar |
| Dashboard identity table | Dashboard | Uniform header treatment matching scanner table; added row hover states |
| Empty states | All pages | Added contextual Material icons to empty-state messages |
| Button alignment | Global | Fixed icon+text misalignment in MDC buttons across all components via `::ng-deep .mdc-button__label` flex layout (toolbar nav, scanner filters, export buttons, dashboard CTA, dialog actions, login) |
| Finding card alignment | FindingCard | Header row vertically centered; body indentation adjusted to align description under title |
| Remediation button | FindingCard | Matching border-radius for visual consistency |
| Auto-fix dialog | AutoRemediationDialog | Fixed simultaneous spinner+icon+text render; now properly toggles between loading/ready states |
| Responsive before/after | AutoRemediationDialog | Added responsive grid for before/after state comparison on mobile |
| Compliance badges | PostureIQ | Fixed indentation to align with card content |
| Login form | Login | Added `autocomplete` attributes for password manager compatibility |
| Dialog accessibility | Dialogs | Added `aria-labelledby` IDs to dialog titles |
| Touch targets | Scanner | Scan button sized to min-height 44px for mobile touch targets |

**Commits:** `5f2e029`, `dc809ab`, `7d6f437`

---

### OpenClaw Skill Suite — Chat-Ops Integration

Added a complete **OpenClaw skill suite** enabling conversational access to PostureIQ from Slack, Teams, and Discord via the OpenClaw agent gateway.

#### Skill Commands

| Command | Description |
|---------|-------------|
| `postureiq scan` | Trigger a security scan and return summary |
| `postureiq status` | Show current scan status and finding counts |
| `postureiq findings` | List findings with optional severity/resource filters |
| `postureiq remediate` | Get AI remediation guidance for a specific finding |
| `postureiq fix` | Trigger auto-fix with approval workflow |
| `postureiq identities` | List high-risk IAM identities |
| `postureiq compliance` | Show compliance posture summary |
| `postureiq report` | Generate and download PDF/JSON report |

#### Infrastructure

| Component | Details |
|-----------|---------|
| Auth library | Shared JWT auth module with token caching and HTTP helpers for all scripts |
| CI/CD scanner | CLI scanner with JSON and SARIF output formats; threshold gating for CI pipelines |
| GitHub Actions workflow | Template workflow with SARIF upload to GitHub Security tab |
| Cron scripts | Daily scan alert digest and compliance summary notifications |
| API reference | Documentation for agent context (LLM prompt grounding) |
| SKILL.md manifest | Environment requirements and capability declarations |

**Commit:** `5e1b497`

---

### Full Commit Log

| Hash | Summary |
|------|---------|
| `5f2e029` | Fix UI alignment issues, enhance UX, and add product roadmap |
| `82bb09c` | Add remediation status indicators to finding cards with filter toggle |
| `8ac0aa0` | Fix live progress spinner persisting after auto-fix completes |
| `06d4c71` | Hide Fix button on remediated finding cards |
| `5cac42d` | Propagate remediation status to PostureIQ and correlation engine |
| `dc809ab` | Fix button icon/text alignment in PostureIQ, scanner, and finding-card |
| `f9717d7` | Replace scanner finding cards with expandable data table |
| `658af92` | Replace actions buttons with dropdown menu and fix column alignment |
| `7d6f437` | Unify page headers, fix scanner redundancy, and improve UX across all pages |
| `5e1b497` | Add OpenClaw skill suite for PostureIQ CSPM |
