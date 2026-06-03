import {
  Component, Input, OnChanges, OnDestroy, SimpleChanges,
  ViewChild, ElementRef, inject, signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from 'src/app/services/api.service';
import { NciChartService, AggregatedPoint, ChartPeriod } from 'src/app/services/nci-chart.service';
import { Chart, registerables } from 'chart.js';
import { NciHistory } from 'src/app/models';

Chart.register(...registerables);

@Component({
  selector: 'app-nci-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './nci-chart.component.html',
  styleUrls: ['./nci-chart.component.css']
})
export class NciChartComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) companyId!: number;
  @ViewChild('chartCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly api      = inject(ApiService);
  private readonly chartSvc = inject(NciChartService);

  // ── State ─────────────────────────────────────────────────────────────────
  loading       = signal(true);
  activePeriod  = signal<ChartPeriod>(1);
  points        = signal<AggregatedPoint[]>([]);
  selectedPoint = signal<AggregatedPoint | null>(null);

  private chart: Chart<'line'> | null = null;
  private rawHistory: NciHistory[] = [];

  // ── Stats computed ─────────────────────────────────────────────────────────
  minValue = () => this.points().length ? Math.min(...this.points().map(p => p.value)) : 0;
  maxValue = () => this.points().length ? Math.max(...this.points().map(p => p.value)) : 0;
  avgValue = () => {
    const pts = this.points();
    return pts.length ? pts.reduce((s, p) => s + p.value, 0) / pts.length : 0;
  };
  trendClass = () => {
    const pts = this.points();
    if (pts.length < 2) return 'trend-flat';
    const delta = pts[pts.length - 1].value - pts[0].value;
    return delta > 0.02 ? 'trend-up' : delta < -0.02 ? 'trend-down' : 'trend-flat';
  };
  trendLabel = () => {
    const pts = this.points();
    if (pts.length < 2) return '→ Stable';
    const delta = pts[pts.length - 1].value - pts[0].value;
    if (delta > 0.02)  return `↑ +${(delta * 100).toFixed(1)}`;
    if (delta < -0.02) return `↓ ${(delta * 100).toFixed(1)}`;
    return '→ Stable';
  };

  readonly periods: { label: string; value: ChartPeriod }[] = [
    { label: '1M', value: 1 },
    { label: '3M', value: 3 },
    { label: '6M', value: 6 },
  ];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['companyId'] && this.companyId) {
      this.loadHistory();
    }
  }

  ngOnDestroy(): void { this.destroyChart(); }

  setPeriod(period: ChartPeriod): void {
    this.activePeriod.set(period);
    this.selectedPoint.set(null);
    this.applyPeriod();
  }

  // ── Private ────────────────────────────────────────────────────────────────

  private loadHistory(): void {
    this.loading.set(true);
    this.destroyChart();

    // Charger toujours 6 mois de données — on filtre côté client
    this.api.getNciHistorySince(this.companyId, 6).subscribe({
      next: history => {
        this.rawHistory = history;
        this.loading.set(false);
        this.applyPeriod();
      },
      error: () => {
        this.rawHistory = [];
        this.loading.set(false);
        this.points.set([]);
      }
    });
  }

  private applyPeriod(): void {
    const period = this.activePeriod();
    const pts    = this.chartSvc.aggregate(this.rawHistory, period);
    this.points.set(pts);

    // Attendre le prochain cycle Angular pour que le canvas soit disponible
    setTimeout(() => this.buildChart(pts), 0);
  }

  private buildChart(pts: AggregatedPoint[]): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas || !pts.length) return;

    this.destroyChart();

    const data    = this.chartSvc.buildChartData(pts);
    const options = this.chartSvc.buildChartOptions(idx => {
      this.selectedPoint.set(pts[idx] ?? null);
    });

    this.chart = new Chart(canvas, { type: 'line', data, options });
  }

  private destroyChart(): void {
    if (this.chart) { this.chart.destroy(); this.chart = null; }
  }

  nciColor(nci: number): string {
    return nci >= 0.7 ? 'var(--green)' : nci >= 0.4 ? 'var(--orange)' : 'var(--red)';
  }
}

