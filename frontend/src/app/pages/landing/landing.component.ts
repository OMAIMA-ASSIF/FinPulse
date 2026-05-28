import {
  Component, AfterViewInit, OnDestroy,
  ViewChild, ElementRef, signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.css']
})
export class LandingComponent implements AfterViewInit, OnDestroy {
  @ViewChild('heroChart') heroChartRef!: ElementRef<HTMLCanvasElement>;

  private chart: Chart | null = null;

  mobileMenuOpen = signal(false);

  // ── Mock companies for Discover Preview ────────────────────────────────────
  readonly mockCompanies = [
    { ticker: 'AAPL', name: 'Apple Inc.', sector: 'Technology',   nci: 82, sentiment: 'POSITIVE', trend: '+2.3%', nciClass: 'nci-high' },
    { ticker: 'TSLA', name: 'Tesla Inc.', sector: 'Automotive',   nci: 43, sentiment: 'NEUTRAL',  trend: '-1.8%', nciClass: 'nci-medium' },
    { ticker: 'NVDA', name: 'NVIDIA Corp', sector: 'Semiconductors', nci: 91, sentiment: 'POSITIVE', trend: '+5.1%', nciClass: 'nci-high' },
    { ticker: 'META', name: 'Meta Platforms', sector: 'Social Media', nci: 38, sentiment: 'NEGATIVE', trend: '-0.9%', nciClass: 'nci-low' },
    { ticker: 'MSFT', name: 'Microsoft Corp', sector: 'Technology', nci: 78, sentiment: 'POSITIVE', trend: '+1.2%', nciClass: 'nci-high' },
    { ticker: 'AMZN', name: 'Amazon.com', sector: 'E-Commerce',   nci: 67, sentiment: 'POSITIVE', trend: '+0.8%', nciClass: 'nci-medium' },
  ];

  // ── Mock NCI chart data (hero) ─────────────────────────────────────────────
  private readonly heroData = {
    labels: ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'],
    datasets: [
      {
        label: 'AAPL — NCI',
        data: [72, 75, 78, 74, 80, 82, 79, 83, 81, 84, 82, 86],
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59,130,246,0.08)',
        borderWidth: 2, pointRadius: 3, tension: 0.4, fill: true
      },
      {
        label: 'TSLA — NCI',
        data: [55, 48, 52, 43, 40, 45, 41, 38, 43, 40, 42, 38],
        borderColor: '#f97316',
        backgroundColor: 'rgba(249,115,22,0.06)',
        borderWidth: 2, pointRadius: 3, tension: 0.4, fill: true
      }
    ]
  };

  readonly features = [
    { icon: '📊', title: 'Live NCI Dashboard',     desc: 'Real-time narrative consistency scoring for hundreds of companies.' },
    { icon: '🤖', title: 'AI Agent',               desc: 'Ask questions and get instant NCI analysis, risks and SEC insights.' },
    { icon: '⚡', title: 'Strategy Detection',     desc: 'AI auto-detects investment theses and generates strategy reports.' },
    { icon: '🔔', title: 'Smart Alerts',           desc: 'Get notified when NCI drops, sentiment shifts, or risks emerge.' },
    { icon: '📌', title: 'Watchlist',              desc: 'Pin companies and track narrative evolution over time.' },
    { icon: '🔍', title: 'Discover & Compare',     desc: 'Filter by sector, sort by NCI, and compare companies side by side.' },
    { icon: '📈', title: 'NCI History Charts',     desc: 'Premium financial charts with 1M/3M/6M historical NCI trends.' },
    { icon: '🛡', title: 'SEC Risk Detection',     desc: 'Detect narrative contradictions against SEC filings automatically.' },
  ];

  readonly solutions = [
    {
      icon: '◈', color: 'var(--brand)', title: 'What is the NCI?',
      desc: 'The Narrative Consistency Index (NCI) measures how coherent a company\'s communication is over time — across earnings calls, press releases, 10-K filings and news. A high NCI means stable, trustworthy messaging. A low NCI signals potential investor risk.'
    },
    {
      icon: '📄', color: 'var(--cyan)', title: 'SEC Data Sources',
      desc: 'FinPulse AI analyses 10-K, 10-Q, 8-K filings and earnings calls. Our NLP pipeline detects when forward guidance contradicts historical statements, flagging discrepancies that human analysts often miss.'
    },
    {
      icon: '⚠', color: 'var(--red)', title: 'Risk Detection',
      desc: 'Sudden NCI drops, evasive language patterns and sentiment shifts trigger automatic alerts. Know before the market reacts — FinPulse AI spots narrative deterioration weeks in advance.'
    },
  ];

  constructor(private readonly router: Router, private readonly kc: KeycloakService) {}

  ngAfterViewInit(): void {
    setTimeout(() => this.buildHeroChart(), 100);
  }

  ngOnDestroy(): void { this.chart?.destroy(); }

  private buildHeroChart(): void {
    const canvas = this.heroChartRef?.nativeElement;
    if (!canvas) return;

    this.chart = new Chart(canvas, {
      type: 'line',
      data: this.heroData,
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 1200, easing: 'easeInOutQuart' },
        plugins: {
          legend: {
            display: true,
            labels: {
              color: '#8899bb', font: { family: "'JetBrains Mono',monospace", size: 10 },
              boxWidth: 12, padding: 14
            }
          },
          tooltip: {
            backgroundColor: '#141d2e',
            borderColor: 'rgba(255,255,255,0.09)',
            borderWidth: 1,
            titleColor: '#8899bb',
            bodyColor: '#f0f4ff',
            titleFont: { family: "'JetBrains Mono',monospace", size: 10 },
            callbacks: { label: ctx => ` NCI: ${ctx.parsed.y}` }
          }
        },
        scales: {
          x: {
            grid:  { color: 'rgba(255,255,255,0.04)' },
            ticks: { color: '#3d5070', font: { size: 10, family: "'JetBrains Mono',monospace" } }
          },
          y: {
            min: 20, max: 100,
            grid:  { color: 'rgba(255,255,255,0.04)' },
            ticks: { color: '#3d5070', font: { size: 10, family: "'JetBrains Mono',monospace" }, count: 5 }
          }
        }
      }
    });
  }

  login():    void { this.kc.login({ redirectUri: `${window.location.origin}/discover` }); }
  register(): void { this.router.navigate(['/register']); }

  scrollTo(id: string): void {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
    this.mobileMenuOpen.set(false);
  }
}
