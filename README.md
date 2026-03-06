Nebula v2 is being rewritten.

The previous stable version is available on:
- Branch: `legacy`
- Tag: `v1.0.0`

## Local 4-node Docker Mesh

From the repository root:

```powershell
docker compose up --build -d
docker compose ps
```

Follow logs:

```powershell
docker compose logs -f node1 node2 node3 node4
```

Failure tests:

```powershell
# Simulate one node going offline
docker compose stop node3

# Bring it back
docker compose start node3

# Restart a different node and validate saved-peer rejoin
docker compose restart node2
```

Shutdown and cleanup:

```powershell
docker compose down
```
