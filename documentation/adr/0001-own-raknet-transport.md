# ADR-0001: Saját RakNet transport

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

A Bedrock kapcsolat UDP/RakNet reliability, ordering, fragmentálás és recovery szemantikát igényel. A projekt független implementációt és teljes erőforrás-kontrollt követel.

## Döntés

Saját, state-machine alapú RakNet réteget készítünk szabványos UDP I/O felett. A reliability engine saját ACK/NACK, wrap-aware sequence, bounded reassembly, MTU és RTT/RTO algoritmust valósít meg. Kész RakNet library legfeljebb feketedoboz-interoperabilitási teszt referenciája lehet, runtime függőség nem.

## Következmények

Nagyobb kezdeti költség és magas protokollkockázat, cserébe auditálható limitek, pontos metrikák és nincs harmadik fél implementációjához kötött viselkedés. Fuzz, impairment és hosszú soak release gate.

## Elvetett alternatívák

- kész RakNet stack: sértené a saját implementációs célt és csökkentené a belső kontrollt;
- megbízhatóság nélküli UDP: nem protokoll-kompatibilis;
- TCP tunnel: a Bedrock klienssel nem kompatibilis.
