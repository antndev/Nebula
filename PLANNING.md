# Nebula Daemon (NebulaD) — Core Planning (temporary, NOT FINAL)

---

## What is NebulaD

A Kotlin application running directly on each machine (or in Docker, TBD).
It is the only thing that needs to be installed on a node. Everything else — lobbies, game servers — are Docker containers that NebulaD manages later.

All nodes are equal. No master. No special roles.

---

## Startup Sequence

1. Load or generate node ID from disk
2. Load local config (region, join token) from config file or env vars
3. Start gRPC server on cluster port and admin port
4. Load saved peer list from local disk
    - Peers known → attempt connection to all saved peers, receive updated cluster state from the first that responds
    - No peers known → bootstrap as first node, sit idle and wait for operator to send a `SeedFrom` command or for other nodes to join
5. Start heartbeat loop
6. Start watching cluster state for changes

---

## Peer Persistence

After every successful state sync, the node writes the current known peer list to local disk. On every restart it reconnects to those saved peers directly.

When a new peer is discovered via a state update it is added to the local peer list on disk immediately.

---

## Joining the Network

A seed address is never stored in config. Joining always happens via an explicit operator admin command.

**First node** — starts up, no peers, idles as a single-node cluster.

**Every subsequent node** — starts up, no peers, idles. Operator sends `SeedFrom(address)` to it via admin command. The node connects to that address, sends a join request, receives full cluster state, saves the peer list to disk. From that point on it reconnects via saved peers on every restart — no further admin intervention needed.

**Isolated node recovery** — same flow. Operator sends `SeedFrom(address)` to the isolated node pointing at any live node in the target network.

This is intentional — a node never silently joins any network. Every initial network connection requires explicit operator action.

---

## Node Identity

Every node has a stable UUID generated on first start and persisted to disk. This never changes — IP can change, the ID does not. The IP is stored separately and updated on every heartbeat.

If a joining node's UUID already exists in the cluster, the request is rejected and the joining node regenerates a new one and retries.

Node statuses: `PENDING` → `ACTIVE` → `DRAINING` → `DEAD`

---

## Cluster State

Every node holds a full copy of the cluster state in memory. There is no central store — the state IS the mesh. Includes: all known nodes with their metadata, distributed config, and any pending nodes awaiting approval.

State changes are streamed to all nodes in real time. Any node that misses an update while offline catches up via a full state sync on reconnect.

---

## gRPC Services

Two separate gRPC servers on two separate ports.

**Cluster port** — node to node communication. Handles join, heartbeat, and real-time state sync stream.

**Admin port** — operator to node communication. Handles cluster inspection, node approval/removal, config updates, and seed commands.

---

## Proto Definition (nebula.proto)

Cluster service methods:
- `Join` — new node requests to join, receives full state on acceptance
- `Heartbeat` — periodic ping carrying node resource metrics
- `SyncState` — bidirectional stream, nodes push state changes to each other in real time

Admin service methods:
- `GetClusterState` — returns full current state
- `ApproveNode` — moves a pending node to active
- `RemoveNode` — removes a node from the cluster
- `UpdateConfig` — updates a config key, propagated to all nodes
- `SeedFrom` — tells this node to perform a join from a given address

---

## Auth

**Node to node — mutual TLS**
The cluster bootstrapper generates a certificate authority. Every approved node receives a certificate signed by that CA. On every connection both sides present and verify each other's certificate. Unapproved nodes cannot connect. Pending nodes use a one-time join token until approved, then receive their signed certificate.

**Operator to admin port — API key over TLS**
API keys are stored hashed in cluster state. Every admin call must include the key as a request header. A gRPC interceptor validates it before any call reaches the handler. Keys have scopes: read-only or full admin.

---

## Node Join Modes

**Auto-accept mode**
Join request comes in → token validated → node immediately becomes active → receives full state and certificate.

**Manual approval mode**
Join request comes in → token validated → node sits in pending state → operator sends approve command → node becomes active → receives full state and certificate → all peers notified.

---

## Heartbeat and Dead Node Detection

Every node sends a heartbeat to all peers on a configurable interval (default 5s). Each node tracks the last seen timestamp for every peer independently.

If a peer has not been heard from beyond the dead threshold (default 15s), the node marks it as dead locally and broadcasts that status to all peers. No single node decides alone — all nodes observe independently. Majority agreement treats the node as dead.

---

## Config Hot Reload

Config lives inside the cluster state itself — no external store needed for the daemon layer. When an operator updates a config key via the admin service, the receiving node applies it immediately and broadcasts the change to all peers via the sync stream. All nodes apply the change in memory with no restart required.

---

## Config Categories (initial scope)

- **Network** — approval mode, heartbeat interval, dead threshold, join token
- **Regions** — list of enabled regions, default region

Scaling, matchmaking, and gamemode config are out of scope for this phase.

---

## Local Storage (per node, on disk)

- `node.id` — stable node UUID
- `peers.json` — saved peer list, updated after every state sync
- `config.yml` — local config (region, join token only — no seed address ever)
- `certs/` — node certificate and cluster CA cert (after approval)

---

## Module Structure (nebula-core)

- `identity` — node ID generation and persistence
- `config` — local config loading and change watching
- `cluster` — cluster state model, join handler, heartbeat manager, state sync, peer persistence
- `admin` — admin gRPC service implementation
- `auth` — mTLS certificate management, API key interceptor

---

## What NebulaD Does NOT Do Yet

The following are explicitly out of scope until the mesh layer is stable:

- Docker container management
- Queue processing and matchmaking
- Embedded broker / Redis replacement
- Map distribution
- Player session management
- Scaling logic

---

## First Coding Milestones

1. Node identity — load or generate UUID, persist to disk
2. Local config loading from file and env vars
3. gRPC server starts on both ports
4. Single node bootstraps with no peers, idles
5. Operator sends SeedFrom via admin → node joins, receives full state, saves peers to disk
6. Node restarts and reconnects via saved peers with no admin intervention
7. Heartbeat loop and dead node detection
8. Admin — get cluster state
9. Admin — approve and remove node
10. Config update propagates to all nodes in real time
11. mTLS enforced between all nodes