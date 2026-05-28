import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { StrategyService } from '../../services/strategy.service';
import { AlertService } from '../../services/alert.service';
import { SseService } from '../../services/sse.service';
import { Company, NciHistory, News } from '../../models';

@Component({
  selector: 'app-company-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './company-detail.component.html',
  styleUrls: ['./company-detail.component.css']
})
export class CompanyDetailComponent implements OnInit, OnDestroy {
  private readonly api         = inject(ApiService);
  private readonly route       = inject(ActivatedRoute);
  private readonly router      = inject(Router);
  readonly strategySvc         = inject(StrategyService);
  readonly alertSvc            = inject(AlertService);
  readonly sse                 = inject(SseService);

  company     = signal<Company | null>(null);
  history     = signal<NciHistory[]>([]);
  news        = signal<News[]>([]);
  trend       = signal('STABLE');
  loading     = signal(true);
  period      = signal(6);
  pinning     = signal(false);

  private sub!: Subscription;

  readonly periods = [
    { label: '1M', months: 1 }, { label: '3M', months: 3 },
    { label: '6M', months: 6 }, { label: '1Y', months: 12 }
  ];

  ngOnInit(): void {
    this.route.params.pipe(
      switchMap(p => this.api.getCompanyById(+p['id']))
    ).subscribe(c => {
      this.company.set(c);
      this.loading.set(false);
      this.loadHistory();
      this.loadNews();
      this.loadTrend();
    });

    // Listen SSE updates for this company
    this.sub = this.sse.update$.subscribe(evt => {
      if (evt.ticker === this.company()?.ticker) {
        this.company.update(c => c
          ? { ...c, nciGlobal: evt.nciValue, sentimentAvg: evt.sentimentAvg }
          : c);
      }
    });
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  setPeriod(months: number): void {
    this.period.set(months);
    this.loadHistory();
  }

  async pinToWatchlist(): Promise<void> {
    const c = this.company();
    if (!c || this.strategySvc.hasStrategy(c.id)) return;
    this.pinning.set(true);
    try {
      await this.strategySvc.create({ companyId: c.id, userArgument: `Watchlist: ${c.ticker}` });
    } finally { this.pinning.set(false); }
  }

  analyzeWithAgent(): void {
    const c = this.company();
    if (c) this.router.navigate(['/ai-assistant'], { queryParams: { companyId: c.id } });
  }

  goBack(): void { this.router.navigate(['/discover']); }

  isPinned(): boolean { return this.strategySvc.hasStrategy(this.company()?.id ?? 0); }

  private loadHistory(): void {
    const id = this.company()?.id;
    if (!id) return;
    this.api.getNciHistorySince(id, this.period()).subscribe(h =>
      this.history.set([...h].reverse())
    );
  }

  private loadNews(): void {
    const id = this.company()?.id;
    if (!id) return;
    this.api.getLatestNews(id).subscribe(n => this.news.set(n));
  }

  private loadTrend(): void {
    const id = this.company()?.id;
    if (!id) return;
    this.api.getNciTrend(id).subscribe(r => this.trend.set(r.trend));
  }

  nciColor(n: number): string { return n >= 0.7 ? 'var(--green)' : n >= 0.4 ? 'var(--orange)' : 'var(--red)'; }

  trendIcon(t: string): string { return t === 'IMPROVING' ? '↑' : t === 'DECLINING' ? '↓' : '→'; }
  trendClass(t: string): string { return t === 'IMPROVING' ? 'trend-up' : t === 'DECLINING' ? 'trend-down' : 'trend-flat'; }

  sentimentLabel(s: number): string { return s >= 0.6 ? 'POSITIVE' : s <= 0.35 ? 'NEGATIVE' : 'NEUTRAL'; }
  sentimentClass(s: number): string { return s >= 0.6 ? 'chip chip-green' : s <= 0.35 ? 'chip chip-red' : 'chip chip-amber'; }

  newsClass(s: number): string { return s >= 0.6 ? 'chip-green' : s <= 0.35 ? 'chip-red' : 'chip-amber'; }
  newsLabel(s: number): string { return s >= 0.6 ? 'POS' : s <= 0.35 ? 'NEG' : 'NEU'; }
}
