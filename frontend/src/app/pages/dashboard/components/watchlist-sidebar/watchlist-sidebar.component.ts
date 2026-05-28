import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Company } from '../../../../models';
import { AlertService } from '../../../../services/alert.service';
import { SseService } from '../../../../services/sse.service';

@Component({
  selector: 'app-watchlist-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './watchlist-sidebar.component.html',
  styleUrls: ['./watchlist-sidebar.component.css']
})
export class WatchlistSidebarComponent {
  @Input() companies: Company[] = [];
  @Input() loading = false;
  @Input() selectedId: number | null = null;
  @Output() companySelected = new EventEmitter<Company>();

  readonly alerts = inject(AlertService);
  readonly sse    = inject(SseService);

  select(c: Company): void { this.companySelected.emit(c); }

  trendIcon(nciLabel: string): string {
    return nciLabel === 'HIGH' ? '↑' : nciLabel === 'LOW' ? '↓' : '→';
  }

  trendClass(nciLabel: string): string {
    return nciLabel === 'HIGH' ? 'trend-up' : nciLabel === 'LOW' ? 'trend-down' : 'trend-flat';
  }

  unreadCount(ticker: string): number {
    return this.alerts.unreadForCompany(ticker);
  }

  nciPercent(nci: number): number {
    return Math.round(nci * 100);
  }
}
