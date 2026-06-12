You define services in a config (or maybe a cleaner way, idk yet, just some
way to configure stuff).

By default, there is an entrypoint server on port `25565` to transfer players
to a service when they join. This will not be used for transfers from a
server, only as an entrypoint.

Let's say, for example, you want a main lobby service. You would define it as
`lobby`, define a Docker image, secrets, scaling behavior, when instances
should be created, max players, etc.

Ports: `25565` is always reserved for the entrypoint itself. All service
instances get their host ports from ONE fixed node range `32800-32899` —
that's the fundamental hardcoded limit (100 servers per node). On top of that
there's a lower configurable limit per node (3..100, default 50).

For the entrypoint to know where players should land, you define its behavior
like the following. The format, etc. may change; this only acts as a rough
idea.

```yaml
host:
  name: "bedwars.example.com"
  service: bedwars-lobby

host:
  name: "play.bedwars.example.com"
  service: bedwars-gamemode

default:
  service: lobby
```

When a player gets transferred from the lobby to Bedwars, for example because
they clicked on the Bedwars NPC, a packet is sent to the daemon to handle
where they should go. Or maybe, if a server needs to be created, shortly after that, a
packet gets sent to the lobby where the player is, and the lobby sends a
Minecraft transfer packet to them for that target server.

In addition, for security, a transfer object gets created in a DB, Redis, or
some communication way, idk yet, to confirm that the transfer is valid so the
target server can check if the player should be allowed to join. This also
gets created when the player joins from the entrypoint,
but obv. not when the player first joins the entrypoint.

## decentralized / multi-proxy

The whole thing should be decentralized as much as possible (multi-proxy). Every
node runs its own daemon with an entrypoint. You join, and you get redirected —
maybe to another node, or actually to the same node on a different port (so just a
different service/server). Because of that the state can't just live in one node's
memory anymore, it has to be shared somehow (DB / Redis / whatever, idk yet).

## transfers + access

There's a transfer token on *every* transfer, just for safety — even if it's only
to another lobby. The only one without a token is the very first join on the
entrypoint (like above).

For SMPs the access control is just the normal whitelist of the server software
itself.

## services (keep it generic)

No big "kind" enum and no class per gamemode. Just one general Service, and the
specifics come from flags that the daemon interprets — not from separate classes. A
new gamemode should just be a new config entry, not new code. It has to stay
generic.

The main flag is a bool `persistent` = whether the world has to be saved. SMPs are
persistent, don't scale, and never get auto-deleted.

## vanilla

Want to be able to run 100% vanilla servers too (maybe through a small custom proxy,
idk yet), because vanilla behaves differently from Paper in some edge cases.

## live comms (node <-> servers)

The telemetry / communication with the servers has to be LIVE, not polling every
10s. I want real live data: who's on right now, how many, etc. — instantly.

Over this channel we need:
- presence: who joined, who left, which players are on
- commands: queue join / queue leave, etc.
- transfers: the node tells the server "send player X to server Y", and the server
  sends a normal Minecraft transfer packet
- live updates: e.g. a player just got a new rank -> it updates live
- parties

The servers are probably all in the same docker network for this (that's how I'd do
it). Transport = **WebSocket**. Each server opens one websocket out to its *local* daemon
and keeps it open; both sides push messages whenever, instantly — that's the "live"
part. The server dials the daemon (not the other way around), so there's no inbound
port on the container and it fits "servers only talk to their local daemon". The
client is built into the JDK, the daemon side is Ktor — both Kotlin, sharing one
`nebula-protocol` module of sealed-class messages, no codegen. Same transport now and
long-term: Redis later is a separate layer *behind* the daemon, it does not replace
the websocket.

## first step (phase 1)

Just the channel, nothing else yet: a player joins -> live presence (join / leave /
count) -> the daemon can push a transfer command down -> the server fires the normal
Minecraft transfer packet. State stays in-memory for now, behind a small interface so
switching to Redis later is just an impl swap, not a rewrite. Storing ranks / player
data comes later, not now.

## player data (groups, perms, prefixes, rank colors)

Need to store player rights / groups / which prefixes, rank colors, etc. — and it
has to be decentralized and update live. How exactly we store this is still to
figure out.