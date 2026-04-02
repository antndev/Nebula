You define services in a config (or maybe a cleaner way, idk yet, just some
way to configure stuff).

By default, there is an entrypoint server on port `25565` to transfer players
to a service when they join. This will not be used for transfers from a
server, only as an entrypoint.

Let's say, for example, you want a main lobby service. You would define it as
`lobby`, define a Docker image, secrets, scaling behavior, when instances
should be created, max players, etc., and the ports. This also defines the
max number of instances you can have of that service, for example
`25566-25576` (`25565` is used by the entrypoint).

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