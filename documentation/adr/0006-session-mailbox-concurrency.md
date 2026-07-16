# ADR-0006: Session mailbox concurrency

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

Két aszinkron hálózati oldal ugyanazt az entity, inventory és state-machine állapotot módosítja.

## Döntés

Sessionönként soros, bounded mailbox az egyetlen state mutation út. Network event loop csak validál/dekódol és eseményt küld. Chunk CPU-munka bounded poolon fut, eredménye session generation/sequence tokennel tér vissza. Prioritás védi a keepalive/login/inventory control eseményeket.

## Következmények

Determinista state és kevesebb lock, viszont egy lassú handler head-of-line blockingot okozhat. Watchdog, handler budget, coalescing és worker offload szükséges.

## Elvetett alternatívák

- shared mutable state lockokkal: deadlock/race kockázat;
- thread/session: magas erőforrásigény és nincs természetes backpressure;
- korlátlan event queue: DoS/memórialeak.
