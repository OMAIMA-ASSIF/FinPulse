import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { SseService } from '../../services/sse.service';
import { AlertService } from '../../services/alert.service';
import { StrategyService } from '../../services/strategy.service';
import { Company, NciHistory, News } from '../../models';
import { WatchlistSidebarComponent } from './components/watchlist-sidebar/watchlist-sidebar.component';
import { CompanyHeaderComponent } from './components/company-header/company-header.component';
import { MetricsPanelComponent } from './components/metrics-panel/metrics-panel.component';
import { NciChartComponent } from './components/nci-chart/nci-chart.component';
import { AlertCenterComponent } from './components/alert-center/alert-center.component';
import { BreakingNewsComponent } from './components/breaking-news/breaking-news.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    WatchlistSidebarComponent,
    CompanyHeaderComponent,
    MetricsPanelComponent,
    NciChartComponent,
    AlertCenterComponent,
    BreakingNewsComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly api       = inject(ApiService);
  private readonly sse       = inject(SseService);
  readonly alerts            = inject(AlertService);
  readonly strategies        = inject(StrategyService);

  // ── State (Signals) ─────────────────────────────────────────────
  companies        = signal<Company[]>([]);
  selectedCompany  = signal<Company | null>(null);
  nciHistory       = signal<NciHistory[]>([]);
  news             = signal<News[]>([]);
  loadingCompanies = signal(true);
  loadingDetail    = signal(false);

  // NCI personnalisé si stratégie existe
  personalizedNci = computed(() => {
    const c = this.selectedCompany();
    if (!c) return null;
    return this.strategies.getStrategyForCompany(c.id)?.nciPersonalized ?? null;
  });

  // Alertes filtrées pour l'entreprise sélectionnée
  companyAlerts = computed(() => {
    const c = this.selectedCompany();
    return c ? this.alerts.alertsForCompany(c.ticker) : [];
  });

  private sub!: Subscription;
  private historyPeriod = 6; // months

  // ── Lifecycle ───────────────────────────────────────────────────
  ngOnInit(): void {
    this.loadCompanies();
    this.listenToSse();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  // ── Data loading ────────────────────────────────────────────────
  private loadCompanies(): void {
    this.loadingCompanies.set(true);
    this.api.getLeaderboard(20).subscribe({
      next: list => {
        this.companies.set(list);
        this.loadingCompanies.set(false);
        if (list.length) this.selectCompany(list[0]);
      },
      error: () => this.loadingCompanies.set(false)
    });
  }

  selectCompany(company: Company): void {
    this.selectedCompany.set(company);
    this.loadingDetail.set(true);

    // Load NCI history
    this.api.getNciHistorySince(company.id, this.historyPeriod).subscribe(h => {
      this.nciHistory.set([...h].reverse());
      this.loadingDetail.set(false);
    });

    // Load news
    this.api.getLatestNews(company.id).subscribe(n => this.news.set(n));
  }

  setHistoryPeriod(months: number): void {
    this.historyPeriod = months;
    const c = this.selectedCompany();
    if (c) {
      this.api.getNciHistorySince(c.id, months).subscribe(h =>
        this.nciHistory.set([...h].reverse())
      );
    }
  }

  // ── SSE real-time updates ────────────────────────────────────────
  private listenToSse(): void {
    this.sub = this.sse.update$.subscribe(evt => {
      // Update companies list
      this.companies.update(list =>
        list.map(c => c.ticker === evt.ticker
          ? { ...c, nciGlobal: evt.nciValue, sentimentAvg: evt.sentimentAvg,
              nciLabel: this.nciLabel(evt.nciValue), lastUpdate: evt.timestamp }
          : c)
      );
      // Update selected company if it matches
      const sel = this.selectedCompany();
      if (sel?.ticker === evt.ticker) {
        this.selectedCompany.update(c => c ? {
          ...c, nciGlobal: evt.nciValue, sentimentAvg: evt.sentimentAvg, lastUpdate: evt.timestamp
        } : c);
      }
    });
  }

  private nciLabel(nci: number): 'HIGH' | 'MEDIUM' | 'LOW' {
    return nci >= 0.7 ? 'HIGH' : nci >= 0.4 ? 'MEDIUM' : 'LOW';
  }
}
