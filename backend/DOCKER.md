# Backend Docker — dépannage Windows

## Ordre de démarrage complet (obligatoire)

FinPulse a **4 services** à lancer dans cet ordre :

| # | Service | Port | Commande |
|---|---------|------|----------|
| 1 | **Keycloak** | 9090 | (votre install Keycloak locale) |
| 2 | **P1 — Postgres/Redis** | 5433, 6380 | `cd Data-Ingestion-Pipeline` → `docker compose up -d` |
| 3 | **P1 — API FastAPI** | 8000 | `py -m uvicorn main:app --port 8000` |
| 4 | **Backend** | 8081 | `cd backend` → `docker compose up -d --build` |
| 5 | **Frontend** | 4200 | `cd frontend` → `ng serve` |

### Données Discover (P1 backfill)

**Discover est vide** tant que P1 n'a pas de sociétés. Après l'étape 3 :

```powershell
cd backend\scripts
.\seed-p1-demo.ps1
```

Ou manuellement :

```powershell
Invoke-RestMethod -Uri "http://localhost:8000/api/v1/pipelines/backfill/company" `
  -Method Post -ContentType "application/json" `
  -Body '{"identifier":"AAPL","run_signals":true}'
```

Vérifications :

```powershell
curl http://localhost:8000/api/v1/companies?limit=5
curl http://localhost:8000/api/v1/score/AAPL
```

### Watchlist & AI Agent

- **Watchlist** : vide tant que vous n'avez pas épinglé une société depuis **Discover** (bouton pin).
- **AI Agent** : tapez une question (ex. « Analyse AAPL »). Le backend appelle P1 + Mistral.

## Cause fréquente : chemin du projet

Docker Desktop **ne monte pas correctement** les fichiers si le chemin contient :

- des **espaces** (`GLSID S4`)
- une **apostrophe** (`projet d'innovation`)

Erreur typique :

```text
error mounting ".../projet d'innovation/.../schema.sql"
failed to create shim task
```

**Recommandation** : garder le projet sous un chemin simple, par exemple :

```text
C:\Users\4B\Downloads\GLSID S4\projet_innovation\FinPulse\FinPulse
```

(renommage `projet d'innovation` → `projet_innovation` : bonne idée)

### Message Discover « backend / P1 »

1. **Backend** : http://localhost:8081/health → doit afficher `{"status":"UP"}`
2. **Diagnostic** : http://localhost:8081/api/integration/status → indique si P1 est joignable
3. **P1** : http://localhost:8000/health → doit répondre
4. Si backend en Docker et P1 sur la machine : `P1_API_URL=http://host.docker.internal:8000` (déjà dans docker-compose)

Après modification du code backend :

```powershell
docker compose up -d --build
```

## Si GET /api/companies renvoie 500

Cause corrigée : un `ObjectMapper` sans module Java Time cassait la sérialisation des dates (`lastUpdate`).

```powershell
docker compose up -d --build
```

Puis retester : http://localhost:8081/api/companies (avec token Bearer si besoin).

## Redémarrage propre

Après une modification du code backend (ex. sécurité JWT), reconstruisez l'image :

Depuis le dossier `backend` :

```powershell
cd "C:\Users\4B\Downloads\GLSID S4\projet_innovation\FinPulse\FinPulse\backend"

docker compose down -v
docker rm -f finpulse_postgres finpulse_redis finpulse_backend 2>$null

docker compose up -d --build
docker compose ps
docker compose logs postgres --tail 30
```

`postgres` doit rester **Up (healthy)**.

## Ports

| Service  | Port hôte |
|----------|-----------|
| Postgres backend | 5432 |
| Redis backend    | 6379 |
| Backend API      | 8081 |

Si **5432** est déjà pris (autre PostgreSQL local), arrêtez l’autre service ou changez le mapping dans `docker-compose.yml` (`"5434:5432"`).

## P1 (pipeline) en parallèle

Le pipeline utilise **5433** — pas de conflit avec le backend **5432**.

P1 doit tourner séparément (`Data-Ingestion-Pipeline/docker-compose.yml` + `uvicorn` sur 8000).
