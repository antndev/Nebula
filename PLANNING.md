# Nebula — planning

Rough direction. Sections marked **decided** are settled; **open** ones are still idk-yet.

## network shape — decided
**No proxy.** The entrypoint only takes the very first join, then the vanilla transfer packet sends
the client straight to a backend. Every later hop is backend → backend, again via transfer.

Consequences (accepted on purpose):
- the whole network speaks exactly one Minecraft version (see *versions*)
- every backend port must be reachable from the internet — no central DDoS filter in front
- cross-server features (party, /msg, global chat, tab list, friends) don't come for free from a
  proxy — they go through the daemon over the live channel
- every server switch is a real reconnect incl. the Mojang login of the target

## versions — decided
- One Minecraft version per network = the version of the Minestom build in use. Today: **26.3**
  (protocol 777, `26_3-SNAPSHOT`). Upgrading = bump Minestom everywhere at once.
- The entrypoint shows that version in the server list and rejects any other client version with a
  clear message ("Please join with Minecraft 26.3.").
- The live channel `Hello` carries `nebulaProtocol` (our wire version, `NebulaProtocol.VERSION`) +
  `minecraftProtocol`; the daemon closes the socket (`1008`) with a clear reason on mismatch → an
  old lobby image can never silently join a newer network. Bump `NebulaProtocol.VERSION` on every
  wire change that old servers can't handle.

## entrypoint & routing
- **decided:** port `25565`, one per node, ONLY for the first join — never for server→server hops.
- **decided:** the entrypoint is a tiny hand-rolled server that only uses Minestom's packet classes
  (no `MinecraftServer`, no world, no instance): handshake → status/ping, or login start →
  `ExpectPlayer` to the target → login success → `Transfer` in the configuration phase.
- **decided:** the entrypoint runs offline (no Mojang call, no encryption). Identity is proven by
  the target backend, which is online-mode (see *auth*). Saves one Mojang join per network entry.
- **open:** routing = a default service + a hostname→service map, e.g.
  `bedwars.example.com → bedwars-lobby`, `default → lobby`. Config format / YAML still open.

## ports
- `25565` reserved for the entrypoint.
- Service instances get host ports from the fixed node range `32800-33799` — the hard limit
  (1000/node). On top of that a configurable per-node cap (`3..1000`, default 50).
- Inside the container every service listens on `25565` (own network namespace, no clash).

## auth & transfers — decided (option A, replaces the old HMAC ticket plan)
Every backend runs **online-mode** → real uuid, signed skin/cape and encryption for free. Access
control is a uuid allowlist pushed by the daemon:

1. daemon picks the target → `ExpectPlayer(uuid, expiresAt)` (TTL 10s) to the target server
2. only if that was delivered: the client gets `Transfer(host, port)`
3. target in `AsyncPlayerConfigurationEvent`: uuid expected and not expired → join, else kick

Notes:
- Mojang `hasJoined` (server side) isn't rate limited. The real cap is the client-side join limit
  (~6 per 30s per account) → *(todo)* per-uuid hop cooldown (~5s) + loop guard in the daemon.
- Switch to offline backends + daemon-cached profile + token ("option D") only on a concrete need:
  moving one player faster than ~6 hops/30s, surviving a Mojang outage, or bot/load tests.

## live channel (node ↔ servers) — decided
Transport = **one WebSocket per server**, dialed OUT to its *local* daemon, kept open, both sides
push instantly. Client = JDK `java.net.http`, daemon = Ktor, shared sealed classes in
`nebula-sdk` (package `nebula.protocol`), no codegen. Redis later sits *behind* the daemon, it does
not replace the socket.

**Reliability:** TCP gives ordered, lossless delivery within a connection. A break is always
noticed: a clean close, or a WS-level ping/pong heartbeat for silent death → close → reconnect. On
reconnect the `Hello` snapshot resyncs, so deltas missed during the gap never cause drift.

Messages today:

| server → daemon (`ServiceMessage`) | daemon → server (`Command`) |
|---|---|
| `Hello(servicePort, players, nebulaProtocol, minecraftProtocol, token)` — snapshot on (re)connect | `ExpectPlayer(uuid, expiresAt)` — allowlist |
| `PlayerJoined(player)` — delta | `Transfer(uuid, host, port)` — send player elsewhere |
| `PlayerLeft(uuid)` — delta | `Kick(uuid, reason?)` |
| `TransferRequest(uuid, targetService)` — *(only logged so far)* | *(later)* `Message(uuid, text)`, rank updates |

Principles:
- snapshot + delta, never poll
- derive lifecycle status, don't transmit it
- stable `@SerialName` discriminators (wire survives class renames)
- ignore unknown → an old server safely skips a new message
- *(planned rename)* roots named by direction: `ServerToDaemon` / `DaemonToServer`

**Channel auth — decided:** the daemon gives each container a random token (env `NEBULA_TOKEN`,
kept as container label `nebula.token` so it survives a daemon restart). `Hello` must carry it,
otherwise the socket is closed (`1008`) and a running session is never replaced. Containers from
an older daemon without a token are removed on reattach and recreated.

## instance lifecycle — open
Today an instance is only "alive" while its socket is connected; a crashed container is never
noticed and never replaced. Proposal: Docker (inspect/events) is the source of truth for "the
container lives", the socket for "ready + who is on it".

## services (generic) — decided
No big `kind` enum, no class per gamemode. One generic `Service`; specifics come from config flags
the daemon interprets. A new gamemode = a new config entry, not new code.
Main flag: `persistent` (bool) = whether the world must be saved. SMPs are persistent, don't scale,
and never auto-delete (`scaleDownEmptyAfterSeconds = null` = never). *(open: what the "key" of a
keyed/persistent instance is — per player, per world?)*

## decentralized / multi-node — open
Every node runs its own daemon + entrypoint; you join and get transferred — maybe to another node,
maybe to the same node on a different port. State can't live in one node's memory → shared store
(Redis: instances, presence, locks). Daemon = node-agent (local docker) + one scheduler deciding
placement. Simpler alternative to weigh: one fixed controller daemon + agent daemons. Not before a
single node is feature complete.

## vanilla — open
Run 100% vanilla servers too (the Mojang jar as a "foreign" service, same version as the network):
telemetry via Server List Ping + RCON (no SDK), access via `whitelist.json`, leave via disconnect.

## player data (groups, perms, prefixes, rank colors) — open
The daemon owns it and pushes changes over the live channel. Storage behind an interface (file /
SQLite first, Postgres later). Decide before parties/groups — it shapes the protocol.

## not now
- admin dashboard — removed, not needed for now
- tests — later (registry + scaling logic are pure and easy to test once we start)

## status
**Phase 1 (the channel) — done:** entrypoint → `ExpectPlayer` → transfer → allowlist check, live
presence (`Hello` / `PlayerJoined` / `PlayerLeft`), reattach running containers, minimum instances,
version handshake + per-container token on the live channel.

**Next, in order:**
1. detect dead containers + real scaling (scale up at `playersToScaleUp`, warm instances, cooldown,
   scale down empty)
2. config file instead of `Main.kt`
3. second service + backend → backend transfer (`TransferRequest` → daemon → `Transfer`)
4. player data → groups / parties
5. multi-node
