import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AssistantChatResponse {
  message: string;
  mode: 'CHATBOT' | 'CLARIFICATION' | 'REPORT' | string;
  success: boolean;
  conversationId?: string;
}

export type AssistantResult =
  | { kind: 'text'; body: AssistantChatResponse }
  | { kind: 'pdf'; blob: Blob; meta: ReportPdfMeta };

export interface ReportPdfMeta {
  ticker: string;
  companyName: string;
  nciGlobal: number;
  nciPersonalized: number;
  fConsistency: number;
  sentiment: number;
}

export interface SaveStrategyPayload {
  ticker: string;
  companyName: string;
  userArgument: string;
  nciGlobal: number;
  nciPersonalized: number;
  fConsistency: number;
  sentiment: number;
  supportEvidence: string;
  redFlags: string;
  finalConclusion: string;
}

@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl.replace('/api', '/api/v2');

  chat(message: string, ticker?: string, conversationId?: string): Observable<AssistantResult> {
    const body: Record<string, string> = { message };
    if (ticker) body['ticker'] = ticker;
    if (conversationId) body['conversationId'] = conversationId;

    return new Observable(observer => {
      this.http.post(`${this.base}/assistant/chat`, body, {
        observe: 'response',
        responseType: 'blob'
      }).subscribe({
        next: res => {
          const contentType = res.headers.get('Content-Type') ?? '';
          if (contentType.includes('application/pdf')) {
            observer.next({
              kind: 'pdf',
              blob: res.body as Blob,
              meta: {
                ticker: res.headers.get('X-Report-Ticker') ?? '',
                companyName: res.headers.get('X-Report-Company') ?? '',
                nciGlobal: parseFloat(res.headers.get('X-NCI-Global') ?? '0'),
                nciPersonalized: parseFloat(res.headers.get('X-NCI-Personalized') ?? '0'),
                fConsistency: parseFloat(res.headers.get('X-F-Consistency') ?? '0'),
                sentiment: parseFloat(res.headers.get('X-Sentiment') ?? '0')
              }
            });
            observer.complete();
            return;
          }
          const reader = new FileReader();
          reader.onload = () => {
            try {
              const json = JSON.parse(reader.result as string) as AssistantChatResponse;
              observer.next({ kind: 'text', body: json });
              observer.complete();
            } catch (e) {
              observer.error(e);
            }
          };
          reader.onerror = () => observer.error(reader.error);
          reader.readAsText(res.body as Blob);
        },
        error: err => observer.error(err)
      });
    });
  }

  saveStrategy(payload: SaveStrategyPayload): Observable<{ success: boolean; strategyId: number; message: string }> {
    return this.http.post<{ success: boolean; strategyId: number; message: string }>(
      `${this.base}/strategy/save`,
      payload
    );
  }
}
