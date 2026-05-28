import { Component, Input, Output, EventEmitter, OnChanges, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, registerables } from 'chart.js';
import { NciHistory } from '../../../../models';

Chart.register(...registerables);

@Component({
  selector: 'app-nci-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './nci-chart.component.html',
  styleUrls: ['./nci-chart.component.css']
})
export class NciChartComponent implements OnChanges, AfterViewInit {
  @Input() history: NciHistory[] = [];
  @Input() loading = false;
  @Output() periodChanged = new EventEmitter<number>();

  @ViewChild('chartCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;

  activePeriod = 6;
  private chart: Chart | null = null;
  private ready = false;

  readonly periods = [
    { label: '1M', months: 1 },
    { label: '3M', months: 3 },
    { label: '6M', months: 6 },
    { label: '1Y', months: 12 }
  ];

  ngAfterViewInit(): void {
    this.ready = true;
    if (this.history.length) this.renderChart();
  }

  ngOnChanges(): void {
    if (this.ready && this.history.length) this.renderChart();
  }

  setPeriod(months: number): void {
    this.activePeriod = months;
    this.periodChanged.emit(months);
  }

  private renderChart(): void {
    if (!this.canvasRef) return;
    this.chart?.destroy();

    const labels = this.history.map(h =>
      new Date(h.recordedAt).toLocaleDateString('en', { month: 'short', day: 'numeric' })
    );
    const data = this.history.map(h => +(h.nciValue * 100).toFixed(2));

    this.chart = new Chart(this.canvasRef.nativeElement, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'NCI Score',
          data,
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59,130,246,0.06)',
          borderWidth: 2,
          pointRadius: 2,
          pointHoverRadius: 5,
          pointBackgroundColor: '#3b82f6',
          fill: true,
          tension: 0.4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#1a2540',
            borderColor: 'rgba(59,130,246,0.3)',
            borderWidth: 1,
            titleColor: '#3b82f6',
            bodyColor: '#f0f4ff',
            padding: 10,
            callbacks: { label: ctx => ` NCI: ${ctx.parsed.y?.toFixed(2) ?? '0.00'}` }
          }
        },
        scales: {
          x: {
            grid: { color: 'rgba(255,255,255,0.04)' },
            ticks: { color: '#3d5070', font: { family: 'JetBrains Mono', size: 10 }, maxTicksLimit: 8 }
          },
          y: {
            min: 0, max: 100,
            grid: { color: 'rgba(255,255,255,0.04)' },
            ticks: {
              color: '#3d5070',
              font: { family: 'JetBrains Mono', size: 10 },
              callback: v => v + '%'
            }
          }
        }
      }
    });
  }
}
