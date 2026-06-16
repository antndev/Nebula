# Nebula — planning

Rough direction. Sections marked **decided** are settled; **open** ones are still idk-yet.

## entrypoint & routing
- Port `25565` = the entrypoint, one per node. Used ONLY for the very first join — never
  for server→server transfers.
- Services are defined in config: docker image, scaling, max players, joining behavior,
  `persistent` flag.
- Routing = a default service + a hostname→service map, e.g.
  `bedwars.example.com → bedwars-lobby`, `default → lobby`. *(open: config format / YAML)*

## ports
- `25565` reserved for the entrypoint.
- Service instances get host ports from the fixed node range `32800-33799` — the hard limit
  (1000/node). On top of that a configurable per-node cap (`3..1000`, default 50).
- Inside the container every service listens on `25565` (own network namespace, no clash).

## live channel (node ↔ servers) — decided
Transport = **one WebSocket per server**, dialed OUT to its *local* daemon, kept open, both
sides push instantly (that's the "live" part — no polling). Client = JDK `java.net.http`,
daemon = Ktor, shared `nebula-protocol` sealed classes, no codegen. Redis later sits *behind*
the daemon, it does not replace the socket.

**Reliability:** within a live connection TCP gives ordered, lossless delivery — no message is
silently dropped or reordered. A break is always noticed: a clean close (FIN/RST), or a
WS-level **ping/pong heartbeat** for silent death (crash / cable / NAT timeout) → close →
reconnect. On reconnect the `Status` snapshot resyncs, so deltas missed during the gap never
cause drift. The heartbeat is **transport-level** (Ping/Pong frames), NOT an app message —
keep it (tuned, not aggressive) for dead-connection detection.

Two message sets, named by **direction** — no abstract "Command"/"ServiceMessage" categories.

### server → daemon  (`ServerToDaemon`)
- `Status(servicePort, players)` — full snapshot, on connect + reconnect (NOT a heartbeat; live changes go via the deltas)
- `Join(player)` — delta
- `Leave(uuid)` — delta
- *(later)* `TransferRequest(uuid, targetService)` — server-initiated transfer (NPC click)

### daemon → server  (`DaemonToServer`)
- `Kick(uuid, reason?)`
- `Transfer(uuid, host, port, ticket)`
- `Message(uuid, text)`  *(+ optional `Broadcast(text)`)*
- *(later)* permission / rank updates

### derived, NOT on the wire
- instance *lifecycle* status (starting/running) → from the connection + the `Status` message
- capacity / full → daemon computes it from presence + config
- journey / audit trail → daemon-side (it mints every hop); redis later
- secret → env `NEBULA_SECRET`

### principles
- two sealed roots, named by direction (`ServerToDaemon` / `DaemonToServer`) — the grouping IS the direction
- snapshot + delta, never poll
- derive lifecycle status, don't transmit it
- stable `@SerialName` discriminators (wire survives class renames)
- ignore unknown → an old server safely skips a new message
- auth off-wire (local HMAC) → the channel stays fire-and-forget

## transfers + tokens — decided
Every transfer carries a token; the only tokenless door is the first entrypoint join. SMP
access control is the server's native `whitelist.json`.

Token = HMAC-signed, **stateless**, validated **locally** on the target (no daemon roundtrip):
```
claims = uuid
       · target  : serviceId  (+ instanceId only for keyed/persistent services)
       · source  : serviceId  (informational — UX "welcome back", audit)
       · exp      (TTL ~30s, + optional iat)
sig    = HMAC_SHA256(NEBULA_SECRET, claims)
```

Flow:
1. daemon decides routing → mints the ticket → `Transfer(uuid, host, port, ticket)` to the SOURCE server
2. source: `storeCookie("nebula:ticket", ticket)` → fires the MC transfer packet to `(host, port)`
3. client connects to the target instance
4. target in `AsyncPlayerConfigurationEvent`: read cookie → check `sig` · `uuid == player` · `target == self` · `exp` → ok join, else disconnect
5. entrypoint skips the check (front door)

`source` is informational only — validation never depends on it. Routing policy ("may lobby
send to bedwars?") is enforced by the daemon at mint time, not by the target.

## services (generic) — decided
No big `kind` enum, no class per gamemode. One generic `Service`; specifics come from config
flags the daemon interprets. A new gamemode = a new config entry, not new code.
Main flag: `persistent` (bool) = whether the world must be saved. SMPs are persistent, don't
scale, and never auto-delete (`scaleDownEmptyAfterSeconds = null` = never).

## decentralized / multi-proxy — open
Every node runs its own daemon + entrypoint; you join and get redirected — maybe to another
node, maybe to the same node on a different port. State can't live in one node's memory →
shared store (Redis: instances, telemetry via TTL heartbeats, transfer tokens). Daemon =
node-agent (local docker) + one scheduler-leader (redis lock) deciding placement; per-node
command queue executes. *(open: details)*

## vanilla — open
Want to run 100% vanilla servers too (the Mojang jar as a "foreign" service): telemetry via
Server List Ping + RCON (no SDK), join via transfer + whitelist, leave via disconnect. An
in-game transfer-out proxy is NOT mini (needs protocol termination + encryption) — defer it.

## player data (groups, perms, prefixes, rank colors) — open
Decentralized + live updates. Storage tbd.

## first step (phase 1)
Just the channel, nothing else: join → live presence (`Status` + `Join`/`Leave`) → the daemon
pushes `Transfer` down → the server fires the MC transfer packet → the target validates the
HMAC ticket. State stays in-memory behind a small interface, so switching to Redis later is an
impl swap, not a rewrite. Player data comes later.

Protocol delta for phase 1 = **two new `DaemonToServer` variants**: `Transfer`, `Message`.
