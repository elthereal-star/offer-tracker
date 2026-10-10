# Read-only Capacity Smoke Test

The repository includes a non-mutating k6 baseline at `docs/ops/k6-readonly-smoke.js`. Run it only against an isolated staging environment or an explicitly approved maintenance window.

```sh
k6 run -e BASE_URL=https://staging.example.com \
  -e ACCESS_TOKEN="$ACCESS_TOKEN" \
  -e VUS=20 -e DURATION=10m \
  docs/ops/k6-readonly-smoke.js
```

The script calls liveness and, when `ACCESS_TOKEN` is supplied, the paginated application list and statistics endpoints. It never creates, updates, deletes, uploads, sends SMS, or calls billable AI endpoints. Record p50/p95/p99 latency, error rate, CPU, heap, MySQL pool usage, Redis latency, and AI provider calls separately. Increase VUs in stages and stop if error rate exceeds 1% or the p95 threshold is breached.

This is a baseline, not a million-user capacity claim. Establish target RPS, RTO/RPO, and scaling limits from measured results before public load testing.
