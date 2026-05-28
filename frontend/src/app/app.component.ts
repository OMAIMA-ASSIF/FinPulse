import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from './shared/components/header/header.component';
import { AuthService } from './services/auth.service';
import { AlertService } from './services/alert.service';
import { StrategyService } from './services/strategy.service';
import { SseService } from './services/sse.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent,CommonModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  readonly auth = inject(AuthService);

  private readonly alerts     = inject(AlertService);
  private readonly strategies = inject(StrategyService);
  private readonly sse        = inject(SseService);

  ngOnInit(): void {
    // Les services métier sont initialisés UNIQUEMENT si l'utilisateur est connecté
    // auth.init() est déjà appelé dans APP_INITIALIZER (app.config.ts)
    if (this.auth.isLoggedIn()) {
      const userId = this.auth.profile()?.id ?? 0;
      this.alerts.load();
      this.strategies.load();
      this.sse.connect(String(userId));
    }
  }
}
