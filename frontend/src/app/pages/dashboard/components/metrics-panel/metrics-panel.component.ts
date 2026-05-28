import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Company } from '../../../../models';

@Component({
  selector: 'app-metrics-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metrics-panel.component.html',
  styleUrls: ['./metrics-panel.component.css']
})
export class MetricsPanelComponent {
  @Input() company!: Company;
  @Input() personalizedNci: number | null = null;

  nciColor(nci: number): string {
    return nci >= 0.7 ? 'var(--green)' : nci >= 0.4 ? 'var(--orange)' : 'var(--red)';
  }
  sentimentColor(s: number): string {
    return s >= 0.6 ? 'var(--green)' : s <= 0.3 ? 'var(--red)' : 'var(--orange)';
  }
  riskLabel(level: string): string {
    return level === 'LOW_RISK' ? 'Low' : level === 'MEDIUM_RISK' ? 'Medium' : 'High';
  }
  riskColor(level: string): string {
    return level === 'LOW_RISK' ? 'var(--green)' : level === 'MEDIUM_RISK' ? 'var(--orange)' : 'var(--red)';
  }
}
