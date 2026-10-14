# Staff HQ 5.2.0

- Reborn Journey is visible directly in the menu and dashboard, with a matching section on player profiles.
- Current JP, ranks, historical credit, permanent ownership, purchase eligibility, claim receipts and daily budgets are separate.
- Source JP and confirmed spending use saved receipts; existing ownership and old claims do not create new purchases or rewards.
- Related evidence is grouped by committed profile change. Expand it for payment phases, exact amounts, source points and reward commands.
- Priority heartbeat distinguishes server reports from delayed HQ event delivery. Stale capture timestamps cannot make a server look current.
- HTTP failures retain event queues. Flapping connections use two healthy minutes before recovery; repeated heartbeat/connector reminders are suppressed.
- Sentinel damage values no longer invalidate healthy statistics; valid rows from previously rejected numeric batches are recovered with stable replay IDs.
- Existing themes, document editing, private data, staff permissions and migration journals are retained.
