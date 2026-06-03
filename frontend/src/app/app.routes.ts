import { Routes } from '@angular/router';
import { authGuard, publicGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  // Public
  { path: 'landing',  canActivate: [publicGuard], loadComponent: () => import('./pages/landing/landing.component').then(m => m.LandingComponent) },
  { path: 'register', canActivate: [publicGuard], loadComponent: () => import('./pages/register/register.component').then(m => m.RegisterComponent) },

  // Protégées
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'discover', pathMatch: 'full' },
      { path: 'discover',       loadComponent: () => import('./pages/discover/discover.component').then(m => m.DiscoverComponent) },
      { path: 'watchlist',      loadComponent: () => import('./pages/watchlist/watchlist.component').then(m => m.WatchlistComponent) },
      { path: 'ai-assistant',   loadComponent: () => import('./pages/chatbot/chatbot.component').then(m => m.ChatbotComponent) },
      { path: 'strategies',     loadComponent: () => import('./pages/strategies/strategies.component').then(m => m.StrategiesComponent) },
      { path: 'notifications',  loadComponent: () => import('./pages/notifications/notifications.component').then(m => m.NotificationsComponent) },
      { path: 'profile',        loadComponent: () => import('./pages/profile/profile.component').then(m => m.ProfileComponent) },
      { path: 'company/:id',    loadComponent: () => import('./pages/company-detail/company-detail.component').then(m => m.CompanyDetailComponent) },
      { path: 'guide',          loadComponent: () => import('./pages/guide/guide.component').then(m => m.GuideComponent) },
    ]
  },
  { path: '**', redirectTo: 'landing' }
];
