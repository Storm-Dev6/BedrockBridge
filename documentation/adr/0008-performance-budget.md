# ADR-0008: Mérhető performance budget

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

„Gyors” vagy „sok session” nem ellenőrizhető követelmény, a bridge és upstream költsége könnyen összekeverhető.

## Döntés

Rögzített 4 core/4 GiB referenciakörnyezeten memória-, CPU-, latency-, TPS-hatás- és sessionkapacitás budgetet alkalmazunk. Micro, macro, impairment és soak benchmark különül el; p95/p99 és kontroll baseline kötelező. A Phase 0.5 specifikáció táblázata release gate.

## Következmények

Regresszió objektíven blokkolható, de stabil runner és statisztikai zajkezelés kell. Cél módosítása mérési bizonyítékkal és ezen ADR-t felülíró döntéssel történhet.

## Elvetett alternatívák

- csak TPS mérés: nem izolálja a bridget;
- fejlesztői laptop ad hoc mérés: nem összehasonlítható;
- átlagérték: elrejti a tail latencyt.
