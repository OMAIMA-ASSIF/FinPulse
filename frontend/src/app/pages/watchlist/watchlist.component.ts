import {
  Component, OnInit, OnDestroy, inject, signal, computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { SseService } from '../../services/sse.service';
import { AlertService } from '../../services/alert.service';
import { StrategyService } from '../../services/strategy.service';
import { WatchlistService } from '../../services/watchlist.service';
import { NciChartComponent } from '../../shared/components/nci-chart/nci-chart.component';
import { ApiService as Api, PriceData } from '../../services/api.service';
import { Company, News, NciUpdateEvent, RiskExplanation } from '../../models';
import { firstValueFrom } from 'rxjs'; 


@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [CommonModule, RouterLink, NciChartComponent],
  templateUrl: './watchlist.component.html',
  styleUrls: ['./watchlist.component.css']
})
export class WatchlistComponent implements OnInit, OnDestroy {
  private readonly api         = inject(ApiService);
  private readonly router      = inject(Router);
  readonly sse                 = inject(SseService);
  readonly alerts              = inject(AlertService);
  readonly strategySvc         = inject(StrategyService);
  readonly watchlistSvc        = inject(WatchlistService);

  // ── State ─────────────────────────────────────────────────────────────────
  selectedCompany  = signal<Company | null>(null);
  news             = signal<News[]>([]);
  priceData        = signal<PriceData | null>(null);
  loadingPrice     = signal(false);
  flashSet         = signal<Set<string>>(new Set());
  removeConfirmId  = signal<number | null>(null);
  riskExplanation  = signal<RiskExplanation | null>(null);
  loadingRiskExplanation = signal(false);

  private sub!: Subscription;

  // ── Computed ───────────────────────────────────────────────────────────────
  readonly companyAlerts = computed(() => {
    const c = this.selectedCompany();
    return c ? this.alerts.alertsForCompany(c.ticker)
                    .sort((a, b) => a.isRead === b.isRead ? 0 : a.isRead ? 1 : -1)
             : [];
  });

  readonly personalizedNci = computed(() => {
    const c = this.selectedCompany();
    return c ? (this.strategySvc.getStrategyForCompany(c.id)?.nciPersonalized ?? null) : null;
  });

  readonly hasStrategy = computed(() => {
    const c = this.selectedCompany();
    return c ? this.strategySvc.hasStrategy(c.id) : false;
  });

  ngOnInit(): void {
    // Charger la watchlist si pas encore chargée
    if (this.watchlistSvc.entries().length === 0) {
      this.watchlistSvc.load();
    }
    // Sélectionner la première entreprise
    setTimeout(() => {
      const companies = this.watchlistSvc.pinnedCompanies();
      if (companies.length) this.selectCompany(companies[0]);
    }, 100);

    this.listenSse();
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  // ── Selection ──────────────────────────────────────────────────────────────
  
  async selectCompany(company: Company): Promise<void> {
    this.selectedCompany.set(company);
    this.priceData.set(null);
    this.riskExplanation.set(null);
    this.loadNews(company.id);
    this.loadPrice(company.id);
    this.loadRiskExplanation(company.ticker);

    // Rafraîchir les données de la société depuis l'API (obtient le vrai sentiment)
    try {
      const fresh = await firstValueFrom(this.api.getCompanyById(company.id));
      if (fresh) {
        this.selectedCompany.set(fresh);
        this.watchlistSvc.updateCompany(fresh);
      }
    } catch (err) {
      console.warn('Could not refresh company data', err);
    }
  }

  private loadNews(companyId: number): void {
    this.api.getLatestNews(companyId).subscribe(n => this.news.set(n));
  }

  private loadPrice(companyId: number): void {
    this.loadingPrice.set(true);
    this.api.getCompanyPrice(companyId).subscribe({
      next: p => { this.priceData.set(p); this.loadingPrice.set(false); },
      error: () => this.loadingPrice.set(false)
    });
  }

  private loadRiskExplanation(ticker: string): void {
    this.loadingRiskExplanation.set(true);
    this.api.getRiskExplanation(ticker).subscribe({
      next: explanation => {
        this.riskExplanation.set(explanation);
        this.loadingRiskExplanation.set(false);
      },
      error: () => {
        this.riskExplanation.set(null);
        this.loadingRiskExplanation.set(false);
      }
    });
  }

  // ── SSE ────────────────────────────────────────────────────────────────────
  private listenSse(): void {
    this.sub = this.sse.update$.subscribe((evt: NciUpdateEvent) => {
      // Mettre à jour la watchlist
      this.watchlistSvc.entries.update(list =>
        list.map(e => e.company.ticker === evt.ticker
          ? { ...e, company: {
                ...e.company, nciGlobal: evt.nciValue,
                sentimentAvg: evt.sentimentAvg,
                nciLabel: this.nciLabel(evt.nciValue)
              }
            }
          : e)
      );
      // Mettre à jour la sélection
      if (this.selectedCompany()?.ticker === evt.ticker) {
        this.selectedCompany.update(c => c
          ? { ...c, nciGlobal: evt.nciValue, sentimentAvg: evt.sentimentAvg }
          : c);
      }
      // Flash
      this.flashSet.update(s => new Set([...s, evt.ticker]));
      setTimeout(() => this.flashSet.update(s => {
        const n = new Set(s); n.delete(evt.ticker); return n;
      }), 700);
    });
  }

  // ── Watchlist actions ──────────────────────────────────────────────────────

  askRemove(companyId: number, event: Event): void {
    event.stopPropagation();
    this.removeConfirmId.set(companyId);
  }
  cancelRemove(): void { this.removeConfirmId.set(null); }

  /**
   * Dépingle de la Watchlist UNIQUEMENT.
   * NE touche PAS aux stratégies (indépendance totale).
   */
  confirmRemove(companyId: number): void {
    this.watchlistSvc.unpin(companyId).subscribe(() => {
      // Si c'était la sélection → sélectionner la suivante
      if (this.selectedCompany()?.id === companyId) {
        const remaining = this.watchlistSvc.pinnedCompanies().filter(c => c.id !== companyId);
        this.selectedCompany.set(remaining.length ? remaining[0] : null);
      }
    });
    this.removeConfirmId.set(null);
  }

  // ── Navigation ─────────────────────────────────────────────────────────────
  openCompanyPage(id: number): void { this.router.navigate(['/company', id]); }
  analyzeWithAgent(id: number): void {
    this.router.navigate(['/ai-assistant'], { queryParams: { companyId: id } });
  }
  markRead(alertId: number): void { this.alerts.markRead(alertId); }
  markAllRead(): void { this.alerts.markAllRead(0); }

  // ── Helpers ────────────────────────────────────────────────────────────────
  isFlashing(ticker: string): boolean    { return this.flashSet().has(ticker); }
  isPinned(companyId: number): boolean   { return this.watchlistSvc.isPinned(companyId); }
  trendIcon(nciLabel: string): string    { return nciLabel==='HIGH'?'↑':nciLabel==='LOW'?'↓':'→'; }
  trendClass(nciLabel: string): string   { return nciLabel==='HIGH'?'trend-up':nciLabel==='LOW'?'trend-down':'trend-flat'; }
  nciColor(nci: number): string          { return nci>=0.7?'var(--green)':nci>=0.4?'var(--orange)':'var(--red)'; }
  sentimentClass(s: number): string      { return s > 0.05 ? 'chip-green' : s < -0.05 ? 'chip-red' : 'chip-amber'; }
  sentimentLabel(s: number): string      { return s > 0.05 ? 'POSITIVE' : s < -0.05 ? 'NEGATIVE' : 'NEUTRAL'; }
  formatRiskLevel(r: string): string     { return r ? r.split('_').join(' ') : ''; }
  formatSentiment(s: number | null | undefined): string {
    if (!this.hasSentiment(s)) return '—';
    return Number(s).toFixed(2);
  }
  hasSentiment(s: number | null | undefined): boolean {
    return s != null && !Number.isNaN(s);
  }
  sentimentColor(s: number | null | undefined): string {
    if (!this.hasSentiment(s)) return 'var(--text-muted)';
    if (s! > 0.05) return 'var(--green)';
    if (s! < -0.05) return 'var(--red)';
    return 'var(--orange)';
  }
  getRiskBadgeColor(riskLevel: string): string {
    const level = riskLevel?.toUpperCase();
    if (level === 'HIGH') return 'var(--red)';
    if (level === 'MEDIUM') return 'var(--orange)';
    return 'var(--green)';
  }
  alertIcon(type: string): string        {
    const m: Record<string,string> = {NCI_DROP:'↓',NCI_RISE:'↑',SENTIMENT_NEGATIVE:'📉',SENTIMENT_POSITIVE:'📈',STRATEGY_RISK:'⚡',COMMUNICATION_CRISIS:'🚨'};
    return m[type]??'◈';
  }
  alertChipClass(type: string): string   {
    const d=['NCI_DROP','SENTIMENT_NEGATIVE','COMMUNICATION_CRISIS'];
    const g=['NCI_RISE','SENTIMENT_POSITIVE'];
    return d.includes(type)?'chip chip-red':g.includes(type)?'chip chip-green':'chip chip-amber';
  }
  formatAlertType(type: string): string  { return type ? type.split('_').join(' ') : ''; }
  private nciLabel(nci: number): 'HIGH'|'MEDIUM'|'LOW' { return nci>=0.7?'HIGH':nci>=0.4?'MEDIUM':'LOW'; }
}
