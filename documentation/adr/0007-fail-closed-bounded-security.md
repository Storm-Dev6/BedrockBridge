# ADR-0007: Fail-closed és bounded security

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

Az internet felőli UDP parser, tömörítés, fragmentálás és tokenellenőrzés erőforrás-kimerítés és identitáshamisítás célpontja.

## Döntés

Minden inputréteg explicit byte/count/time/state limittel működik, olcsó ellenőrzés előzi meg a kriptográfiát/allokációt. Többszintű token bucket, replay guard, bounded queue/cache és fail-closed auth/crypto policy kötelező. Offline mód csak explicit izolált fejlesztői policy.

## Következmények

Biztonságos, kiszámítható túlterhelés, de legitim extrém forgalom limitbe ütközhet. A limitek metrikázottak és load teszttel kalibráltak, emelésük security review-köteles.

## Elvetett alternatívák

- korlátlan kompatibilitás: DoS kockázat;
- csak per-IP limit: NAT és elosztott támadás miatt elégtelen;
- auth hiba esetén offline fallback: identitásmegkerülés.
