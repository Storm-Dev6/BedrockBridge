# Live Bedrock 1.21.40 observer

The `bedrock-registry-generator` module now contains a bounded RakNet relay CLI:

```powershell
$env:JAVA_HOME='C:\Users\Gamestormzone\.jdks\ms-21.0.9'
.\gradlew.bat --no-daemon :bedrock-registry-generator:runLiveObserver `
  --args="--listen 0.0.0.0:19132 --bds 127.0.0.1:19142 --artifact C:/Users/Gamestormzone/Documents/BDS/real-observation/item-registry-748.ndjson --trusted-root C:/Users/Gamestormzone/Documents/BDS/real-observation/official-root.der --timeout-seconds 300"
```

The listener binds `0.0.0.0:19132`; the unchanged BDS target is
`127.0.0.1:19142`. A phone on the same private network must connect to the
host's private IPv4 address on UDP `19132`, not to `0.0.0.0`. Find the address
with:

```powershell
Get-NetIPAddress -AddressFamily IPv4 |
  Where-Object {$_.InterfaceAlias -notmatch 'Loopback' -and $_.IPAddress -notlike '169.254.*'} |
  Select-Object InterfaceAlias,IPAddress
```

The `--trusted-root` file is an externally supplied DER `SubjectPublicKeyInfo`
or PEM `PUBLIC KEY` containing only the official P-384 public trust root. The
observer never accepts a private key and never writes the login chain, client
data JWT, identity claims, or complete packet payloads. The key file and output
artifact must both remain outside the Git worktree.

The observer validates the real client's protocol-748 Login chain before
forwarding it. It records only bounded packet IDs and state transitions. The
raw relay cannot decrypt the BDS-side StartGame after the server-to-client
encryption handshake: the BDS private key and the Bedrock client's private
identity key are not available to the relay. Therefore the CLI fails closed at
`ENCRYPTED_GAME_PAYLOAD_UNAVAILABLE_NO_ENDPOINT_PRIVATE_KEY` and does not write
an artifact in that case. This is intentional; a timeout or opaque ciphertext
is never treated as a registry observation. A trusted, terminating observer
with an independently established endpoint key is required before a real
StartGame artifact can be produced.

If a clear, authenticated StartGame frame is legitimately available, the CLI
writes only these fields as NDJSON: `itemName`, signed-short `itemId`, and
`componentBased`. It then prints `itemCount`, byte count, and SHA-256. Validate
the resulting external file with:

```powershell
$hash = (Get-FileHash `
  'C:\Users\Gamestormzone\Documents\BDS\real-observation\item-registry-748.ndjson' `
  -Algorithm SHA256).Hash.ToLowerInvariant()

.\gradlew.bat --no-daemon :bedrock-registry-generator:run `
  --args="--artifact C:/Users/Gamestormzone/Documents/BDS/real-observation/item-registry-748.ndjson --protocol 748 --sha256 $hash"
```

Only after that command succeeds may `bridge.properties` reference the file:

```properties
bridge.registry-path=C:/Users/Gamestormzone/Documents/BDS/real-observation/item-registry-748.ndjson
bridge.registry-protocol-version=748
bridge.registry-sha256=<the-lowercase-64-character-hash>
```

An empty file, a synthetic file, a guessed item list, or a missing trust root
is rejected. CI never downloads BDS or stores any proprietary artifact.
