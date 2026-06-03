import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Alert, AlertType } from '../../../../models';
import { AlertService } from '../../../../services/alert.service';
import { AuthService } from 'src/app/services/auth.service';

@Component({
  selector: 'app-alert-center',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './alert-center.component.html',
  styleUrls: ['./alert-center.component.css']
})
export class AlertCenterComponent {
  @Input() alerts: Alert[] = [];
  @Input() ticker = '';

  private readonly alertSvc = inject(AlertService);
  private readonly authSvc = inject(AuthService);
  private readonly USER_ID = this.authSvc.profile()?.id ?? 0;

  markRead(alert: Alert): void {
    this.alertSvc.markRead(alert.id, this.USER_ID);
  }

  markAllRead(): void {
    this.alertSvc.markAllRead(this.USER_ID);
  }

  alertIcon(type: AlertType): string {
    const icons: Record<AlertType, string> = {
      NCI_DROP:            '↓',
      NCI_RISE:            '↑',
      SENTIMENT_NEGATIVE:  '📉',
      SENTIMENT_POSITIVE:  '📈',
      STRATEGY_RISK:       '⚡',
      COMMUNICATION_CRISIS:'🚨'
    };
    return icons[type] ?? '•';
  }

  chipClass(type: AlertType): string {
    const danger = ['NCI_DROP','SENTIMENT_NEGATIVE','COMMUNICATION_CRISIS'];
    const good   = ['NCI_RISE','SENTIMENT_POSITIVE'];
    const warn   = ['STRATEGY_RISK'];
    if (danger.includes(type)) return 'chip chip-red';
    if (good.includes(type))   return 'chip chip-green';
    if (warn.includes(type))   return 'chip chip-orange';
    return 'chip chip-amber';
  }

  get unread(): number {
    return this.alerts.filter(a => !a.isRead).length;
  }

  formatAlertType(type: string): string {
    return type.replace(/_/g, ' ');
 }
}
