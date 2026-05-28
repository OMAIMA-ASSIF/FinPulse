import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AlertService } from '../../services/alert.service';
import { StrategyService } from '../../services/strategy.service';
import { AuthService } from '../../services/auth.service';
import { Alert } from '../../models';
import { ApiService } from 'src/app/services/api.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.css']
})
export class NotificationsComponent {
  readonly alertSvc    = inject(AlertService);
  readonly strategySvc = inject(StrategyService);
  readonly apiSvc  = inject(ApiService);
  readonly auth        = inject(AuthService);
  private readonly router = inject(Router);

  activeTab = signal<'all' | 'unread'>('all');

  readonly displayed = computed(() => {
    const all = this.activeTab() === 'unread'
      ? this.alertSvc.alerts().filter(a =>!a.isRead)
      : this.alertSvc.alerts();

    // Unread en haut, read en bas
    return [...all].sort((a, b) => {
      if (a.isRead === b.isRead) return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      return a.isRead ? 1 : -1;
    });
  });

  get userId(): number { return this.auth.profile()?.id ?? 0; }

  markRead(alert: Alert): void { this.alertSvc.markRead(alert.id); }
  markAllRead(): void { this.alertSvc.markAllRead(this.userId); }

  // ── Navigation ────────────────────────────────────────────────────────────
  openCompanyDashboard(alert: Alert): void {
    this.apiSvc.getCompanyId(alert.companyTicker).subscribe({
      next: (res) => {
        this.router.navigate(['/company', res]);
      }
    });
    
  }

  openStrategy(alert: Alert): void {
    // Naviguer vers strategies + highlight la bonne
    this.router.navigate(['/strategies'], {
      queryParams: { highlight: alert.strategyId }
    });
  }

  // ── Formatting ────────────────────────────────────────────────────────────
  alertIcon(type: string): string {
    const m: Record<string, string> = {
      NCI_DROP:'↓', NCI_RISE:'↑', SENTIMENT_NEGATIVE:'📉',
      SENTIMENT_POSITIVE:'📈', STRATEGY_RISK:'⚡', COMMUNICATION_CRISIS:'🚨'
    };
    return m[type] ?? '◈';
  }

  chipClass(type: string): string {
    if (['NCI_DROP','SENTIMENT_NEGATIVE','COMMUNICATION_CRISIS'].includes(type)) return 'chip chip-red';
    if (['NCI_RISE','SENTIMENT_POSITIVE'].includes(type)) return 'chip chip-green';
    return 'chip chip-amber';
  }

  formatAlertType(type: string): string {
    if (!type) return '';
    return type.split('_').join(' ');
  }
}
