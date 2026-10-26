# usage

Durable query usage for the MVP, backed by V11's `query_usage` table.

- `TokenEstimator` approximates missing synthesis counts at four UTF-16 characters per token, rounded up. Query input uses the question plus unique full cited document bodies; output uses the answer. The backend cannot reconstruct the actual provider prompt.
- `TokenUsage` carries engine-reported or estimated counts. `estimated` is true when either count was estimated. Cached answers add zero new tokens.
- `UsageService` records successful search and synthesis requests. It stores no question or document text. Accounting failures are logged without failing a completed query.
- `UsageController` exposes `GET /api/usage`: users see personal totals; admins see workspace totals. The verified JWT determines scope.

Counts survive application restarts. Totals start when V11 is applied and exclude earlier activity, failed/unreported provider calls, ingestion, and embedding. Search-only requests count toward queries but add no synthesis tokens. Extractive fallback without engine usage adds no observed tokens. Totals are observations and estimates, not a provider billing ledger. Cost conversion and ingestion accounting are outside this implementation.
