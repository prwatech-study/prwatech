# Context pack scan (P4)

Vendored from Skillama Context Hub. Jenkins hard-fails Decision-context deploys/PR diffs that violate the frozen pack.

Requires env: `HUB_BASE_URL`, `HUB_CI_TOKEN`, `SERVICE_ID`, and a Decision ID via `DECISION_ID`, `BRANCH_NAME` (`ctx/dec_…/…`), or PR body/diff citation.
