import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { KeycloakService } from 'keycloak-angular';
import { NciUpdateEvent } from '../models';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SseService {
  private readonly kc = inject(KeycloakService);

  private es:         EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private retryDelay  = 1000;
  private readonly MAX_RETRY = 30_000;
  private userId  = '';
  private tickers?: string[];

  readonly connected = signal(false);
  readonly lastEvent = signal<NciUpdateEvent | null>(null);
  readonly update$   = new Subject<NciUpdateEvent>();

  async connect(userId: string, tickers?: string[]): Promise<void> {
    this.disconnect();
    this.userId = userId; this.tickers = tickers; this.retryDelay = 1000;
    await this.openConnection();
  }

  private async openConnection(): Promise<void> {
    try {
      await this.kc.updateToken(30);
      const token = await this.kc.getToken();
      if (!token) { console.warn('[SSE] No token available'); return; }

      const params = new URLSearchParams({ userId: this.userId, token });
      if (this.tickers?.length) params.set('tickers', this.tickers.join(','));

      this.es = new EventSource(`${environment.apiUrl}/stream/watchlist?${params.toString()}`);

      this.es.addEventListener('connected', () => {
        this.connected.set(true);
        this.retryDelay = 1000;
        console.log('[SSE] Connected');
      });

      this.es.addEventListener('nci-update', (e: MessageEvent) => {
        try {
          const evt: NciUpdateEvent = JSON.parse(e.data);
          this.lastEvent.set(evt);
          this.update$.next(evt);
        } catch (err) { console.error('[SSE] Parse error', err); }
      });

      this.es.addEventListener('heartbeat', () => {});

      this.es.onerror = () => {
        this.connected.set(false);
        this.closeEventSource();
        this.scheduleReconnect();
      };
    } catch (err) {
      console.error('[SSE] Token error:', err);
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (this.retryTimer) return;
    this.retryTimer = setTimeout(async () => {
      this.retryTimer = null;
      await this.openConnection();
    }, this.retryDelay);
    this.retryDelay = Math.min(this.retryDelay * 2, this.MAX_RETRY);
  }

  disconnect(): void {
    if (this.retryTimer) { clearTimeout(this.retryTimer); this.retryTimer = null; }
    this.closeEventSource();
    this.retryDelay = 1000;
  }

  private closeEventSource(): void {
    if (this.es) { this.es.close(); this.es = null; }
    this.connected.set(false);
  }

  isConnected(): boolean { return this.es?.readyState === EventSource.OPEN; }
}
