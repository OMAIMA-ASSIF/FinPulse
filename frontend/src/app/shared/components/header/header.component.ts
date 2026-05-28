import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';
import { AlertService } from '../../../services/alert.service';
import { ApiService } from '../../../services/api.service';
import { ThemeService } from '../../../services/theme.service';
import { WatchlistService } from '../../../services/watchlist.service';
import { Company } from '../../../models';
import { ClickOutsideDirective } from 'src/app/core/directives/click-outside.directive';
import { debounceTime, Subject, switchMap } from 'rxjs';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, CommonModule, FormsModule, ClickOutsideDirective],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent {
  readonly auth      = inject(AuthService);
  readonly alerts    = inject(AlertService);
  readonly theme     = inject(ThemeService);
  readonly watchlist = inject(WatchlistService);
  private readonly api    = inject(ApiService);
  private readonly router = inject(Router);

  searchQuery   = signal('');
  searchResults = signal<Company[]>([]);
  searchOpen    = signal(false);
  userMenuOpen  = signal(false);

  private readonly search$ = new Subject<string>();

  readonly navLinks = [
    { path: '/discover',      label: 'Discover',       icon: 'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z' },
    { path: '/watchlist',     label: 'My Watchlist',   icon: 'M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z' },
    { path: '/ai-assistant',  label: 'AI Agent',       icon: 'M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z' },
    { path: '/strategies',    label: 'My Strategies',  icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6m14 0v-6a2 2 0 00-2-2h-2a2 2 0 00-2 2v6' },
    { path: '/notifications', label: 'Notifications',  icon: 'M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9' },
  ];

  constructor() {
    this.search$.pipe(
      debounceTime(300),
      switchMap(q => this.api.searchCompanies(q))
    ).subscribe(r => this.searchResults.set(r));
  }

  onSearch(q: string): void {
    this.searchQuery.set(q);
    if (q.trim().length >= 2) { this.searchOpen.set(true); this.search$.next(q); }
    else { this.searchOpen.set(false); this.searchResults.set([]); }
  }

  goToCompany(id: number): void { this.clearSearch(); this.router.navigate(['/company', id]); }
  clearSearch(): void { this.searchQuery.set(''); this.searchOpen.set(false); this.searchResults.set([]); }
  toggleUserMenu(): void { this.userMenuOpen.update(v => !v); }
  closeUserMenu(): void  { this.userMenuOpen.set(false); }
  logout(): void { this.auth.logout(); }

  profileLabel(): string { return this.auth.profileType() === 'PRUDENT' ? 'PRUDENT' : 'SPÉCULATEUR'; }
  profileClass(): string { return this.auth.profileType() === 'PRUDENT' ? 'profile-prudent' : 'profile-spec'; }
  avatarUrl(): string | null { return this.auth.avatarUrl(); }
}
