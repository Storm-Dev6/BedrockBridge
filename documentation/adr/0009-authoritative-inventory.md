# ADR-0009: Authoritative inventory reconciliation

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

A Bedrock tranzakciós és Java window/click modell eltér; optimista kliensstate duplikációt vagy deszinkront okozhat.

## Döntés

A Java upstream inventory az egyetlen authoritative állapot. Bedrock művelet canonical `InventoryIntent`, revision/precondition ellenőrzéssel és idempotency request cache-sel. Csak szerver update/ack commitol; reject/timeout mismatch resyncet vált ki.

## Következmények

Megakadályozza a kliens által diktált stackeket és duplikációt, de extra latency és összetett container adapterek keletkeznek. Nem támogatott művelet biztonságosan elutasított.

## Elvetett alternatívák

- kliensoptimista commit: exploit/desync;
- packetek vak átnevezése: eltérő szemantika;
- globális inventory lock: sessionök felesleges összekapcsolása.
