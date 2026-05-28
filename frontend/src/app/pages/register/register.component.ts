import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../environments/environment';

interface RegisterForm {
  username:    string;
  email:       string;
  password:    string;
  confirmPwd:  string;
  profileType: 'PRUDENT' | 'SPECULATEUR';
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {
  private readonly http   = inject(HttpClient);
  public readonly auth   = inject(AuthService);

  // ── Signals d'état ────────────────────────────────────────────────────────
  loading      = signal(false);
  success      = signal(false);
  errorMessage = signal('');
  showPassword = signal(false);
  selectedProfile = signal<'PRUDENT' | 'SPECULATEUR'>('PRUDENT');

  // ── Formulaire ────────────────────────────────────────────────────────────
  form: RegisterForm = {
    username:    '',
    email:       '',
    password:    '',
    confirmPwd:  '',
    profileType: 'PRUDENT'
  };

  // ── Actions ───────────────────────────────────────────────────────────────

  selectProfile(type: 'PRUDENT' | 'SPECULATEUR'): void {
    this.selectedProfile.set(type);
    this.form.profileType = type;
  }

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  async submit(ngForm: NgForm): Promise<void> {

    if (ngForm.invalid) return;

    if (this.form.password !== this.form.confirmPwd) {
      this.errorMessage.set('Les mots de passe ne correspondent pas.');
      return;
    }
    if (this.form.password.length < 8) {
      this.errorMessage.set('Le mot de passe doit contenir au moins 8 caractères.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    const body = {
      username:    this.form.username.trim(),
      email:       this.form.email.trim().toLowerCase(),
      password:    this.form.password,
      profileType: this.form.profileType
    };

    try {
      await this.http.post(
        `${environment.apiUrl}/auth/register`,
        body
      ).toPromise();

      this.success.set(true);

      // Après 3 secondes, rediriger vers Keycloak login
      setTimeout(() => {
        this.auth.login();
      }, 3000);

    } catch (err: any) {
      const errorCode = err?.error?.error;
      const message   = err?.error?.message;

      if (errorCode === 'USERNAME_EXISTS') {
        this.errorMessage.set('Ce nom d\'utilisateur est déjà pris.');
      } else if (errorCode === 'EMAIL_EXISTS') {
        this.errorMessage.set('Un compte avec cet email existe déjà.');
      } else {
        this.errorMessage.set(message ?? 'Une erreur inattendue s\'est produite.');
      }
    } finally {
      this.loading.set(false);
    }
  }

  // ── Helpers UI ────────────────────────────────────────────────────────────

  passwordStrength(): { label: string; class: string; width: number } {
    const p = this.form.password;
    if (!p) return { label: '', class: '', width: 0 };

    let score = 0;
    if (p.length >= 8)  score++;
    if (p.length >= 12) score++;
    if (/[A-Z]/.test(p))   score++;
    if (/[0-9]/.test(p))   score++;
    if (/[^A-Za-z0-9]/.test(p)) score++;

    if (score <= 1) return { label: 'Faible',  class: 'strength--weak',   width: 25 };
    if (score <= 2) return { label: 'Moyen',   class: 'strength--medium', width: 50 };
    if (score <= 3) return { label: 'Bon',     class: 'strength--good',   width: 75 };
    return           { label: 'Fort',   class: 'strength--strong', width: 100 };
  }
}
