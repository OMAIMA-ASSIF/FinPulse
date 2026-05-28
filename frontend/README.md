# FinPulse Frontend v4

Production-ready Angular 19 frontend for the FinPulse AI Financial Analytics platform.

## Stack
- **Angular 19** — Standalone components, Signals API
- **Pure CSS** — No Tailwind, no SCSS, dark fintech theme with CSS variables
- **Chart.js 4** — Interactive NCI history charts
- **Keycloak Angular 16** — OAuth2 authentication
- **SSE (EventSource)** — Real-time NCI updates

## Architecture

```
src/app/
├── core/
│   ├── guards/auth.guard.ts          ← Keycloak route protection
│   └── interceptors/auth.interceptor.ts ← Bearer token injection
├── models/index.ts                    ← All TypeScript interfaces (matches Spring Boot DTOs)
├── services/
│   ├── api.service.ts                 ← All REST calls to backend
│   ├── auth.service.ts                ← Keycloak wrapper (Signals)
│   ├── sse.service.ts                 ← SSE EventSource + auto-reconnect
│   ├── alert.service.ts               ← Alert state management (Signals)
│   └── strategy.service.ts            ← Strategy CRUD (Signals)
├── shared/
│   └── components/header/             ← Global header (all pages)
└── pages/
    ├── dashboard/                     ← 3-column layout
    │   ├── dashboard.component.*      ← Parent orchestrator
    │   └── components/
    │       ├── watchlist-sidebar/     ← Left: live company list (SSE)
    │       ├── company-header/        ← Center: company title bar
    │       ├── metrics-panel/         ← Center: NCI + Sentiment + Risk
    │       ├── nci-chart/             ← Center: Chart.js NCI history
    │       ├── alert-center/          ← Right: filtered alerts + mark-read
    │       └── breaking-news/         ← Right: latest news with links
    ├── chatbot/                       ← AI Assistant (ChatGPT-style UI)
    ├── strategy-tester/               ← Strategy form + AI analysis + save
    └── profile/                       ← User profile + PRUDENT/SPÉCULATEUR
```

## Installation

```bash
npm install
```

## Configuration Keycloak

1. Create realm `finpulse` at http://localhost:8180/admin
2. Create client `finpulse-frontend`:
   - Client type: **Public**
   - Valid redirect URIs: `http://localhost:4200/*`
   - Web origins: `http://localhost:4200`
3. Modify `src/environments/environment.ts` if needed

## Launch

```bash
# Development (proxy /api → localhost:8080)
npm start
# → http://localhost:4200
```

## Backend connection

The `proxy.conf.json` routes all `/api/*` calls to `http://localhost:8080`.

| Frontend Page       | Backend Endpoint(s)                                |
|---------------------|----------------------------------------------------|
| Dashboard           | GET /api/companies/leaderboard, /api/nci-history   |
| Watchlist (SSE)     | GET /api/stream/watchlist                          |
| Alert Center        | GET /api/alerts, PATCH /api/alerts/{id}/read       |
| Breaking News       | GET /api/news/{companyId}/latest                   |
| Chatbot             | POST /api/chat (Spring AI)                         |
| Strategy Tester     | POST /api/strategies, GET /api/companies           |
| Profile             | GET /api/users/username/{name}                     |

## Key Design Decisions

- **Signals over RxJS**: All service state uses `signal()` and `computed()`. RxJS only for SSE and HTTP.
- **Personalized NCI**: Displayed in MetricsPanel when a UserStrategy exists for the selected company.
- **Save Strategy flow**: Chatbot/StrategyTester → `POST /api/strategies` → Watchlist auto-updates via SSE.
- **Alert filtering**: AlertCenter shows only alerts for the currently selected company ticker.
- **Profile impact**: PRUDENT (×1.0) vs SPÉCULATEUR (×1.15) multiplier applied to nciPersonalized.

## Build

```bash
npm run build
# Output: dist/finpulse-v4/
```
