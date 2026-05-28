import { Injectable, inject, signal, computed } from '@angular/core';
import { ApiService, StrategyReport } from './api.service';
import { Strategy, CreateStrategyRequest } from '../models';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class StrategyService {
  private readonly api = inject(ApiService);

  readonly strategies = signal<Strategy[]>([]);
  readonly loading    = signal(false);
  readonly saving     = signal(false);

  readonly activeStrategies   = computed(() => this.strategies().filter(s => s.isActive));
  readonly archivedStrategies = computed(() => this.strategies().filter(s => !s.isActive));

  hasStrategy(companyId: number): boolean {
    return this.strategies().some(s => s.company.id === companyId && s.isActive);
  }

  getStrategyForCompany(companyId: number): Strategy | undefined {
    return this.strategies().find(s => s.company.id === companyId && s.isActive);
  }

  /** Charge toutes les stratégies (actives + archivées) */
  load(): void {
    this.loading.set(true);
    this.api.getAllStrategies().subscribe({
      next: list => { this.strategies.set(list); this.loading.set(false); },
      error: ()  => {
        // Fallback: getStrategies (actives seulement)
        this.api.getStrategies().subscribe({
          next: list => { this.strategies.set(list); this.loading.set(false); },
          error: ()  => this.loading.set(false)
        });
      }
    });
  }

  create(req: CreateStrategyRequest): Promise<Strategy> {
    this.saving.set(true);
    return new Promise((resolve, reject) => {
      this.api.createStrategy(req).subscribe({
        next: strategy => {
          this.strategies.update(list => [strategy, ...list]);
          this.saving.set(false);
          resolve(strategy);
        },
        error: err => { this.saving.set(false); reject(err); }
      });
    });
  }

  /** Désactiver → archived */
  deactivate(id: number): void {
    this.api.deactivateStrategy(id).subscribe(() => {
      this.strategies.update(l => l.map(s => s.id === id ? { ...s, isActive: false } : s));
    });
  }

  /** Réactiver depuis archived */
  reactivate(id: number): void {
    this.api.reactivateStrategy(id).subscribe(() => {
      this.strategies.update(l => l.map(s => s.id === id ? { ...s, isActive: true } : s));
    });
  }

  delete(id: number): void {
    this.api.deleteStrategy(id).subscribe(() => {
      this.strategies.update(list => list.filter(s => s.id !== id));
    });
  }

  getReport(id: number): Observable<StrategyReport> {
    return this.api.getStrategyReport(id);
  }

  downloadPdf(id: number): void {
    this.api.downloadStrategyPdf(id).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a   = document.createElement('a');
      a.href     = url;
      a.download = `strategy-report-${id}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
