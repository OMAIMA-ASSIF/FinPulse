import { Component, OnInit, effect, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AlertService } from '../../services/alert.service';
import { AuthService } from '../../services/auth.service';
import { WatchlistService } from '../../services/watchlist.service';
import { Alert, Anomaly, Company, RiskExplanation } from '../../models';
import { ApiService } from 'src/app/services/api.service';

export interface WatchlistFeedRow {
  kind: 'ai' | 'alert';
  company: Company;
  alert?: Alert;
  risk?: RiskExplanation;
}

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.css']
})
export class NotificationsComponent implements OnInit {

  readonly alertSvc = inject(AlertService);
  readonly apiSvc = inject(ApiService);
  readonly auth = inject(AuthService);
  readonly watchlistSvc = inject(WatchlistService);
  private readonly router = inject(Router);

  readonly riskExplanations = signal<Record<string, RiskExplanation>>({});

  activeTab = signal<'all' | 'unread' | 'anomalies'>('all');
  anomalies = signal<Anomaly[]>([]);
  loadingAnomalies = signal(false);
  selectedCompanyForAnomalies = signal<Company | null>(null);

  /** Lignes affichées = même contenu que le panneau Alerts de la watchlist, toutes entreprises */
  readonly feedRows = computed(() => {
    this.watchlistSvc.entries();
    this.alertSvc.alerts();
    this.riskExplanations();
    const tab = this.activeTab();

    const rows: WatchlistFeedRow[] = [];
    for (const entry of this.watchlistSvc.entries()) {
      const company = entry.company;
      const risk = this.lookupRisk(company.ticker);

      if (tab !== 'unread' && risk?.llm_explanation) {
        rows.push({ kind: 'ai', company, risk });
      }

      for (const alert of this.alertSvc.alerts()) {
        if (!this.alertMatchesCompany(alert, company)) continue;
        if (tab === 'unread' && alert.isRead) continue;
        rows.push({ kind: 'alert', company, alert });
      }
    }
    return rows;
  });

  readonly watchlistCompanies = computed(() =>
    this.watchlistSvc.entries().map(e => e.company)
  );

  readonly watchlistAlertCount = computed(() =>
    this.feedRows().filter(r => r.kind === 'alert').length
  );

  readonly watchlistUnreadCount = computed(() =>
    this.feedRows().filter(r => r.kind === 'alert' && !r.alert!.isRead).length
  );

  readonly isPageLoading = computed(() =>
    this.watchlistSvc.loading() || this.alertSvc.loading()
  );

  constructor() {
    effect(() => {
      const companies = this.watchlistCompanies();
      if (companies.length > 0) {
        this.fetchRiskExplanations(companies);
      }
    }, { allowSignalWrites: true });
  }

  ngOnInit(): void {
    this.watchlistSvc.load();
    this.alertSvc.load();
  }

  get userId(): number { return this.auth.profile()?.id ?? 0; }

  markRead(alert: Alert): void { this.alertSvc.markRead(alert.id); }
  markAllRead(): void { this.alertSvc.markAllRead(this.userId); }

  alertIcon(type: string): string {
    const m: Record<string, string> = {
      NCI_DROP: '↓', NCI_RISE: '↑', SENTIMENT_NEGATIVE: '📉',
      SENTIMENT_POSITIVE: '📈', STRATEGY_RISK: '⚡', COMMUNICATION_CRISIS: '🚨'
    };
    return m[type] ?? '◈';
  }

  
  // Pagination anomalies
  currentPage = signal<number>(1);
  itemsPerPage = 3;

  totalPages = computed(() => {
    const len = this.anomalies().length;
    return Math.ceil(len / this.itemsPerPage);
  });

  paginatedAnomalies = computed(() => {
    const start = (this.currentPage() - 1) * this.itemsPerPage;
    return this.anomalies().slice(start, start + this.itemsPerPage);
  });

  // Modifie loadAnomalies pour récupérer toutes les anomalies (top_k = 0 ou valeur élevée)
  loadAnomalies(ticker: string): void {
    if (!ticker?.trim()) {
      this.anomalies.set([]);
      return;
    }
    this.loadingAnomalies.set(true);
    // Utilise top_k = 0 pour récupérer toutes les anomalies (si backend modifié)
    this.apiSvc.getAnomalies(ticker, undefined, 0).subscribe({
      next: response => {
        this.anomalies.set(response?.paragraphs ?? []);
        this.currentPage.set(1);        // reset page
        this.loadingAnomalies.set(false);
      },
      error: () => {
        this.anomalies.set([]);
        this.loadingAnomalies.set(false);
      }
    });
  }

  // Méthode pour changer de page
  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages()) {
      this.currentPage.set(page);
    }
  }

  alertChipClass(type: string): string {
    if (['NCI_DROP', 'SENTIMENT_NEGATIVE', 'COMMUNICATION_CRISIS'].includes(type)) return 'chip chip-red';
    if (['NCI_RISE', 'SENTIMENT_POSITIVE'].includes(type)) return 'chip chip-green';
    return 'chip chip-amber';
  }

  formatAlertType(type: string): string {
    if (!type) return '';
    return type.split('_').join(' ');
  }

  selectCompanyForAnomalies(companyId: string): void {
    if (!companyId) {
      this.selectedCompanyForAnomalies.set(null);
      this.anomalies.set([]);
      return;
    }
    const company = this.watchlistCompanies().find(c => c.id === parseInt(companyId, 10));
    if (company) {
      this.selectedCompanyForAnomalies.set(company);
      this.loadAnomalies(company.ticker);
    }
  }

 

  openCompanyDashboard(alert: Alert): void {
    this.apiSvc.getCompanyId(alert.companyTicker).subscribe({
      next: res => this.router.navigate(['/company', res])
    });
  }

  openStrategy(alert: Alert): void {
    this.router.navigate(['/strategies'], { queryParams: { highlight: alert.strategyId } });
  }

  private lookupRisk(ticker: string): RiskExplanation | null {
    const risks = this.riskExplanations();
    return risks[ticker] ?? risks[ticker?.toUpperCase()] ?? risks[ticker?.toLowerCase()] ?? null;
  }

  private alertMatchesCompany(alert: Alert, company: Company): boolean {
    const alertTicker = (alert.companyTicker ?? '').trim().toUpperCase();
    const companyTicker = (company.ticker ?? '').trim().toUpperCase();
    if (alertTicker && companyTicker && alertTicker === companyTicker) return true;

    const alertName = (alert.companyName ?? '').trim().toUpperCase();
    const companyName = (company.name ?? '').trim().toUpperCase();
    return !!(alertName && companyName && alertName === companyName);
  }

  private fetchRiskExplanations(companies: Company[]): void {
    forkJoin(
      companies.map(c =>
        this.apiSvc.getRiskExplanation(c.ticker).pipe(
          map(ex => ({ ticker: c.ticker, ex })),
          catchError(() => of({ ticker: c.ticker, ex: null as RiskExplanation | null }))
        )
      )
    ).subscribe(results => {
      const map: Record<string, RiskExplanation> = {};
      for (const r of results) {
        if (r.ex?.llm_explanation) {
          map[r.ticker] = r.ex;
          map[r.ticker.toUpperCase()] = r.ex;
        }
      }
      this.riskExplanations.set(map);
    });
  }
}
