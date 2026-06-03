import { Component, inject, signal, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { StrategyService } from '../../services/strategy.service';
import { AlertService } from '../../services/alert.service';
import { environment } from '../../../environments/environment';

type ProfileTab = 'account' | 'security' | 'preferences';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent {
  @ViewChild('fileInput') fileInputRef!: ElementRef<HTMLInputElement>;

  readonly auth        = inject(AuthService);
  readonly strategySvc = inject(StrategyService);
  readonly alertSvc    = inject(AlertService);
  private readonly http   = inject(HttpClient);
  private readonly router = inject(Router);

  // ── Tabs ──────────────────────────────────────────────────────────────────
  activeTab = signal<ProfileTab>('account');

  // ── Account ───────────────────────────────────────────────────────────────
  editMode     = signal(false);
  editUsername = signal('');
  editEmail    = signal('');
  infoSaving   = signal(false);
  infoSaved    = signal(false);
  infoError    = signal('');

  // ── Avatar ────────────────────────────────────────────────────────────────
  avatarUploading = signal(false);
  avatarError     = signal('');

  // ── Password ──────────────────────────────────────────────────────────────
  pwdForm = { current: '', newPwd: '', confirm: '' };
  showCurrentPwd = signal(false);
  showNewPwd     = signal(false);
  pwdSaving      = signal(false);
  pwdSaved       = signal(false);
  pwdError       = signal('');

  // ── Preferences ───────────────────────────────────────────────────────────
  profileSaving = signal(false);
  profileSaved  = signal(false);

  // ── Delete ────────────────────────────────────────────────────────────────
  showDeleteConfirm = signal(false);
  deleting          = signal(false);
  globalError       = signal('');

  readonly baseUrl = environment.apiUrl.replace('/api', '');

  // ── Account edit ──────────────────────────────────────────────────────────
  startEdit(): void {
    this.editUsername.set(this.auth.username());
    this.editEmail.set(this.auth.email());
    this.editMode.set(true);
    this.infoError.set('');
  }
  cancelEdit(): void { this.editMode.set(false); this.infoError.set(''); }

  saveInfo(): void {
    const username = this.editUsername().trim();
    const email    = this.editEmail().trim();
    if (!username || !email) { this.infoError.set('Username and email are required.'); return; }
    this.infoSaving.set(true);
    this.auth.updateInfo({ username, email }).subscribe({
      next: updated => {
        this.auth._profile.update(p => p ? { ...p, username: updated.username, email: updated.email } : p);
        this.editMode.set(false); this.infoSaving.set(false);
        this.infoSaved.set(true); setTimeout(() => this.infoSaved.set(false), 3000);
      },
      error: err => { this.infoSaving.set(false); this.infoError.set(err?.error?.message ?? 'Update failed.'); }
    });
  }

  // ── Avatar ────────────────────────────────────────────────────────────────
  triggerFileInput(): void { this.fileInputRef.nativeElement.click(); }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) { this.avatarError.set('Please select an image file.'); return; }
    if (file.size > 5 * 1024 * 1024)    { this.avatarError.set('Image must be under 5MB.'); return; }
    this.avatarUploading.set(true); this.avatarError.set('');
    this.auth.uploadAvatar(file).subscribe({
      next: res => { this.auth.setAvatarUrl(res.avatarUrl); this.avatarUploading.set(false); },
      error: err => { this.avatarUploading.set(false); this.avatarError.set(err?.error?.error ?? 'Upload failed.'); }
    });
    (event.target as HTMLInputElement).value = '';
  }

  removeAvatar(): void {
    this.auth.deleteAvatar().subscribe({
      next: () => this.auth.setAvatarUrl(null),
      error: () => this.avatarError.set('Failed to remove avatar.')
    });
  }

  // ── Password ──────────────────────────────────────────────────────────────
  changePassword(): void {
    this.pwdError.set('');
    if (!this.pwdForm.current)               { this.pwdError.set('Current password is required.'); return; }
    if (this.pwdForm.newPwd.length < 8)      { this.pwdError.set('New password must be at least 8 characters.'); return; }
    if (this.pwdForm.newPwd !== this.pwdForm.confirm) { this.pwdError.set('Passwords do not match.'); return; }
    if (this.pwdForm.current === this.pwdForm.newPwd) { this.pwdError.set('New password must differ from current.'); return; }

    this.pwdSaving.set(true);
    this.http.patch(`${environment.apiUrl}/auth/password`, {
      currentPassword: this.pwdForm.current,
      newPassword:     this.pwdForm.newPwd,
      confirmPassword: this.pwdForm.confirm
    }).subscribe({
      next: () => {
        this.pwdSaving.set(false); this.pwdSaved.set(true);
        this.pwdForm = { current: '', newPwd: '', confirm: '' };
        setTimeout(() => this.pwdSaved.set(false), 4000);
      },
      error: err => { this.pwdSaving.set(false); this.pwdError.set(err?.error?.error ?? 'Password change failed.'); }
    });
  }

  passwordStrength(): { label: string; class: string; width: number } {
    const p = this.pwdForm.newPwd;
    if (!p) return { label: '', class: '', width: 0 };
    let s = 0;
    if (p.length >= 8)           s++;
    if (p.length >= 12)          s++;
    if (/[A-Z]/.test(p))         s++;
    if (/[0-9]/.test(p))         s++;
    if (/[^A-Za-z0-9]/.test(p))  s++;
    if (s <= 1) return { label: 'Weak',   class: 'strength--weak',   width: 25  };
    if (s <= 2) return { label: 'Fair',   class: 'strength--medium', width: 50  };
    if (s <= 3) return { label: 'Good',   class: 'strength--good',   width: 75  };
    return               { label: 'Strong', class: 'strength--strong', width: 100 };
  }

  // ── Preferences ───────────────────────────────────────────────────────────
  setProfile(type: 'PRUDENT' | 'SPECULATEUR'): void {
    if (this.auth.profileType() === type) return;
    this.profileSaving.set(true);
    this.auth.updateProfileType(type).subscribe({
      next: () => {
        this.auth._profile.update(p => p ? { ...p, profileType: type } : p);
        this.profileSaved.set(true); this.profileSaving.set(false);
        setTimeout(() => this.profileSaved.set(false), 3000);
      },
      error: () => this.profileSaving.set(false)
    });
  }

  nciImpact(): string {
    return this.auth.profileType() === 'PRUDENT'
      ? '× 1.0 — Raw NCI score (conservative)'
      : '× 1.15 — Amplified NCI (risk tolerant)';
  }

  // ── Delete account ────────────────────────────────────────────────────────
  confirmDelete(): void { this.showDeleteConfirm.set(true); }
  cancelDelete(): void  { this.showDeleteConfirm.set(false); }

  deleteAccount(): void {
    this.deleting.set(true);
    this.auth.deleteAccount().subscribe({
      next: () => this.auth.logout(),
      error: err => {
        this.deleting.set(false);
        this.globalError.set(err?.error?.message ?? 'Account deletion failed.');
        this.showDeleteConfirm.set(false);
      }
    });
  }

  logout(): void { this.auth.logout(); }

  fullAvatarUrl(): string | null {
    const url = this.auth.avatarUrl();
    if (!url) return null;
    return url.startsWith('http') ? url : `${this.baseUrl}${url}`;
  }
}