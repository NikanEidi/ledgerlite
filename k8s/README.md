# Kubernetes deployment

LedgerLite and Postgres on a local `kind` cluster: a ConfigMap for non-sensitive configuration, a Secret for credentials, resource requests/limits, and readiness/liveness probes against `/actuator/health`.

Kubernetes fundamentals practiced separately: [github.com/NikanEidi/k8s-practice](https://github.com/NikanEidi/k8s-practice).

## Files

| File | Purpose |
|---|---|
| `postgres.yaml` | Postgres Deployment, Service, and PersistentVolumeClaim |
| `postgres-secret.example.yaml` | Shape of the Postgres password Secret |
| `app-config.yaml` | Non-sensitive config: Postgres host and port |
| `app-secret.example.yaml` | Shape of the app's Secret: `DB_PASSWORD`, `JWT_SECRET_KEY` |
| `app.yaml` | LedgerLite Deployment (2 replicas, probes, resource limits) and Service |

`postgres-secret.yaml` and `app-secret.yaml` hold real values and are gitignored. The `.example.yaml` files document their shape.

## Setup

```bash
cd k8s
cp postgres-secret.example.yaml postgres-secret.yaml
cp app-secret.example.yaml app-secret.yaml
```

Fill in real values in both. `DB_PASSWORD` must match in both files. `JWT_SECRET_KEY` needs at least 32 bytes — generate one with `openssl rand -base64 32`.

## Build and load the image

```bash
docker build -t ledgerlite:local .
kind load docker-image ledgerlite:local
```

## Deploy

```bash
kubectl apply -f k8s/postgres-secret.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/app-config.yaml
kubectl apply -f k8s/app-secret.yaml
kubectl apply -f k8s/app.yaml
```

## Verify

```bash
kubectl get pods -l app=postgres
kubectl get pods -l app=ledgerlite
kubectl port-forward service/ledgerlite-service 8080:8080
```

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`. Flyway runs migrations automatically on startup.

## Clean up

```bash
kubectl delete -f k8s/app.yaml -f k8s/app-secret.yaml -f k8s/app-config.yaml -f k8s/postgres.yaml -f k8s/postgres-secret.yaml
```
