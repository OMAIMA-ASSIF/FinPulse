# FinPulse

## Overview

FinPulse is a research platform for financial risk monitoring of public companies. The project includes multiple services and components to ingest data, detect anomalies, explain risk, and assist users through a hierarchical conversational agent.

The system focuses on three core services:

1. Data ingestion service
2. Anomaly explanation service
3. Hierarchical multi-agent assistant

The architecture combines Python pipelines, a Java Spring Boot backend, and an Angular frontend.

---

## System Architecture

### Main components

- `Data-Ingestion-Pipeline/`
  - Python service responsible for data ingestion, processing, and scoring.
- `backend/`
  - Java Spring Boot application that consumes pipeline data, exposes APIs, and handles business logic.
- `frontend/`
  - Angular application for visualization, dashboards, and the conversational assistant.

### Overall flow

1. The ingestion service collects public data sources: SEC EDGAR, XBRL, Form 4, news, market, macro.
2. It transforms these sources into financial signals, text anomalies, and composite risk scores (`nci_global`).
3. The Java backend retrieves those results via APIs and serves the frontend.
4. An explanation service enriches anomalies with generated text from an LLM (via Spring AI / Mistral).
5. The frontend provides a user interface with a hierarchical intelligent assistant for answers and explanations.

---

## Data Ingestion Service

### Purpose

Collect, normalize, and store all financial data sources required to generate risk signals and anomalies.

### Key components

- `Data-Ingestion-Pipeline/main.py`
  - Primary entry point for the Python pipeline.
- `Data-Ingestion-Pipeline/pipeline.py`
  - Orchestrates the ingestion and transformation stages.
- `Data-Ingestion-Pipeline/ingestion/`
  - `edgar_client.py`: retrieves SEC filings and metadata.
  - `form4_client.py`: retrieves Form 4 filings and insider transaction data.
  - `news_client.py`: fetches news items related to companies.
  - `market_client.py`: retrieves price and volume history.
  - `fred_client.py`: retrieves macroeconomic series.
- `Data-Ingestion-Pipeline/processing/`
  - `filing_splitter.py`: splits filings into sections.
  - `embeddings.py`: computes embeddings and text anomaly scores.
  - `news_sentiment.py`: computes sentiment signals.
  - `xbrl_parser.py`: extracts structured financial XBRL facts.

### Core capabilities

- Multi-source ingestion: SEC, XBRL, Form 4, news, market, macro.
- Filing text segmentation.
- Paragraph-level embedding generation.
- Sector anomaly scoring and text anomaly detection.
- Multi-layer signal computation: text, numeric, behavior, market, sentiment.
- Composite score construction: `nci_global`.

### Example commands

```powershell
cd Data-Ingestion-Pipeline
py -m pipelines.filing_pipeline --ticker AAPL --form 10-K --max 5
py -m pipelines/run_news_pipeline.py --ticker AAPL --limit 50
py -m pipelines/run_market_pipeline.py --ticker AAPL --symbol AAPL --start 2020-01-01 --end 2026-01-01
py -m pipelines/run_backfill_company.py --ticker AAPL
```

### Expected outputs

- Database tables in PostgreSQL or local SQLite: `companies`, `filings`, `filing_sections`, `xbrl_facts`, `insider_transactions`, `market_prices`, `news_items`, `signal_scores`, `nci_scores`, `embeddings`.
- Explicit risk signals such as `numeric_anomaly`, `market_signal`, `sentiment_signal`, `insider_signal`, `forward_pessimism`.
- Paragraph-level text anomaly scores.

---

## Anomaly Explanation Service

### Purpose

Turn detected anomalies into human-understandable explanations, including key drivers, sector comparison, and recommended actions.

### Key components

- `Data-Ingestion-Pipeline/signals/explainability_client.py`
  - Collects signals, anomalies, and builds a structured prompt.
- `Data-Ingestion-Pipeline/WORKFLOW.md`
  - Documents the explainability process, especially Spring AI integration.
- `backend/`
  - The Java backend can consume LLM-generated explanations to enrich the UI.

### How it works

1. The pipeline identifies important anomalies in signals and text paragraphs.
2. It builds a structured prompt containing:
   - `nci_global` scores
   - each layer’s signals
   - anomalous text paragraphs
   - sector-level comparison
3. A Spring Boot microservice exposes an explanation endpoint to Mistral or another LLM.
4. The response is parsed into a JSON `Explanation` object.

### Output

- A concise summary of the situation.
- Key risk drivers.
- Sector benchmarking and comparison.
- Recommended actions.
- Metadata about the model used.

### Usage example

```powershell
cd Data-Ingestion-Pipeline
py run_explainability.py --filing-id 123
```

This command should produce JSON with fields like `summary`, `key_drivers`, `sector_comparison`, and `recommended_actions`.

---

## Hierarchical Multi-Agent Assistant

### Purpose

Provide a conversational user interface that can explore data, explain anomalies, and generate strategy guidance using multiple agent levels.

### Key components

- `frontend/src/app/services/assistant.service.ts`
  - Sends user messages to the `/assistant/chat` API.
- `frontend/src/app/pages/chatbot/chatbot.component.ts`
  - Manages the chat UI.
- `backend/src/main/java/ma/enset/backend/service/IngestionPipelineService.java`
  - Retrieves score, anomaly, and explanation data from the ingestion service.

### Hierarchical architecture

The "hierarchical assistant" approach includes:

- A front-facing agent (`AGENT`) that talks with the user.
- A strategy-level agent (`STRATEGY`) that structures reports, recommends actions, and maintains conversation context.
- A coordination layer that uses business data (NCI scores, anomalies, explanations) from the backend.

### How it operates

1. The user asks a question in the AI Agent interface.
2. The frontend sends the message and ticker to the API via `AssistantService.chat(...)`.
3. The backend orchestrates required data:
   - NCI score
   - detected anomalies
   - LLM explanation
   - company context
4. The system selects a response mode:
   - `CHATBOT` for direct answers,
   - `CLARIFICATION` for follow-up questions,
   - `REPORT` for detailed summaries.
5. Sub-agents may be invoked to:
   - search anomalies,
   - analyze market trends,
   - generate investment recommendations.

### Why it matters

- This assistant is not just a chatbot; it uses real pipeline data.
- It keeps a `conversationId` to track dialogue state and switch between `AGENT` and `STRATEGY` modes.
- It can generate reports and save strategy outputs.

---

## Core Services Explained

### 1. Data Ingestion Service

- Collects and normalizes public financial data.
- Produces advanced risk signals and anomaly measures.
- Enables multi-layer analysis: text, numeric, behavior, market, sentiment.
- Stores outputs for API and frontend consumption.

### 2. Anomaly Explanation Service

- Consumes pipeline anomalies.
- Generates text explanations via an LLM engine.
- Provides summaries, root causes, and recommendations.
- Acts as a trust layer for end users.

### 3. Hierarchical Assistant Service

- Conversational frontend interface.
- Backend business data orchestration.
- Multiple response modes (`CHATBOT`, `CLARIFICATION`, `REPORT`).
- Hierarchical design separating user action from strategy generation.

---

## Local Run Instructions

### Requirements

- Python 3.11+ for `Data-Ingestion-Pipeline`
- PostgreSQL for production data
- Java + Maven for the Spring Boot backend
- Node.js / npm for the Angular frontend
- Mistral API key if using the explanation service

### Quick start

1. Install Python dependencies:

```powershell
cd Data-Ingestion-Pipeline
py -m pip install -r requirements.txt
```

2. Start the Java backend:

```powershell
cd backend
./mvnw spring-boot:run
```

3. Start the frontend:

```powershell
cd frontend
npm install
npm start
```

4. Start the ingestion pipeline and load data:

```powershell
cd Data-Ingestion-Pipeline
py -m pipelines/run_backfill_company.py --ticker AAPL
```

5. Test the API and explanation service:

- `http://localhost:8000/health`
- `http://localhost:8000/api/v1/score/AAPL`
- `http://localhost:8081/api/v1/explain`

---

## Repository layout

- `backend/`: Java Spring Boot backend, business services, API integration.
- `Data-Ingestion-Pipeline/`: Python ingestion pipeline, signal generation, explanation logic.
- `frontend/`: Angular app for dashboards and the assistant.
- `finpulse/`: additional analysis and exploration modules.

---

## Key takeaways

- The ingestion service is the data engine: it feeds the entire system.
- The explanation service turns technical anomalies into actionable insights.
- The hierarchical assistant enables a richer interaction than a simple dashboard.
- The architecture separates ingestion, analysis, explanation, and interaction.

---

## Notes

- This project is research-oriented and prototype-focused.
- Scores and explanations are generated offline and served in read-only mode via the API.
- The conversational agents can be extended to support additional workflows.

