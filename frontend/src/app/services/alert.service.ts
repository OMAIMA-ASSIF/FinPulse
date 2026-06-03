import { Injectable, inject, signal, computed } from '@angular/core';
import { ApiService } from './api.service';
import { Alert } from '../models';

@Injectable({ providedIn: 'root' })
export class AlertService {
  private readonly api = inject(ApiService);

  readonly alerts   = signal<Alert[]>([]);
  readonly loading  = signal(false);

  readonly unreadCount = computed(() =>
    this.alerts().filter(a => !a.isRead).length
  );

  // ── Load ───────────────────────────────────────────────────────────────────
  load(userId?: number): void {
    this.loading.set(true);
    this.api.getAlerts(0, 100).subscribe({
      next: page => {
        const normalized = (page.content ?? []).map(a => this.normalizeAlert(a));
        const sorted = [...normalized].sort((a, b) => {
          if (a.isRead === b.isRead) {
            return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
          }
          return a.isRead ? 1 : -1;
        });
        this.alerts.set(sorted);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  // ── Computed queries ───────────────────────────────────────────────────────

  private normalizeAlert(raw: Alert): Alert {
    const r = raw as Alert & { company_ticker?: string; company_name?: string; is_read?: boolean; read?: boolean };
    return {
      ...raw,
      companyTicker: String(raw.companyTicker ?? r.company_ticker ?? '').trim(),
      companyName: String(raw.companyName ?? r.company_name ?? '').trim(),
      isRead: Boolean(raw.isRead ?? r.is_read ?? r.read ?? false),
    };
  }

  /** Retourne les alertes pour un ticker donné */
  alertsForCompany(ticker: string): Alert[] {
    const t = ticker?.trim().toUpperCase();
    return this.alerts().filter(a => a.companyTicker?.toUpperCase() === t);
  }

  /** Nombre d'alertes non lues pour un ticker */
  unreadForCompany(ticker: string): number {
    return this.alertsForCompany(ticker).filter(a => !a.isRead).length;
  }

  /** Alertes pour toutes les entreprises de la watchlist (tickers + noms en secours) */
  alertsForWatchlist(tickers: string[], companyNames: string[] = []): Alert[] {
    const tickerSet = new Set(tickers.map(t => t.toUpperCase()));
    const nameSet = new Set(companyNames.map(n => n.toUpperCase()));
    return this.alerts().filter(a => {
      const t = a.companyTicker?.toUpperCase();
      const n = a.companyName?.toUpperCase();
      return (t && tickerSet.has(t)) || (n && nameSet.has(n));
    });
  }

  /** Nombre d'alertes non lues pour la watchlist */
  unreadCountForWatchlist(tickers: string[]): number {
    return this.alertsForWatchlist(tickers).filter(a => !a.isRead).length;
  }

  // ── Actions ────────────────────────────────────────────────────────────────

  markRead(alertId: number): void {
    this.api.markAlertRead(alertId).subscribe(updated => {
      this.alerts.update(list =>
        list.map(a => a.id === alertId ? { ...a, isRead: true } : a)
      );
    });
  }

  markAllRead(userId: number): void {
    this.api.markAllRead().subscribe(() => {
      this.alerts.update(list => list.map(a => ({ ...a, isRead: true })));
    });
  }

  /** Ajoute une alerte reçue via SSE */
  addAlert(alert: Alert): void {
    this.alerts.update(list => [alert, ...list]);
  }
}
