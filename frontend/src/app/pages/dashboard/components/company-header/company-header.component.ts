import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Company } from '../../../../models';

@Component({
  selector: 'app-company-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './company-header.component.html',
  styleUrls: ['./company-header.component.css']
})
export class CompanyHeaderComponent {
  @Input() company!: Company;
}
