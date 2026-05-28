import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  Company, Dashboard, NciHistory, News, Strategy, Alert,
  PageResponse, CreateStrategyRequest
} from '../models';

export interface PriceData {
  ticker: string;
  price: number;
  change24h: number;
  changePct24h: number;
  currency: string;
}

export interface StrategyReport {
  id: number;
  ticker: string;
  companyName: string;
  thesis: string;
  bullCase: string[];
  risks: string[];
  secContradictions: string[];
  historicalInsight: string;
  recommendation: 'BUY' | 'HOLD' | 'AVOID';
  nciPersonalized: number;
  generatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  // ── Companies ───────────────────────────────────────────────────
  getCompanies(): Observable<Company[]> {
    return this.http.get<Company[]>(`${this.base}/companies`);
  }
  getDashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.base}/companies/dashboard`);
  }
  getLeaderboard(limit = 10): Observable<Company[]> {
    return this.http.get<Company[]>(`${this.base}/companies/leaderboard`, {
      params: new HttpParams().set('limit', limit)
    });
  }
  getAtRisk(threshold = 0.4): Observable<Company[]> {
    return this.http.get<Company[]>(`${this.base}/companies/at-risk`, {
      params: new HttpParams().set('threshold', threshold)
    });
  }
  getCompanyId(ticker: string): Observable<{ id: number }> {
    return this.http.get<{ id: number }>(`${this.base}/companies/ticker/${ticker}/id`);
  }
  getCompanyById(id: number): Observable<Company> {
    return this.http.get<Company>(`${this.base}/companies/${id}`);
  }
  getCompanyByTicker(ticker: string): Observable<Company> {
    return this.http.get<Company>(`${this.base}/companies/ticker/${ticker}`);
  }
  searchCompanies(q: string): Observable<Company[]> {
    return this.http.get<Company[]>(`${this.base}/companies/search`, {
      params: new HttpParams().set('q', q)
    });
  }
  getCompanyPrice(id: number): Observable<PriceData> {
    return this.http.get<PriceData>(`${this.base}/companies/${id}/price`);
  }

  // ── NCI History ─────────────────────────────────────────────────
  getNciHistory(companyId: number): Observable<NciHistory[]> {
    return this.http.get<NciHistory[]>(`${this.base}/nci-history/${companyId}`);
  }
  getNciHistorySince(companyId: number, months: number): Observable<NciHistory[]> {
    return this.http.get<NciHistory[]>(`${this.base}/nci-history/${companyId}/months/${months}`);
  }
  getNciTrend(companyId: number): Observable<{ trend: string }> {
    return this.http.get<{ trend: string }>(`${this.base}/nci-history/${companyId}/trend`);
  }

  // ── News ────────────────────────────────────────────────────────
  getLatestNews(companyId: number): Observable<News[]> {
    return this.http.get<News[]>(`${this.base}/news/${companyId}/latest`);
  }
  getNews(companyId: number, page = 0, size = 10): Observable<PageResponse<News>> {
    return this.http.get<PageResponse<News>>(`${this.base}/news/${companyId}`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  // ── Strategies ──────────────────────────────────────────────────
  getStrategies(): Observable<Strategy[]> {
    return this.http.get<Strategy[]>(`${this.base}/strategies`);
  }
  getAllStrategies(): Observable<Strategy[]> {
    return this.http.get<Strategy[]>(`${this.base}/strategies/all`);
  }
  getStrategy(id: number): Observable<Strategy> {
    return this.http.get<Strategy>(`${this.base}/strategies/${id}`);
  }
  createStrategy(body: CreateStrategyRequest): Observable<Strategy> {
    return this.http.post<Strategy>(`${this.base}/strategies`, body);
  }
  // Sécurisé : plus de userId en param — JWT extrait côté backend
  deactivateStrategy(id: number): Observable<void> {
    return this.http.patch<void>(`${this.base}/strategies/${id}/deactivate`, null);
  }
  reactivateStrategy(id: number): Observable<void> {
    return this.http.patch<void>(`${this.base}/strategies/${id}/reactivate`, null);
  }
  deleteStrategy(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/strategies/${id}`);
  }
  // View Report — appel backend Spring AI
  getStrategyReport(id: number): Observable<StrategyReport> {
    return this.http.get<StrategyReport>(`${this.base}/strategies/${id}/report`);
  }
  // Download PDF — retourne Blob
  downloadStrategyPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/strategies/${id}/report/pdf`, {
      responseType: 'blob'
    });
  }

  // ── Alerts ──────────────────────────────────────────────────────
  getAlerts(page = 0, size = 50): Observable<PageResponse<Alert>> {
    return this.http.get<PageResponse<Alert>>(`${this.base}/alerts`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }
  markAlertRead(alertId: number): Observable<Alert> {
    return this.http.patch<Alert>(`${this.base}/alerts/${alertId}/read`, {});
  }
  markAllRead(): Observable<{ marked: number }> {
    return this.http.patch<{ marked: number }>(`${this.base}/alerts/read-all`, {});
  }

  // ── Chat ────────────────────────────────────────────────────────
  sendChat(message: string): Observable<string> {
    return this.http.post(`${this.base}/chat`, { message }, { responseType: 'text' });
  }
}
