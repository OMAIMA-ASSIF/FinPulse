import { Injectable, inject, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';
import { HttpClient } from '@angular/common/http';
import { from, Observable, firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UserProfile {
  id: number;
  keycloakId: string;
  username: string;
  email: string;
  profileType: 'PRUDENT' | 'SPECULATEUR';
  strategyCount: number;
  createdAt: string;
  avatarUrl?: string | null;    // ← nouveau
  firstLogin?: boolean;         // ← nouveau
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly kc     = inject(KeycloakService);
  private readonly router = inject(Router);
  private readonly http   = inject(HttpClient);

  // ── Signals ───────────────────────────────────────────────────────────────
  readonly _profile    = signal<UserProfile | null>(null);
  private readonly _loading    = signal(false);
  private readonly _loggedIn   = signal(false);

  readonly profile     = this._profile.asReadonly();
  readonly loading     = this._loading.asReadonly();
  readonly isLoggedIn  = this._loggedIn.asReadonly();

  // Computed
  readonly username    = computed(() => this._profile()?.username    ?? this.kcUsername);
  readonly email       = computed(() => this._profile()?.email       ?? this.kcEmail);
  readonly profileType = computed(() => this._profile()?.profileType ?? 'PRUDENT');
  readonly initials    = computed(() => this.username().slice(0, 2).toUpperCase());
  readonly isPrudent   = computed(() => this.profileType() === 'PRUDENT');
  readonly isSpec      = computed(() => this.profileType() === 'SPECULATEUR');
  readonly avatarUrl   = computed(() => this._profile()?.avatarUrl ?? null);  // ← nouveau
  readonly isFirstLogin = computed(() => this._profile()?.firstLogin ?? false); // ← nouveau

  // ── Keycloak raw data ─────────────────────────────────────────────────────
  get kcUsername(): string { return this.kc.getKeycloakInstance().tokenParsed?.['preferred_username'] ?? ''; }
  get kcEmail(): string    { return this.kc.getKeycloakInstance().tokenParsed?.['email'] ?? ''; }
  get kcSub(): string      { return this.kc.getKeycloakInstance().tokenParsed?.['sub'] ?? ''; }
  get roles(): string[]    { return this.kc.getUserRoles(); }

  // ── Init ──────────────────────────────────────────────────────────────────
  async init(): Promise<void> {
    const loggedIn = await this.kc.isLoggedIn();
    this._loggedIn.set(loggedIn);
    if (loggedIn) await this.loadProfile();
  }

  // ── Load profile from backend ─────────────────────────────────────────────
  async loadProfile(): Promise<void> {
    this._loading.set(true);
    try {
      const profile = await firstValueFrom(
        this.http.get<UserProfile>(`${environment.apiUrl}/auth/me`)
      );
      this._profile.set(profile);
    } catch (err) {
      console.error('[AuthService] Cannot load profile:', err);
    } finally {
      this._loading.set(false);
    }
  }

  // ── Auth actions ──────────────────────────────────────────────────────────
  login(): void {
    this.kc.login({ redirectUri: `${window.location.origin}/discover` });
  }

  async logout(): Promise<void> {
    this._profile.set(null);
    this._loggedIn.set(false);
    await this.kc.logout(`${window.location.origin}/`);
  }

  // ── Profile type ──────────────────────────────────────────────────────────
  updateProfileType(type: 'PRUDENT' | 'SPECULATEUR'): Observable<any> {
    return this.http.patch(`${environment.apiUrl}/auth/profile-type`, { profileType: type });
  }

  // ── Avatar upload ─────────────────────────────────────────────────────────
  uploadAvatar(file: File): Observable<{ avatarUrl: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ avatarUrl: string }>(
      `${environment.apiUrl}/auth/avatar`, form
    );
  }

  deleteAvatar(): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/auth/avatar`);
  }

  /** Met à jour le signal local après upload réussi */
  setAvatarUrl(url: string | null): void {
    this._profile.update(p => p ? { ...p, avatarUrl: url } : p);
  }

  // ── Delete account ────────────────────────────────────────────────────────
  deleteAccount(): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/auth/account`);
  }

  // ── Update username/email ─────────────────────────────────────────────────
  updateInfo(data: { username?: string; email?: string }): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${environment.apiUrl}/auth/info`, data);
  }

  // ── First login flag ──────────────────────────────────────────────────────
  markFirstLoginDone(): Observable<void> {
    return this.http.patch<void>(`${environment.apiUrl}/auth/first-login-done`, {});
  }

  // ── Helpers ───────────────────────────────────────────────────────────────
  getToken(): Observable<string> { return from(this.kc.getToken()); }
  async refreshToken(): Promise<boolean> { return this.kc.updateToken(60); }
  hasRole(role: string): boolean { return this.kc.isUserInRole(role); }
}
