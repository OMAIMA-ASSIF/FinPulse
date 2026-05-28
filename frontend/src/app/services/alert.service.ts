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
        // Sort: unread first, then by date desc
        const sorted = [...page.content].sort((a, b) => {
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

  /** Retourne les alertes pour un ticker donné */
  alertsForCompany(ticker: string): Alert[] {
    return this.alerts().filter(a =>
      a.companyTicker?.toUpperCase() === ticker?.toUpperCase()
    );
  }

  /** Nombre d'alertes non lues pour un ticker */
  unreadForCompany(ticker: string): number {
    return this.alertsForCompany(ticker).filter(a => !a.isRead).length;
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
