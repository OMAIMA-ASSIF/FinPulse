import { Injectable } from '@angular/core';
import { NciHistory } from '../models';
import { ChartData, ChartOptions, TooltipItem } from 'chart.js';

export type ChartPeriod = 1 | 3 | 6;

interface AggregatedPoint {
  label: string;
  value: number;
  rawDate: Date;
  count: number;
  reason?: string;
}

/**
 * Transforme les données brutes NCI en datasets Chart.js
 * avec granularité adaptée à la période sélectionnée.
 *
 * 1M → données quotidiennes (groupées par jour)
 * 3M → données hebdomadaires (groupées par semaine)
 * 6M → données mensuelles (groupées par mois)
 */
@Injectable({ providedIn: 'root' })
export class NciChartService {

  /**
   * Filtre et agrège les données NCI selon la période.
   * @param history  — entrées NCI brutes depuis le backend
   * @param months   — 1, 3 ou 6 mois
   * @returns        — points agrégés prêts pour Chart.js
   */
  aggregate(history: NciHistory[], months: ChartPeriod): AggregatedPoint[] {
    if (!history.length) return [];

    const now   = new Date();
    const cutoff = new Date(now);
    cutoff.setMonth(cutoff.getMonth() - months);

    // Filtrer sur la période
    const filtered = history
      .map(h => ({ ...h, date: new Date(h.recordedAt) }))
      .filter(h => h.date >= cutoff)
      .sort((a, b) => a.date.getTime() - b.date.getTime());

    if (!filtered.length) return [];

    // Grouper selon la granularité
    const getKey = (d: Date): string => {
      if (months === 1) return this.keyDay(d);
      if (months === 3) return this.keyWeek(d);
      return this.keyMonth(d);
    };

    const groups = new Map<string, { values: number[]; date: Date; reasons: string[] }>();

    for (const entry of filtered) {
      const key = getKey(entry.date);
      if (!groups.has(key)) {
        groups.set(key, { values: [], date: entry.date, reasons: [] });
      }
      const g = groups.get(key)!;
      g.values.push(entry.nciValue);
      if (entry.reason) g.reasons.push(entry.reason);
    }

    return Array.from(groups.entries()).map(([key, g]) => {
      const avg   = g.values.reduce((s, v) => s + v, 0) / g.values.length;
      const label = this.formatLabel(g.date, months);
      return {
        label,
        value:   Math.round(avg * 1000) / 1000,
        rawDate: g.date,
        count:   g.values.length,
        reason:  g.reasons[g.reasons.length - 1]
      };
    });
  }

  /**
   * Construit le ChartData Chart.js v4 à partir des points agrégés.
   */
  buildChartData(points: AggregatedPoint[]): ChartData<'line'> {
    const values = points.map(p => +(p.value * 100).toFixed(2));

    return {
      labels: points.map(p => p.label),
      datasets: [{
        label: 'NCI Score',
        data: values,
        borderColor: 'var(--chart-line, #3b82f6)',
        backgroundColor: (ctx: any) => {
          const chart  = ctx.chart;
          const { ctx: c, chartArea } = chart;
          if (!chartArea) return 'transparent';
          const gradient = c.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
          // Couleur adaptée au thème via CSS variable
          const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
          if (isDark) {
            gradient.addColorStop(0, 'rgba(59,130,246,0.18)');
            gradient.addColorStop(1, 'rgba(59,130,246,0.01)');
          } else {
            gradient.addColorStop(0, 'rgba(29,111,235,0.12)');
            gradient.addColorStop(1, 'rgba(29,111,235,0.01)');
          }
          return gradient;
        },
        borderWidth: 2,
        pointRadius: points.length <= 30 ? 4 : 2,
        pointHoverRadius: 7,
        pointBackgroundColor: values.map(v =>
          v >= 70 ? '#22c55e' : v >= 40 ? '#f97316' : '#ef4444'
        ),
        pointBorderColor: 'transparent',
        pointHoverBorderColor: '#ffffff',
        pointHoverBorderWidth: 2,
        fill: true,
        tension: 0.4,
      }]
    };
  }

  /**
   * Options Chart.js v4 — style finance premium.
   */
  buildChartOptions(
    onPointClick: (idx: number) => void
  ): ChartOptions<'line'> {
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 400, easing: 'easeInOutQuart' },
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          enabled: true,
          backgroundColor: 'var(--chart-tooltip-bg, #141d2e)',
          borderColor: 'var(--chart-tooltip-border, rgba(255,255,255,0.09))',
          borderWidth: 1,
          titleColor: '#8899bb',
          bodyColor: '#f0f4ff',
          titleFont: { family: "'JetBrains Mono', monospace", size: 10 },
          bodyFont:  { family: "'Inter', sans-serif", size: 12 },
          padding: 10,
          displayColors: false,
          callbacks: {
            title: (items: TooltipItem<'line'>[]) => items[0]?.label ?? '',
            label: (item: TooltipItem<'line'>) =>
              `NCI: ${(item.parsed.y)?.toFixed(1)} / 100`,
            afterLabel: (item: TooltipItem<'line'>) => {
              const v = item.parsed.y;
              if (typeof v !== 'number') return '';
              if (v >= 70) return '▲ Consistent narrative';
              if (v >= 40) return '● Moderate consistency';
              return '▼ Narrative risk';
            }
          }
        }
      },
      scales: {
        x: {
          title: {
            display: true,
            text: 'Time',
            color: 'var(--chart-tick, #3d5070)',
            font: { family: "'JetBrains Mono', monospace", size: 10, weight: 700 }
          },
          grid:  { color: 'var(--chart-grid, rgba(255,255,255,0.04))', drawTicks: false },
          border: { dash: [4, 4] },
          ticks: {
            color: 'var(--chart-tick, #3d5070)',
            font:  { family: "'JetBrains Mono', monospace", size: 10 },
            maxRotation: 0,
            maxTicksLimit: 8,
            padding: 6,
          }
        },
        y: {
          title: {
            display: true,
            text: 'NCI Value',
            color: 'var(--chart-tick, #3d5070)',
            font: { family: "'JetBrains Mono', monospace", size: 10, weight: 700 }
          },
          min: 0,
          max: 100,
          grid:  { color: 'var(--chart-grid, rgba(255,255,255,0.04))', drawTicks: false },
          border: { dash: [4, 4] },
          ticks: {
            color: 'var(--chart-tick, #3d5070)',
            font:  { family: "'JetBrains Mono', monospace", size: 10 },
            padding: 6,
            count: 6,
            callback: (v: number | string) => `${v}`,
          }
        }
      },
      onClick: (_evt, elements) => {
        if (elements.length > 0) onPointClick(elements[0].index);
      }
    };
  }

  // ── Key generators ────────────────────────────────────────────────────────

  private keyDay(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  }

  private keyWeek(d: Date): string {
    // ISO week: lundi de la semaine
    const tmp = new Date(d);
    tmp.setHours(0, 0, 0, 0);
    tmp.setDate(tmp.getDate() - ((tmp.getDay() + 6) % 7)); // lundi
    return `${tmp.getFullYear()}-W${String(Math.ceil(tmp.getDate() / 7)).padStart(2,'0')}-${tmp.getMonth()}`;
  }

  private keyMonth(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`;
  }

  // ── Label formatters ──────────────────────────────────────────────────────

  private formatLabel(d: Date, months: ChartPeriod): string {
    if (months === 1) {
      // "Jan 5"
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    }
    if (months === 3) {
      // "W12 Mar"
      const weekNum = this.isoWeek(d);
      return `W${weekNum} ${d.toLocaleDateString('en-US', { month: 'short' })}`;
    }
    // "Jan '25"
    return d.toLocaleDateString('en-US', { month: 'short', year: '2-digit' });
  }

  private isoWeek(d: Date): number {
    const tmp  = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const day  = tmp.getUTCDay() || 7;
    tmp.setUTCDate(tmp.getUTCDate() + 4 - day);
    const year = new Date(Date.UTC(tmp.getUTCFullYear(), 0, 1));
    return Math.ceil((((tmp.getTime() - year.getTime()) / 86400000) + 1) / 7);
  }
}

export type { AggregatedPoint };
