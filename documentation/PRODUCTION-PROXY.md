# Production Bedrock-to-Java proxy vertical slice

> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

This branch wires the real Bedrock UDP/RakNet listener to a separate
`BedrockConnectedPlayAdapter` for every admitted endpoint. Each adapter owns one Bedrock
authentication/encryption session and one Java/Paper TCP upstream. The Java upstream is closed when
the Bedrock session times out, disconnects, or the listener shuts down.

The bridge is a server to Bedrock and a client to Java. The observer in
`documentation/LIVE-BDS-OBSERVER.md` is not part of this runtime path.

## Configuration

The minimum production properties are:

```properties
bridge.application-name=BedrockBridge
bridge.bind-address=0.0.0.0
bridge.bind-port=19132
bridge.upstream-address=127.0.0.1
bridge.upstream-port=25565
bridge.maximum-sessions=100
bridge.scheduler-threads=2
bridge.development-mode=false
bridge.registry-path=C:/Users/<user>/Documents/BDS/real-observation/item-registry-748.ndjson
bridge.registry-protocol-version=748
bridge.registry-sha256=<lowercase SHA-256 of the external artifact>
bridge.auth-trusted-root=C:/Users/<user>/Documents/BDS/real-observation/official-root.der
bridge.offline-auth-mode=deny
```

`deny` means official online Bedrock login-chain verification is required. The root file is an
external DER/PEM P-384 public key and never a private key. For a documented local-only test, use
`bridge.offline-auth-mode=allow-self-signed`; this does not accept offline clients in online mode.

Static listener mapping and named upstreams are supported without a hardcoded server count:

```properties
bridge.upstream.lobby.address=127.0.0.1
bridge.upstream.lobby.port=25565
bridge.upstream.survival.address=127.0.0.1
bridge.upstream.survival.port=25566
bridge.listener.19132.upstream=lobby
bridge.listener.19133.upstream=survival
```

The registry preflight runs before the UDP socket is opened. An absent, synthetic, malformed,
protocol-mismatched, or hash-mismatched artifact produces
`BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT` and no listener is started.

## Build and run

```powershell
$env:JAVA_HOME='C:\Users\Gamestormzone\.jdks\ms-21.0.9'
.\gradlew.bat --no-daemon clean check assemble
java -jar application\build\libs\BedrockBridge-0.1.0-SNAPSHOT.jar C:\path\to\bridge.properties
```

Expected successful startup log:

```text
INFO  ... BedrockBridge infrastructure started
```

The listener is `0.0.0.0:19132` (UDP); a phone on the same private network connects to the host's
private IPv4 address, not to `0.0.0.0`. The Java/Paper upstream is the endpoint selected by the
listener mapping, for example `127.0.0.1:25565`.

## Current boundary

The production composition now performs Bedrock login-chain validation, generates the
server-side encryption challenge, activates the connected cipher after the client handshake,
connects a distinct Java upstream per session, sends Java-derived PlayStatus/resource-pack flow,
and builds a protocol-748 StartGame frame from the validated three-field external item registry.

The remaining external manual gate is a real Bedrock client with a valid external registry artifact
and, in online mode, the user-supplied official trust-root public key. CI uses only synthetic
registry fixtures and never downloads BDS or stores proprietary data.
