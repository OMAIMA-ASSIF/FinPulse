import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { News } from '../../../../models';

@Component({
  selector: 'app-breaking-news',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './breaking-news.component.html',
  styleUrls: ['./breaking-news.component.css']
})
export class BreakingNewsComponent {
  @Input() news: News[] = [];

  sentimentClass(label: string): string {
    if (label === 'POSITIVE') return 'chip chip-green';
    if (label === 'NEGATIVE') return 'chip chip-red';
    return 'chip chip-amber';
  }
}
