Each region connects through a separate domain:

- `eu.example.com`
- `us.example.com`
- `...`

Additionally, `example.com` will automatically transfer you to the correct one.

You have at least 1 region, with each region having at least 1 node.

## TCP Proxy Option

You do not actually need all of those ports if you build a simple TCP proxy.

Problem: SPOF. However, that may be unrealistic, because a lightweight TCP proxy in Rust, for example, likely would not really break.

This would work as follows:

All "unknown" connections get proxied to a lobby server on the same node. If the player wants to play a gamemode, the lobby server sends a transfer packet and reconnects the client to another server, for example Bedwars.

But how will that work?

Maybe via many DNS entries, for example:

- `24n3.bedwars.gamemode.node1.eu.example.com`

The TCP proxy could then use that to determine the target.

Problem: very unpleasant and complicated for fast scaling, etc.

## Without a TCP Proxy

If we do not use a TCP proxy and instead use the 64,512 free ports, which should never run out, we can transfer the player cleanly to the target server by sending a transfer packet with the same IP but a different port.

Joining in this case would work by setting an auto-transfer server at port `25565`, which can act as a limbo server.

Idea: whenever we want to send the player somewhere, we first send them to that limbo server, and then the limbo server sends the final transfer packet.

Usage: when the Bedwars server has to be created, this will cover up the 5 seconds. Alternatively, only use it for first joining into the network, so only `join -> lobby`, not `lobby -> gamemode`, and just let the player wait in the lobby until a gamemode server is ready.

---

## Additional Thoughts on the TCP Proxy

The client has a 30-second timeout, I believe. Anyway, as long as we keep it under 30 seconds, we could hold the connection with a TCP proxy, so with the proxy there would be no need for a limbo server.

But with a proxy, as I said, it is still pretty bad because this is not really a layer-2 problem, but an application-layer routing problem. We would need to identify specific clients and route them back to the correct server. The only solution I currently see is, as mentioned before, the hostname/DNS approach, similar to a proxy reading the hostname and routing based on that.

So I will probably go without the TCP proxy idea.

## Capacity Estimate Per Node

Each node can have a different amount of Minecraft servers, especially if the lobby and gamemodes are using optimized servers like Minestom instead of Spigot, for example.

So let's say a normal node has only 32 GB. If we reserve 1 GB for the system, 1 GB for the daemon Docker container, and 2 GB as a buffer, then we have about 25 servers if each one uses 1 GB.

Each server would handle about 12 players, which seems pretty similar for lobby and Bedwars. A duels server would probably have fewer players, maybe `1v1`, so only 2.

## Docker Port Allocation Question

I will have the Nebula daemon running in Docker. It has to somehow check that a port is free. We cannot just assume the ports from `1024` to `65535` are free.

We need a way to check them, or just let the Docker engine choose one, which is probably the easiest and best way.

Testing showed that Docker does return an error when a port cannot be allocated. Example error:

`Bind for 0.0.0.0:40000 failed: port is already allocated`

So port-allocation failure handling has to be implemented in Nebula.

Important distinction:

1. One specific port is already in use, but other ports are still free.
In that case, Nebula should simply try another port on the same node.

2. No usable ports are left on that node.
In that case, Nebula should treat the node as out of port capacity and place the server on another node, queue the startup, or mark the node as temporarily full.

This specific test only proved case 1: port collision. Case 2 is different and should be tracked separately in the daemon logic.
