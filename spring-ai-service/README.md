# FinPulse — Service Spring AI (explicabilité)

Microservice Java consommé par le backend Python (`signals/explainability_client.py`).

## Ouvrir dans IntelliJ IDEA

1. **File → Open** → sélectionner le dossier `spring-ai-service` (IntelliJ détecte Maven).
2. **JDK 17+** : *File → Project Structure → Project SDK*.
3. Copier `.env.example` vers `.env` à la racine de `FinPulse` et définir `MISTRAL_API_KEY` (ou une autre clé selon le modèle choisi).
4. Variables d'environnement pour le run IntelliJ (*Run → Edit Configurations → Environment*):
   - `MISTRAL_API_KEY=<votre_clé>`
   - `SERVER_PORT=8081` (recommandé : Adminer Docker utilise déjà le port **8080**)
5. Lancer `FinpulseExplainApplication` (classe principale).

## Côté Python

Dans `.env` à la racine FinPulse :

```env
SPRING_AI_BASE_URL=http://localhost:8081
SPRING_AI_EXPLAIN_ENDPOINT=/api/v1/explain
```

Test :

```powershell
cd ..
.\venv\Scripts\Activate.ps1
python run_explainability.py --filing-id <ID>
```

## Endpoints

| Méthode | Chemin | Client Python |
|--------|--------|----------------|
| POST | `/api/v1/explain` | `ExplicabilityEngine._step5_call_spring_ai` |
| POST | `/api/explain/anomaly` | `request_explanation()` / `build_explanation_request` |

## Build Maven (terminal)

**Erreur fréquente :** `Mistral API key must be set` — la clé doit être dans `FinPulse/.env` (racine du projet, pas dans `spring-ai-service/`).

1. Vérifiez `FinPulse/.env` :

```env
MISTRAL_API_KEY=votre_cle_mistral
SERVER_PORT=8081
```

2. Depuis `spring-ai-service` :

```powershell
cd spring-ai-service
mvn -q spring-boot:run
```

`application.yml` importe automatiquement `../.env`. Alternative : `.\run.ps1`.

**Erreur :** `Port 8081 was already in use` — une ancienne instance tourne encore :

```powershell
netstat -ano | findstr :8081
taskkill /PID <PID> /F
```

Ou changez de port : `SERVER_PORT=8082` dans `FinPulse/.env`.
