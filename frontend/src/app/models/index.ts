/* ================================================================
   FinPulse— Domain Models (matches Spring Boot entities)
   ================================================================ */

// ── Company (CompanyDTO) ─────────────────────────────────────────
export interface Company {
  id: number;
  ticker: string;
  name: string;
  sector: string;
  nciGlobal: number;          // 0.0 – 1.0
  sentimentAvg: number;       // FinBERT raw [-1.0, 1.0] from P1
  lastUpdate: string;
  nciLabel: NciLabel;
  riskLevel: RiskLevel;
}

export type NciLabel  = 'HIGH' | 'MEDIUM' | 'LOW';
export type RiskLevel = 'LOW_RISK' | 'MEDIUM_RISK' | 'HIGH_RISK';

// ── Dashboard ────────────────────────────────────────────────────
export interface Dashboard {
  globalNciAverage: number;
  globalSentimentAverage: number;
  topCompanies: Company[];
  atRiskCompanies: Company[];
  totalCompanies: number;
  generatedAt: string;
}

// ── NCI History ──────────────────────────────────────────────────
export interface NciHistory {
  id: number;
  companyId: number;
  ticker: string;
  nciValue: number;
  recordedAt: string;
  reason: string;
}

// ── News (NewsDTO) ───────────────────────────────────────────────
export interface News {
  id: number;
  companyId: number;
  ticker: string;
  title: string;
  url: string;
  source: string;
  sentimentScore: number;
  sentimentLabel: SentimentLabel;
  publishedAt: string;
}
export type SentimentLabel = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';

// ── User ─────────────────────────────────────────────────────────
export interface User {
  id: number;
  keycloakId: string;
  username: string;
  email: string;
  profileType: ProfileType;
  createdAt: string;
  strategyCount: number;
  avatarUrl?: string | null;
  firstLogin?: boolean;  
}
export type ProfileType = 'PRUDENT' | 'SPECULATEUR';

// ── Strategy (UserStrategy) ──────────────────────────────────────
export interface Strategy {
  id: number;
  company: Company;
  userArgument: string;
  nciPersonalized: number;    
  isActive: boolean;
  createdAt: string;
  unreadAlerts: number;
}

export interface CreateStrategyRequest {
  companyId: number;
  userArgument: string;
}

// ── Alert ────────────────────────────────────────────────────────
export interface Alert {
  id: number;
  strategyId: number;
  companyTicker: string;
  companyName: string;
  alertType: AlertType;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export type AlertType =
  | 'NCI_DROP'
  | 'NCI_RISE'
  | 'SENTIMENT_NEGATIVE'
  | 'SENTIMENT_POSITIVE'
  | 'STRATEGY_RISK'
  | 'COMMUNICATION_CRISIS';

// ── SSE Event (NciUpdateEvent) ───────────────────────────────────
export interface NciUpdateEvent {
  companyId: number;
  ticker: string;
  name: string;
  nciValue: number;
  previousNci: number;
  sentimentAvg: number;
  trend: 'UP' | 'DOWN' | 'STABLE';
  timestamp: string;
}

// ── Chat ─────────────────────────────────────────────────────────
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  loading?: boolean;
  mode?: 'AGENT' | 'STRATEGY';
  strategyCard?: StrategyCard;
}

export interface StrategyCard {
  companyName: string;
  ticker: string;
  thesis: string;
  bullCase: string[];
  risks: string[];
  contradictions: string[];
  historicalInsight: string;
  recommendation: 'BUY' | 'HOLD' | 'AVOID';
  nciPersonalized: number;
}

// ── Pagination ───────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// ── Keycloak User ────────────────────────────────────────────────
export interface KeycloakUser {
  sub: string;
  preferred_username: string;
  email: string;
  given_name?: string;
  family_name?: string;
  realm_access?: { roles: string[] };
}

// ── Risk Explanation (from Data-Ingestion-Pipeline) ───────────────
export interface RiskExplanation {
  llm_explanation: string | null;
  llm_explanation_meta: {
    risk_level: string;
    confidence: number;
    model_used: string;
    key_drivers: string[];
    recommended_actions: string[];
    generated_at: string;
  } | null;
}

// ── Anomaly (from Data-Ingestion-Pipeline) ───────────────────────
export interface Anomaly {
  text: string;
  section: string | null;
  anomaly_score: number;
  mse: number;
  sector_threshold: number;
}
