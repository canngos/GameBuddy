# Deploying GameBuddy

Five services behind one ingress. Nothing is reachable from outside the cluster except
through `k8s/ingress.yaml`.

## Layout

| Service | Port | Public paths |
|---|---|---|
| auth-service | 4567 | `/auth/**` — plus `/admin/**`, source-restricted |
| application-service | 4568 | `/application/**` |
| notif-service | 4569 | `/notif/showall` only |
| community-service | 4570 | `/community/**` |
| match-service | 4571 | `/match/**`, `/messages/**`, `/ws/**` |

Three Ingress objects, because they need different rules:

- **`gamebuddy`** — the public API. Rate-limited, 1 MB body cap, 30s timeouts.
- **`gamebuddy-admin`** — `/admin/**`, restricted by `whitelist-source-range`. **Set this
  to your own network before applying.** The default is the RFC1918 private ranges, so
  out of the box `/admin` is reachable from a VPN or office network and not from the
  internet. Being locked out after applying it unchanged is the annotation working.
- **`gamebuddy-websocket`** — `/ws`, with a 1-hour read timeout and cookie affinity that
  the rest of the API must not inherit.

`/notif/token` and `/notif/topic` are **deliberately not routed**. They push to a device
and broadcast to every user, and they are called by application-service and match-service
in-cluster. Three things stand between them and the internet: they are absent from the
ingress, `network-policies.yaml` restricts them to their two callers, and
`InternalApiKeyFilter` requires a shared key.

## Secrets

Two values must be **identical across services**, because they are shared contracts:

- `JWT_SECRET` — auth-service mints the tokens the other four verify.
- `INTERNAL_API_KEY` — application-service and match-service present it, notif-service
  checks it.

```bash
JWT_SECRET=$(openssl rand -base64 48)
INTERNAL_API_KEY=$(openssl rand -base64 32)

for svc in auth application community match notif; do
  kubectl create secret generic ${svc}-service-secrets \
    --from-literal=SPRING_DATASOURCE_USERNAME=... \
    --from-literal=SPRING_DATASOURCE_PASSWORD=... \
    --from-literal=JWT_SECRET="$JWT_SECRET" \
    --from-literal=INTERNAL_API_KEY="$INTERNAL_API_KEY"
done
```

auth-service additionally needs `EMAIL` and `EMAIL_PWD` for the verification mail.

The Firebase service-account key is a separate secret, mounted as a file rather than
baked into the image:

```bash
kubectl create secret generic notif-service-firebase \
  --from-file=service-account.json=./firebase-service-account.json
```

## Order

Schema migrations first — the `k8s` profile sets `ddl-auto: validate`, so a pod whose
schema does not match will fail to start rather than quietly alter your tables.

```bash
# 1. Migrations, once per service, against the same database
psql "$DATABASE_URL" -f GameBuddy-auth-service/src/main/resources/db/upgrade-2026.sql
psql "$DATABASE_URL" -f GameBuddy-application-service/src/main/resources/db/upgrade-2026.sql
psql "$DATABASE_URL" -f GameBuddy-community-service/src/main/resources/db/upgrade-2026.sql
psql "$DATABASE_URL" -f GameBuddy-match-service/src/main/resources/db/upgrade-2026.sql
psql "$DATABASE_URL" -f GameBuddy-notif-service/src/main/resources/db/upgrade-2026.sql
# match-service also has MongoDB steps, in comments at the bottom of its file — run
# those with mongosh.

# 2. Ingress controller (minikube)
minikube addons enable ingress

# 3. Network policies need a CNI that enforces them
#    minikube start --cni=calico

# 4. Services
kubectl apply -f GameBuddy-auth-service/deploy-to-minikube.yml
kubectl apply -f GameBuddy-application-service/deploy-to-minikube.yml
kubectl apply -f GameBuddy-community-service/deploy-to-minikube.yml
kubectl apply -f GameBuddy-match-service/deploy-to-minikube.yml
kubectl apply -f GameBuddy-notif-service/deploy-to-minikube.yml

# 5. Edge
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/network-policies.yaml
```

## Verifying the boundary

The point of this layout is what is *not* reachable. Check it:

```bash
HOST=api.gamebuddy.example

# Public routes answer.
curl -sk "https://$HOST/application/get/games" -o /dev/null -w "games: %{http_code}\n"

# The broadcast endpoint is not routed at all — expect 404 from the ingress, never 200.
curl -skX POST "https://$HOST/notif/topic" \
  -H 'Content-Type: application/json' \
  -d '{"topic":"all","title":"x","body":"x"}' \
  -o /dev/null -w "broadcast from outside: %{http_code}\n"

# No service answers on its own address any more.
kubectl get svc -o custom-columns=NAME:.metadata.name,TYPE:.spec.type
# every row should read ClusterIP

# In-cluster, the send endpoint still requires the key.
kubectl run probe --rm -it --image=curlimages/curl --restart=Never -- \
  curl -s -o /dev/null -w "%{http_code}\n" -XPOST http://notif-service:4569/notif/token \
  -H 'Content-Type: application/json' -d '{"token":"t","title":"x","body":"x"}'
# expect 401 without X-Internal-Api-Key
```

## Scaling note

The STOMP broker is `enableSimpleBroker`, which keeps subscriptions in the pod that owns
the socket. The websocket ingress therefore pins a client to one replica with a session
cookie. That works, but it means chat does not fan out across replicas: a message
published on pod A is not delivered to a subscriber connected to pod B. Two pods each
holding their own half of the conversations is fine while both sides of a chat land on
the same pod, and stops being fine as soon as they do not. Moving to an external relay
(Redis or RabbitMQ, via `enableStompBrokerRelay`) is the fix when chat volume justifies
it; until then, keep `match-service` at one replica or accept the affinity.
