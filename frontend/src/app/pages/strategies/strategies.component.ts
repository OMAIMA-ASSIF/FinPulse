import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { StrategyService } from '../../services/strategy.service';
import { AuthService } from '../../services/auth.service';
import { AlertService } from '../../services/alert.service';
import { Strategy } from '../../models';
import { StrategyReport } from '../../services/api.service';

type Tab = 'active' | 'archived';

@Component({
  selector: 'app-strategies',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './strategies.component.html',
  styleUrls: ['./strategies.component.css']
})
export class StrategiesComponent implements OnInit {
  readonly strategySvc = inject(StrategyService);
  readonly auth        = inject(AuthService);
  readonly alertSvc    = inject(AlertService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute)
  highlightedId = signal<number | null>(null);

  activeTab       = signal<Tab>('active');
  reportModal     = signal<StrategyReport | null>(null);
  reportLoading   = signal(false);
  reportError     = signal('');
  downloadingId   = signal<number | null>(null);
  confirmDeleteId = signal<number | null>(null);

  ngOnInit() {
    // On s'abonne aux paramètres de l'URL
    this.route.queryParams.subscribe(params => {
      const id = params['highlight'];
      if (id) {
        this.highlightedId.set(Number(id));
        
       //Effacer le highlight après 5 secondes pour l'effet "flash"
        setTimeout(() => this.highlightedId.set(null), 5000);
      }
    });
  }
  displayed() {
    return this.activeTab() === 'active'
      ? this.strategySvc.activeStrategies()
      : this.strategySvc.archivedStrategies();
  }

  openCompany(id: number): void   { this.router.navigate(['/company', id]); }
  openWatchlist(): void           { this.router.navigate(['/watchlist']); }
  openAgent(companyId: number): void {
    this.router.navigate(['/ai-assistant'], { queryParams: { companyId } });
  }

  deactivate(s: Strategy): void {
    this.strategySvc.deactivate(s.id);
  }

  reactivate(s: Strategy): void {
    this.strategySvc.reactivate(s.id);
    this.activeTab.set('active'); // switch to active tab
  }

  askDelete(s: Strategy): void   { this.confirmDeleteId.set(s.id); }
  cancelDelete(): void            { this.confirmDeleteId.set(null); }
  confirmDelete(s: Strategy): void {
    this.strategySvc.delete(s.id);
    this.confirmDeleteId.set(null);
  }

  viewReport(s: Strategy): void {
    this.reportLoading.set(true);
    this.reportError.set('');
    this.strategySvc.getReport(s.id).subscribe({
      next: report => { this.reportModal.set(report); this.reportLoading.set(false); },
      error: err => {
        this.reportError.set(err?.error?.message ?? 'Could not load report.');
        this.reportLoading.set(false);
        // Afficher un rapport mock si backend pas encore prêt
        this.reportModal.set(this.mockReport(s));
      }
    });
  }

  closeReport(): void { this.reportModal.set(null); this.reportError.set(''); }

  downloadPdf(s: Strategy): void {
    this.downloadingId.set(s.id);
    this.strategySvc.downloadPdf(s.id);
    setTimeout(() => this.downloadingId.set(null), 2000);
  }

  nciColor(nci: number): string {
    return nci >= 0.7 ? 'var(--green)' : nci >= 0.4 ? 'var(--orange)' : 'var(--red)';
  }

  statusLabel(s: Strategy): string {
    if (!s.isActive) return 'ARCHIVED';
    const unread = this.alertSvc.alertsForCompany(s.company.ticker).filter(a => !a.isRead).length;
    return unread > 0 ? 'ALERT' : 'SAFE';
  }

  statusClass(s: Strategy): string {
    const lbl = this.statusLabel(s);
    if (lbl === 'ALERT')    return 'chip chip-red';
    if (lbl === 'ARCHIVED') return 'chip chip-amber';
    return 'chip chip-green';
  }

  unreadAlerts(ticker: string): number {
    return this.alertSvc.alertsForCompany(ticker).filter(a => !a.isRead).length;
  }

  recClass(r: string): string {
    if (r === 'BUY')   return 'chip chip-green';
    if (r === 'AVOID') return 'chip chip-red';
    return 'chip chip-amber';
  }

  private mockReport(s: Strategy): StrategyReport {
    return {
      id: s.id,
      ticker: s.company.ticker,
      companyName: s.company.name,
      thesis: s.userArgument ?? 'No thesis provided.',
      bullCase: ['Strong market position', 'Consistent earnings growth', 'High NCI score'],
      risks: ['Regulatory exposure', 'Market competition', 'Narrative inconsistency detected'],
      secContradictions: ['Forward guidance vs. historical performance', 'Risk factor evolution'],
      historicalInsight: `Based on NCI history, ${s.company.ticker} has maintained a score of ~${Math.round(s.company.nciGlobal * 100)} over the past 6 months.`,
      recommendation: s.nciPersonalized >= 0.65 ? 'BUY' : s.nciPersonalized >= 0.45 ? 'HOLD' : 'AVOID',
      nciPersonalized: s.nciPersonalized,
      generatedAt: new Date().toISOString()
    };
  }
}

// Re-export type for template
export type { StrategyReport };
