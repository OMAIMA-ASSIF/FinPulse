# FinPulse — Workflow complet et guide de test

Ce document décrit **le parcours bout-en-bout** du projet FinPulse : de l’ingestion des données publiques jusqu’à l’exposition des scores de risque et à l’explicabilité IA. Chaque étape inclut **ce qui se passe**, **où ça vit dans le code**, et **des commandes pour vérifier** que tout fonctionne.

---

## Table des matières

1. [Vue d’ensemble](#1-vue-densemble)
2. [Prérequis](#2-prérequis)
3. [Étape 0 — Installation de l’environnement](#étape-0--installation-de-lenvironnement)
4. [Étape 1 — Infrastructure (Docker)](#étape-1--infrastructure-docker)
5. [Étape 2 — Base de données](#étape-2--base-de-données)
6. [Étape 3 — Ingestion et traitement](#étape-3--ingestion-et-traitement)
7. [Étape 4 — Calcul des signaux et NCI](#étape-4--calcul-des-signaux-et-nci)
8. [Étape 5 — API de lecture](#étape-5--api-de-lecture)
9. [Étape 6 — Explicabilité (Spring AI)](#étape-6--explicabilité-spring-ai)
10. [Étape 7 — Tests automatisés](#étape-7--tests-automatisés)
11. [Workflow « démo rapide » (30 min)](#workflow-démo-rapide-30-min)
12. [Dépannage](#dépannage)
13. [Références](#références)

---

## 1. Vue d’ensemble

FinPulse répond à la question : **« Comment évoluent la narration publique, les finances, le comportement des initiés, le marché et la presse d’une société cotée ? »**

### Schéma du flux

```mermaid
flowchart LR
    subgraph sources [Sources externes]
        EDGAR[SEC EDGAR]
        FORM4[Form 4 XML]
        NEWS[Google News RSS]
        YF[Yahoo Finance]
        FRED[FRED macro]
        MISTRAL[Mistral API]
    end

    subgraph ingest [Ingestion]
        I1[edgar_client]
        I2[form4_client]
        I3[news_client]
        I4[market_client]
        I5[fred_client]
    end

    subgraph process [Traitement]
        P1[filing_splitter]
        P2[xbrl_parser]
        P3[embeddings]
        P4[form4_parser]
    end

    subgraph store [(PostgreSQL + pgvector)]
        DB[(companies, filings, embeddings, signal_scores, nci_scores...)]
    end

    subgraph signals [Moteur de signaux]
        S1[text / numeric / behavior / market / sentiment]
        S2[composite_engine → nci_global]
    end

    subgraph serve [Exposition]
        API[FastAPI :8000]
        SPRING[Spring AI :8081]
    end

    EDGAR --> I1 --> P1 --> P3 --> DB
    EDGAR --> I1 --> P2 --> DB
    FORM4 --> I2 --> P4 --> DB
    NEWS --> I3 --> DB
    YF --> I4 --> DB
    FRED --> I5 --> DB
    MISTRAL --> P3
    DB --> S1 --> S2 --> DB
    DB --> API
    DB --> SPRING
```

### Couches de signaux

| Couche | Module | Exemples de signaux |
|--------|--------|---------------------|
| Texte | `signals/text_signals.py` | `rlds`, `mda_drift`, `forward_pessimism` |
| Numérique | `signals/numeric_signals.py` | `fundamental_deterioration`, `numeric_anomaly` |
| Comportement | `signals/behavior_signals.py` | `ita`, `insider_signal` |
| Marché | `signals/market_signals.py` | `market_signal` |
| Sentiment | `signals/sentiment_signals.py` | `sentiment_signal` |
| Composite | `signals/composite_engine.py` | `nci_global` |

L’API est **en lecture seule** : elle ne recalcule pas les signaux à la volée ; elle lit ce que les pipelines ont déjà écrit.

---

## 2. Prérequis

| Outil | Usage |
|-------|--------|
| Python 3.11+ | Pipelines, API, tests |
| Docker Desktop | PostgreSQL, Redis, Adminer |
| JDK 17+ + Maven | Service Spring AI (explicabilité) |
| Git | Clone du dépôt |

**Clés API (pipelines « live » uniquement) :**

| Variable | Obligatoire pour | Où l’obtenir |
|----------|------------------|--------------|
| `MISTRAL_API_KEY` | Embeddings + explicabilité | [console.mistral.ai](https://console.mistral.ai/) |
| `FRED_API_KEY` | Pipeline macro | [fred.stlouisfed.org](https://fred.stlouisfed.org/docs/api/api_key.html) |
| `EDGAR_USER_AGENT` | Ingestion SEC (nom + email réels) | Convention SEC |

> Avec un **dump PostgreSQL** fourni (`finpulse_dump.dump`), vous pouvez tester l’API et l’explicabilité **sans** relancer l’ingestion SEC.

---

## Étape 0 — Installation de l’environnement

### Ce qui se passe

Création d’un environnement Python isolé et installation des dépendances du backend.

### Commandes

```powershell
cd C:\Users\essad\OneDrive\Bureau\PI3\FinPulse

py -m venv env
.\env\Scripts\Activate.ps1

pip install -r requirements.txt
pip install pytest httpx requests torch transformers
```

### Vérification

```powershell
py -c "import fastapi, sqlalchemy; print('OK')"
```

**Résultat attendu :** `OK` sans erreur d’import.

### Fichier `.env`

Créez `FinPulse/.env` à la racine (voir aussi `app/core/config.py`). Exemple minimal :

```env
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=finpulse
POSTGRES_USER=finpulse
POSTGRES_PASSWORD=finpulse_secret

EDGAR_USER_AGENT=FinPulse VotreNom votre.email@domaine.com
MISTRAL_API_KEY=votre_cle_mistral
FRED_API_KEY=votre_cle_fred

SERVER_PORT=8081
SPRING_AI_SERVICE_URL=http://localhost:8081
SPRING_AI_EXPLAIN_ENDPOINT=/api/v1/explain
```

---

## Étape 1 — Infrastructure (Docker)

### Ce qui se passe

Démarrage de trois conteneurs définis dans `docker-compose.yml` :

| Service | Port | Rôle |
|---------|------|------|
| `finpulse_db` | 5432 | PostgreSQL 16 + extension **pgvector** |
| `finpulse_redis` | 6379 | Cache / files (support futur) |
| `finpulse_adminer` | 8080 | Interface web SQL (serveur `db`) |

### Commandes

```powershell
docker compose up -d
docker compose ps
```

### Vérification

```powershell
docker exec finpulse_db pg_isready -U finpulse -d finpulse
```

**Résultat attendu :** `accepting connections`.

**Explication :** Sans PostgreSQL, aucun pipeline ni l’API ne peuvent persister ou lire des données. Redis est démarré pour l’architecture cible mais n’est pas bloquant pour un test minimal.

---

## Étape 2 — Base de données

### Ce qui se passe

Deux chemins possibles :

**A — Base vide :** Alembic applique les migrations dans `alembic/versions/` et crée les tables (`companies`, `filings`, `embeddings`, `signal_scores`, `nci_scores`, etc.).

**B — Dump fourni :** Restauration d’un snapshot déjà peuplé (recommandé pour démo / soutenance).

### Commandes — Option A (schéma vide)

```powershell
alembic upgrade head
```

### Commandes — Option B (dump)

```powershell
docker cp .\finpulse_dump.dump finpulse_db:/tmp/finpulse_dump.dump
docker exec finpulse_db pg_restore -U finpulse -d finpulse --clean --if-exists --no-owner --no-privileges /tmp/finpulse_dump.dump
```

### Vérification SQL

```powershell
docker exec -it finpulse_db psql -U finpulse -d finpulse -c "SELECT COUNT(*) AS companies FROM companies;"
docker exec -it finpulse_db psql -U finpulse -d finpulse -c "SELECT COUNT(*) AS filings FROM filings;"
docker exec -it finpulse_db psql -U finpulse -d finpulse -c "SELECT ticker, name FROM companies LIMIT 5;"
```

**Résultat attendu :** comptages > 0 si dump ou après backfill ; extension vector :

```sql
SELECT extname FROM pg_extension WHERE extname = 'vector';
```

### Vérification Python

```powershell
py Tests\test_db.py
```

**Explication :** `schema.sql` à la racine est une **photo** du schéma ; la source de vérité pour le développement reste **Alembic + modèles** dans `app/db/models/`.

---

## Étape 3 — Ingestion et traitement

### Ce qui se passe (par sous-étape)

Pour un filing SEC (10-K / 10-Q), le pipeline typique :

1. **Ingestion** — Téléchargement métadonnées + texte brut → `data/raw/` + ligne `filings`
2. **Découpage** — `processing/filing_splitter.py` → sections (`risk_factors`, `mda`, …)
3. **XBRL** — `processing/xbrl_parser.py` → `xbrl_facts`
4. **Embeddings** — `processing/embeddings.py` (Mistral) → `embeddings` (vecteurs pgvector)
5. **Flags** — Colonnes `is_extracted`, `is_xbrl_parsed`, `is_embedded` sur `filings`

Parallèlement, d’autres pipelines alimentent :

- **Form 4** → `insider_transactions`
- **News RSS** → `news_items`
- **Marché** → `market_prices`
- **Macro FRED** → `macro_observations`

### 3.1 — Backfill complet (recommandé pour une société)

**Module :** `pipelines/run_backfill_company.py` / `run_backfill_company.py`

**Explication :** Orchestre filings 10-K/10-Q, Form 4, news, marché, macro, et optionnellement le calcul des signaux (`run_signals=true`).

```powershell
py run_backfill_company.py --ticker MSFT --symbol MSFT `
  --ten-k-max 1 --ten-q-max 1 `
  --form4-max 10 --news-limit 20 `
  --market-start 2024-01-01 --macro-start 2020-01-01 `
  --run-signals
```

**Vérification :**

```powershell
docker exec finpulse_db psql -U finpulse -d finpulse -c `
  "SELECT id, form_type, is_embedded, is_signal_scored FROM filings WHERE company_id = (SELECT id FROM companies WHERE ticker='MSFT') ORDER BY filed_at DESC LIMIT 5;"
```

**Résultat attendu :** au moins un filing avec `is_embedded = t` et `is_signal_scored = t` si `--run-signals` a réussi.

---

### 3.2 — Pipeline filing seul (10-K)

**Module :** `pipelines/filing_pipeline.py`

```powershell
py -m pipelines.filing_pipeline --ticker MSFT --form 10-K --max 1
```

**Sans re-télécharger** (données déjà en base) :

```powershell
py -m pipelines.filing_pipeline --ticker MSFT --form 10-K --max 1 --skip-ingest
```

**Explication :** Utile pour re-traiter sections / embeddings / XBRL sur des filings déjà ingérés.

---

### 3.3 — Form 4 (initiés)

```powershell
py run_form4_pipeline.py --ticker MSFT --max 10
```

**Vérification :**

```sql
SELECT COUNT(*) FROM insider_transactions
WHERE company_id = (SELECT id FROM companies WHERE ticker = 'MSFT');
```

---

### 3.4 — News

```powershell
py -m pipelines.run_news_pipeline --ticker MSFT --limit 20
```

**Vérification :** lignes dans `news_items` pour la société.

---

### 3.5 — Prix de marché

```powershell
py -m pipelines.run_market_pipeline --ticker MSFT --symbol MSFT --start 2024-01-01 --end 2026-05-28
```

**Vérification :**

```sql
SELECT COUNT(*) FROM market_prices
WHERE company_id = (SELECT id FROM companies WHERE ticker = 'MSFT');
```

---

### 3.6 — Macro (FRED)

Nécessite `FRED_API_KEY` dans `.env`.

```powershell
py -m pipelines.run_macro_pipeline --start 2020-01-01
```

**Vérification :**

```sql
SELECT series_id, COUNT(*) FROM macro_observations GROUP BY series_id;
```

---

### 3.7 — Backfill via API (asynchrone)

Alternative sans CLI : déclencher le même orchestrateur via FastAPI.

1. Démarrer l’API (voir étape 5).
2. Requête :

```http
POST http://localhost:8000/api/v1/pipelines/backfill/company
Content-Type: application/json

{
  "identifier": "MSFT",
  "ten_k_max": 1,
  "ten_q_max": 1,
  "form4_max": 10,
  "news_limit": 20,
  "run_signals": true
}
```

3. Polling :

```http
GET http://localhost:8000/api/v1/pipelines/jobs/{job_id}
```

**Explication :** Retourne `202 Accepted` avec un `job_id` ; le travail s’exécute en arrière-plan dans le processus uvicorn.

---

## Étape 4 — Calcul des signaux et NCI

### Ce qui se passe

Pour un **filing ancré** (10-K ou 10-Q), le moteur sous `signals/` :

1. Calcule les signaux nommés par couche → table `signal_scores`
2. Fusionne via `composite_engine.py` → `nci_global` dans `signal_scores` et snapshot riche dans `nci_scores`
3. Peut scorer les anomalies texte (autoencodeur sectoriel) → `embeddings.anomaly_score`

**Point important :** les graphiques historiques utilisent en général **`filed_at`** en abscisse et **`nci_global`** en ordonnée.

### Commande — un filing précis

Trouver un `filing_id` :

```powershell
docker exec finpulse_db psql -U finpulse -d finpulse -c `
  "SELECT f.id, f.form_type, f.filed_at, c.ticker FROM filings f JOIN companies c ON c.id = f.company_id WHERE c.ticker='MSFT' ORDER BY f.filed_at DESC LIMIT 3;"
```

Recalculer les signaux :

```powershell
py run_signals.py --filing-id <ID>
```

### Vérification SQL

```sql
SELECT signal_name, signal_value, computed_at
FROM signal_scores
WHERE filing_id = <ID>
ORDER BY signal_name;

SELECT nci_global, confidence, coverage_ratio, computed_at
FROM nci_scores
WHERE filing_id = <ID>
ORDER BY computed_at DESC
LIMIT 1;
```

**Résultat attendu :** présence de `nci_global` et de signaux de couches (`rlds`, `market_signal`, etc.) si les données sources (XBRL, marché, news…) sont suffisantes.

**Explication du `coverage_ratio` :** si trop de couches sont manquantes (< 0,60), `nci_global` peut ne pas être produit pour ce filing (voir `signals/README.md`).

---

## Étape 5 — API de lecture

### Ce qui se passe

`main.py` démarre FastAPI. Les routes sous `app/api/v1/` lisent PostgreSQL et renvoient JSON (snapshot société, historique signaux, filings, embeddings).

L’API **ne recalcule pas** les signaux ; si un ticker est inconnu → `404`.

### Commandes

```powershell
# Terminal dédié, venv activé
py -m uvicorn main:app --reload --port 8000
```

### Vérifications

| Test | Commande / URL | Attendu |
|------|----------------|---------|
| Santé | `Invoke-RestMethod http://localhost:8000/health` | `"db": "connected"` |
| Liste tickers | `http://localhost:8000/api/v1/companies/tickers` | Tableau non vide |
| Snapshot | `http://localhost:8000/api/v1/score/MSFT` | `composite_risk_score`, `signals`, `xbrl_summary` |
| Historique NCI | `http://localhost:8000/api/v1/signals/MSFT/history` | Points `{ filed_at, signal_value }` |
| Filings | `http://localhost:8000/api/v1/filings/MSFT` | Flags `is_embedded`, `is_signal_scored` |
| Docs | http://localhost:8000/docs | Swagger UI |

**Exemple PowerShell :**

```powershell
Invoke-RestMethod http://localhost:8000/api/v1/score/MSFT | Select-Object ticker, company_name, composite_risk_score
```

**Explication :** `GET /api/v1/score/{ticker}` agrège `companies`, derniers filings, derniers signaux, résumé XBRL, insider, marché, news et fraîcheur des données (`data_freshness`).

---

## Étape 6 — Explicabilité (Spring AI)

### Ce qui se passe

1. **Python** (`signals/explainability_client.py`) collecte signaux, NCI, paragraphes anormaux, profil sectoriel.
2. Construit un **prompt** structuré.
3. Appelle le **microservice Java** (`spring-ai-service`, port **8081**) → Mistral chat.
4. Parse la réponse JSON → objet `Explanation` (résumé, facteurs clés, comparaison secteur, actions).

> L’explication n’est **pas stockée en base** par défaut : elle est affichée en CLI ou consommable par votre propre code.

### 6.1 — Démarrer Spring AI

```powershell
cd spring-ai-service
mvn -q spring-boot:run
```

(`application.yml` charge automatiquement `../.env` pour `MISTRAL_API_KEY`.)

**Vérification :** logs `Started FinpulseExplainApplication` et `Tomcat started on port 8081`.

Si `Port 8081 was already in use` :

```powershell
netstat -ano | findstr :8081
taskkill /PID <PID> /F
```

### 6.2 — Générer une explication

```powershell
cd ..
.\env\Scripts\Activate.ps1
py run_explainability.py --filing-id 123
```

Remplacer `123` par un `filing_id` réel avec `nci_scores` / embeddings.

**Sauvegarder dans un fichier :**

```powershell
py run_explainability.py --filing-id 123 | Out-File -Encoding utf8 explanation_123.json
```

### Résultat attendu (extrait)

```json
{
  "ticker": "MSFT",
  "nci_score": 0.0,
  "summary": "...",
  "key_drivers": [{ "signal": "...", "contribution": "..." }],
  "sector_comparison": { "text": "...", "sector_avg_nci": 0.35 },
  "recommended_actions": ["..."],
  "model_used": "spring_ai"
}
```

| `model_used` | Signification |
|--------------|---------------|
| `spring_ai` | Mistral via service Java OK |
| `fallback_template` | Spring/Mistral indisponible → texte déterministe |

### Endpoints Spring (debug manuel)

| Méthode | URL | Usage |
|---------|-----|--------|
| POST | `http://localhost:8081/api/v1/explain` | Payload prompt V1 (`ExplicabilityEngine`) |
| POST | `http://localhost:8081/api/explain/anomaly` | Paragraphes anormaux (`request_explanation`) |

---

## Étape 7 — Tests automatisés

### Ce qui se passe

La suite `Tests/` valide parsers, signaux, API (mock DB), pipelines (SQLite mémoire), etc.

### Commandes

```powershell
# Sans test d'intégration PostgreSQL + torch lourd
pytest Tests/ -q --ignore=Tests/test_sector_autoencoder_integration.py

# Sous-ensemble rapide
pytest Tests/test_api.py Tests/test_composite_signals.py Tests/test_form4_parser.py -q
```

**Résultat attendu :** `passed` sans `failed` (sauf dépendances manquantes : installer `pytest`, `torch`, etc.).

**Explication :** `Tests/conftest.py` utilise SQLite en mémoire pour la majorité des tests ; l’intégration autoencodeur nécessite PostgreSQL réel.

---

## Workflow « démo rapide » (30 min)

Ordre recommandé si vous avez déjà un **dump** restauré :

| # | Action | Commande / URL |
|---|--------|----------------|
| 1 | Docker | `docker compose up -d` |
| 2 | Venv + deps | `.\env\Scripts\Activate.ps1` + `pip install -r requirements.txt` |
| 3 | Santé DB | `docker exec finpulse_db pg_isready -U finpulse` |
| 4 | API | `py -m uvicorn main:app --port 8000` |
| 5 | Health | http://localhost:8000/health |
| 6 | Score | http://localhost:8000/api/v1/score/MSFT |
| 7 | Spring AI | `cd spring-ai-service; mvn -q spring-boot:run` |
| 8 | Explication | `py run_explainability.py --filing-id <ID>` |
| 9 | Tests | `pytest Tests/test_api.py -q` |

---

## Dépannage

| Symptôme | Cause probable | Action |
|----------|----------------|--------|
| `db: "error"` sur `/health` | PostgreSQL arrêté | `docker compose up -d` |
| `404` sur `/score/TICKER` | Société absente | Backfill ou restore dump |
| `Mistral API key must be set` | Clé absente pour Spring | `MISTRAL_API_KEY` dans `FinPulse/.env` |
| `Port 8081 already in use` | Ancienne instance Java | `taskkill` sur le PID |
| `Spring AI call failed: Expecting value...` | Mauvais port (8080 = Adminer) | `SPRING_AI_SERVICE_URL=http://localhost:8081` |
| `key_drivers: []` avec JSON dans `summary` | Parse markdown | Redémarrer Spring après mise à jour `ExplainService` |
| API ne démarre pas (import torch) | PyTorch requis au boot | `pip install torch` (build CPU) |
| Embeddings échouent | Clé Mistral | Vérifier `MISTRAL_API_KEY` |
| SEC bloqué | User-Agent invalide | `EDGAR_USER_AGENT` avec email réel |

---

## Références

| Document | Contenu |
|----------|---------|
| [README.md](./README.md) | Architecture générale |
| [VERIFICATION.md](./VERIFICATION.md) | Checklist de validation |
| [app/api/README.md](./app/api/README.md) | Détail des endpoints API |
| [signals/README.md](./signals/README.md) | Catalogue des signaux |
| [spring-ai-service/README.md](./spring-ai-service/README.md) | Microservice explicabilité |

---

## Carte des dossiers (rappel)

```text
FinPulse/
├── app/              # API FastAPI + config + modèles SQLAlchemy
├── ingestion/        # Clients SEC, news, marché, FRED
├── processing/       # Parsers, embeddings, FinBERT
├── pipelines/        # Orchestration runnable
├── signals/          # Moteur de risque + explicabilité client
├── spring-ai-service/# Service Java Mistral (explications)
├── Tests/            # pytest
├── alembic/          # Migrations
├── main.py           # Point d'entrée API
├── run_*.py          # Scripts CLI
└── docker-compose.yml
```

---

*Document généré pour le dépôt FinPulse — workflow ingestion → signaux → API → explicabilité.*
