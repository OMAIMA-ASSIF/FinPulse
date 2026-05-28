import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Company } from '../models';

export interface WatchlistEntry {
  id: number;
  company: Company;
  pinnedAt: string;
}

/**
 * Service gérant la Watchlist de l'utilisateur.
 *
 * INDÉPENDANT de StrategyService :
 * - Pin/Unpin ne touche JAMAIS aux stratégies.
 * - Les stratégies auto-pinent via le backend (StrategyService.createStrategy).
 */
@Injectable({ providedIn: 'root' })
export class WatchlistService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  readonly entries      = signal<WatchlistEntry[]>([]);
  readonly loading      = signal(false);
  readonly pinnedIds    = computed(() => new Set(this.entries().map(e => e.company.id)));

  // ── Load ────────────────────────────────────────────────────────────────────

  load(): void {
    this.loading.set(true);
    this.http.get<WatchlistEntry[]>(`${this.base}/watchlist`).subscribe({
      next: list => { this.entries.set(list); this.loading.set(false); },
      error: ()  => this.loading.set(false)
    });
  }

  // ── Queries ─────────────────────────────────────────────────────────────────

  isPinned(companyId: number): boolean {
    return this.pinnedIds().has(companyId);
  }

  pinnedCompanies(): Company[] {
    return this.entries().map(e => e.company);
  }

  // ── Pin ─────────────────────────────────────────────────────────────────────

  pin(companyId: number): Observable<WatchlistEntry> {
    return new Observable(observer => {
      this.http.post<WatchlistEntry>(`${this.base}/watchlist/${companyId}`, null)
        .subscribe({
          next: entry => {
            // Optimistic update — évite les doublons
            if (!this.isPinned(companyId)) {
              this.entries.update(list => [entry, ...list]);
            }
            observer.next(entry);
            observer.complete();
          },
          error: err => observer.error(err)
        });
    });
  }

  // ── Unpin ───────────────────────────────────────────────────────────────────

  /**
   * Dépingle de la Watchlist UNIQUEMENT.
   * Ne supprime JAMAIS une stratégie associée.
   */
  unpin(companyId: number): Observable<void> {
    return new Observable(observer => {
      this.http.delete<void>(`${this.base}/watchlist/${companyId}`)
        .subscribe({
          next: () => {
            this.entries.update(list => list.filter(e => e.company.id !== companyId));
            observer.next();
            observer.complete();
          },
          error: err => observer.error(err)
        });
    });
  }

  // ── Sync après Save Strategy ─────────────────────────────────────────────────

  /**
   * Appelé par le chatbot après "Save Strategy".
   * Recharge la watchlist pour refléter l'auto-pin effectué par le backend.
   */
  refreshAfterStrategySave(): void {
    this.load();
  }
}
