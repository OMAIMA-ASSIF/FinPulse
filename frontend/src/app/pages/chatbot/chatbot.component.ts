import {
  Component, OnInit, AfterViewChecked,
  inject, signal, ViewChild, ElementRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../services/api.service';
import { AssistantService, ReportPdfMeta, SaveStrategyPayload } from '../../services/assistant.service';
import { StrategyService } from '../../services/strategy.service';
import { WatchlistService } from '../../services/watchlist.service';
import { AuthService } from '../../services/auth.service';
import { Company, ChatMessage, StrategyCard } from '../../models';
import { environment } from '../../../environments/environment';

interface SessionSummary {
  id: number;
  title: string;
  contextType: string;
  lastMessageAt: string;
  messageCount: number;
}

@Component({
  selector: 'app-chatbot',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot.component.html',
  styleUrls: ['./chatbot.component.css']
})
export class ChatbotComponent implements OnInit, AfterViewChecked {
  @ViewChild('msgContainer') containerRef!: ElementRef<HTMLDivElement>;
  @ViewChild('textarea')     textareaRef!: ElementRef<HTMLTextAreaElement>;

  private readonly http          = inject(HttpClient);
  private readonly api           = inject(ApiService);
  private readonly assistantSvc  = inject(AssistantService);
  private readonly strategySvc   = inject(StrategyService);
  private readonly watchlistSvc  = inject(WatchlistService);
  readonly auth                = inject(AuthService);
  private readonly route       = inject(ActivatedRoute);
  private readonly router      = inject(Router);

  // ── State ──────────────────────────────────────────────────────────────────
  messages        = signal<ChatMessage[]>([]);
  sessions        = signal<SessionSummary[]>([]);
  activeSessionId = signal<number | null>(null);
  inputText       = signal('');
  isLoading       = signal(false);
  sessionsLoading = signal(false);
  pinnedCompanies = signal<Company[]>([]);
  savedIds        = signal<Set<string>>(new Set());
  conversationId  = signal<string | null>(null);
  pendingReport   = signal<{ blob: Blob; meta: ReportPdfMeta; thesis: string; msgId: string } | null>(null);

  // Delete confirmation state
  deleteConfirmId = signal<number | null>(null);
  deleting        = signal(false);
  showClearAllConfirm = signal(false);

  private prevMsgLen = 0;

  // ── Strategy keywords ──────────────────────────────────────────────────────
  private readonly STRATEGY_KEYWORDS = [
    'strategy','invest in','i want to invest','investment thesis',
    'my thesis','portfolio','horizon','because i believe','because of',
    'i believe','long term','short term','position in','bull case',
    'bear case','add to my watchlist'
  ];

  readonly quickPrompts = [
    'Analyze Apple narrative consistency',
    'What is the NCI score for TSLA?',
    'I want to invest in NVIDIA because AI demand is growing',
    'Compare Microsoft vs Google NCI scores',
    'My thesis: Tesla will dominate EV market because of FSD',
    'Show companies with highest narrative risk',
  ];

  // ── Init ───────────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.pinnedCompanies.set(
      this.watchlistSvc.pinnedCompanies()
    );
    this.loadSessions();

    this.route.queryParams.subscribe(p => {
      if (p['companyId']) {
        this.api.getCompanyById(+p['companyId']).subscribe(c => {
          this.sendMessage(
            `Analyze ${c.name} (${c.ticker}) — NCI score, narrative risks, and investment outlook.`,
            'AGENT', null, c.id
          );
        });
      }
    });
  }

  ngAfterViewChecked(): void {
    if (this.messages().length !== this.prevMsgLen) {
      this.scrollToBottom();
      this.prevMsgLen = this.messages().length;
    }
  }

  // ── Sessions ───────────────────────────────────────────────────────────────

  loadSessions(): void {
    this.sessionsLoading.set(true);
    this.http.get<SessionSummary[]>(`${environment.apiUrl}/chat-sessions`).subscribe({
      next: list => { this.sessions.set(list); this.sessionsLoading.set(false); },
      error: () => this.sessionsLoading.set(false)
    });
  }

  loadSession(session: SessionSummary): void {
    this.http.get<any>(`${environment.apiUrl}/chat-sessions/${session.id}`).subscribe(s => {
      this.activeSessionId.set(session.id);
      const msgs: ChatMessage[] = (s.messages ?? []).map((m: any) => ({
        id:        String(m.id),
        role:      m.sender === 'USER' ? 'user' : 'assistant',
        content:   m.message,
        timestamp: new Date(m.createdAt),
        mode:      m.intent as 'AGENT' | 'STRATEGY'
      }));
      this.messages.set(msgs);
      this.prevMsgLen = msgs.length;
    });
  }

  startNewChat(): void {
    this.activeSessionId.set(null);
    this.messages.set([]);
    this.prevMsgLen = 0;
    this.deleteConfirmId.set(null);
  }

  // ── Delete session ─────────────────────────────────────────────────────────

  askDelete(sessionId: number, event: Event): void {
    event.stopPropagation();
    this.deleteConfirmId.set(sessionId);
  }

  cancelDelete(): void { this.deleteConfirmId.set(null); }

  confirmDelete(sessionId: number): void {
    this.deleting.set(true);
    this.http.delete(`${environment.apiUrl}/chat-sessions/${sessionId}`).subscribe({
      next: () => {
        this.sessions.update(list => list.filter(s => s.id !== sessionId));
        // If currently viewing this session → clear chat
        if (this.activeSessionId() === sessionId) {
          this.startNewChat();
        }
        this.deleteConfirmId.set(null);
        this.deleting.set(false);
      },
      error: () => this.deleting.set(false)
    });
  }

  confirmClearAll(): void {
    this.http.delete(`${environment.apiUrl}/chat-sessions`).subscribe({
      next: () => {
        this.sessions.set([]);
        this.startNewChat();
        this.showClearAllConfirm.set(false);
      },
      error: () => this.showClearAllConfirm.set(false)
    });
  }

  // ── Send ───────────────────────────────────────────────────────────────────

  onEnter(e: KeyboardEvent): void {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this.send(); }
  }

  autoResize(): void {
    const ta = this.textareaRef?.nativeElement;
    if (!ta) return;
    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 140) + 'px';
  }

  sendQuick(prompt: string): void {
    this.inputText.set(prompt);
    setTimeout(() => this.send(), 10);
  }

  send(): void {
    const text = this.inputText().trim();
    if (!text || this.isLoading()) return;
    this.inputText.set('');
    if (this.textareaRef?.nativeElement) this.textareaRef.nativeElement.style.height = 'auto';
    const intent = this.detectMode(text);
    this.sendMessage(text, intent, this.activeSessionId(), null);
  }

  private sendMessage(text: string, intent: string,
                      sessionId: number | null, companyId: number | null): void {
    const userMsg: ChatMessage = {
      id: this.uid(), role: 'user', content: text,
      timestamp: new Date(), mode: intent as 'AGENT' | 'STRATEGY'
    };
    const loadingMsg: ChatMessage = {
      id: 'loading', role: 'assistant', content: '',
      timestamp: new Date(), loading: true, mode: intent as 'AGENT' | 'STRATEGY'
    };
    this.messages.update(m => [...m, userMsg, loadingMsg]);
    this.isLoading.set(true);
    this.pendingReport.set(null);

    let ticker: string | undefined;
    if (companyId) {
      this.api.getCompanyById(companyId).subscribe(c => {
        this.assistantSvc.chat(text, c.ticker, this.conversationId() ?? undefined)
          .subscribe({
            next: result => this.handleAssistantResult(result, text, intent, sessionId),
            error: err => this.handleAssistantError(err)
          });
      });
      return;
    }

    this.assistantSvc.chat(text, ticker, this.conversationId() ?? undefined).subscribe({
      next: result => this.handleAssistantResult(result, text, intent, sessionId),
      error: err => this.handleAssistantError(err)
    });
  }

  private handleAssistantResult(result: import('../../services/assistant.service').AssistantResult,
                                userText: string, intent: string, sessionId: number | null): void {
    if (result.kind === 'pdf') {
      const msgId = this.uid();
      this.pendingReport.set({ blob: result.blob, meta: result.meta, thesis: userText, msgId });
      const aiMsg: ChatMessage = {
        id: msgId,
        role: 'assistant',
        content: `📄 Strategic report generated for **${result.meta.companyName}** (${result.meta.ticker}). ` +
          `Review the PDF and click **Save Strategy** to add it to My Strategies.`,
        timestamp: new Date(),
        mode: 'STRATEGY',
        strategyCard: {
          ticker: result.meta.ticker,
          companyName: result.meta.companyName,
          thesis: userText,
          recommendation: 'HOLD',
          nciPersonalized: result.meta.nciPersonalized,
          bullCase: [],
          risks: [],
          contradictions: [],
          historicalInsight: ''
        }
      };
      this.messages.update(m => [...m.filter(x => x.id !== 'loading'), aiMsg]);
      this.isLoading.set(false);
      return;
    }

    const body = result.body;
    if (body.conversationId) {
      this.conversationId.set(body.conversationId);
    }

    const aiMsg: ChatMessage = {
      id: this.uid(),
      role: 'assistant',
      content: body.message ?? 'No response.',
      timestamp: new Date(),
      mode: body.mode === 'CLARIFICATION' ? 'AGENT' : (intent as 'AGENT' | 'STRATEGY')
    };
    this.messages.update(m => [...m.filter(x => x.id !== 'loading'), aiMsg]);
    this.isLoading.set(false);
  }

  private handleAssistantError(err: unknown): void {
    const errMsg: ChatMessage = {
      id: this.uid(), role: 'assistant',
      content: `⚠️ Could not reach AI backend. Please try again.`,
      timestamp: new Date(), mode: 'AGENT'
    };
    this.messages.update(m => [...m.filter(x => x.id !== 'loading'), errMsg]);
    this.isLoading.set(false);
  }

  // ── Save Strategy ──────────────────────────────────────────────────────────

  async saveStrategy(msg: ChatMessage): Promise<void> {
    if (this.savedIds().has(msg.id)) return;
    const pending = this.pendingReport();
    if (!pending || pending.msgId !== msg.id) {
      this.appendSystem('⚠️ No report available to save. Generate a strategy report first.');
      return;
    }

    const payload: SaveStrategyPayload = {
      ticker: pending.meta.ticker,
      companyName: pending.meta.companyName,
      userArgument: pending.thesis,
      nciGlobal: pending.meta.nciGlobal,
      nciPersonalized: pending.meta.nciPersonalized,
      fConsistency: pending.meta.fConsistency,
      sentiment: pending.meta.sentiment,
      supportEvidence: '',
      redFlags: '',
      finalConclusion: ''
    };

    this.assistantSvc.saveStrategy(payload).subscribe({
      next: () => {
        this.savedIds.update(s => new Set([...s, msg.id]));
        this.strategySvc.load();
        this.watchlistSvc.refreshAfterStrategySave();
        this.appendSystem(`✅ Strategy for **${pending.meta.companyName}** saved! See it in My Strategies.`);
      },
      error: () => this.appendSystem(`❌ Could not save strategy for ${pending.meta.ticker}.`)
    });
  }

  cancelStrategy(msg: ChatMessage): void {
    this.messages.update(m => m.map(x =>
      x.id === msg.id ? { ...x, strategyCard: undefined } : x
    ));
  }

  downloadPdf(msg: ChatMessage): void {
    const pending = this.pendingReport();
    if (!pending || pending.msgId !== msg.id) return;
    const a = document.createElement('a');
    a.href = URL.createObjectURL(pending.blob);
    a.download = `rapport_${pending.meta.ticker}.pdf`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  clearCurrentChat(): void {
    this.startNewChat();
  }

  // ── Formatting ─────────────────────────────────────────────────────────────

  formatContent(text: string): string {
    return text
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/`(.*?)`/g, '<code class="inline-code">$1</code>')
      .replace(/\n/g, '<br>');
  }

  modeLabel(mode?: string): string {
    return mode === 'STRATEGY' ? '⚡ Strategy Engine' : 'Chatbot mode';
  }

  modeClass(mode?: string): string {
    return mode === 'STRATEGY'
      ? 'chat-mode-badge chat-mode-badge--strategy'
      : 'chat-mode-badge chat-mode-badge--agent';
  }

  recClass(r: string): string {
    if (r === 'BUY')   return 'chip chip-green';
    if (r === 'AVOID') return 'chip chip-red';
    return 'chip chip-amber';
  }

  isSaved(id: string): boolean { return this.savedIds().has(id); }

  timeAgo(dateStr: string): string {
    const d = new Date(dateStr);
    const diff = Date.now() - d.getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1)  return 'Just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24)  return `${hrs}h ago`;
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  // ── Private ────────────────────────────────────────────────────────────────

  detectMode(text: string): string {
    const lower = text.toLowerCase();
    return this.STRATEGY_KEYWORDS.some(k => lower.includes(k)) ? 'STRATEGY' : 'AGENT';
  }

  private buildStrategyCard(input: string, aiResponse: string): StrategyCard {
    const tickerMatch = input.match(/\b([A-Z]{2,5})\b/);
    const ticker      = tickerMatch ? tickerMatch[1] : 'UNKNOWN';
    const nameMatch   = this.watchlistSvc.pinnedCompanies().find(c => c.ticker === ticker);
    const recMatch    = aiResponse.match(/\b(BUY|HOLD|AVOID)\b/i);
    const rec         = (recMatch ? recMatch[1].toUpperCase() : 'HOLD') as 'BUY'|'HOLD'|'AVOID';

    return {
      companyName:       nameMatch?.name ?? ticker,
      ticker,
      thesis:            input.slice(0, 300),
      bullCase:          this.extractList(aiResponse, ['bull','positive','strength','upside']),
      risks:             this.extractList(aiResponse, ['risk','danger','concern','weakness','downside']),
      contradictions:    this.extractList(aiResponse, ['contradict','inconsist','conflict','discrepanc']),
      historicalInsight: this.extractSentence(aiResponse, ['historical','history','past','previous','trend']),
      recommendation:    rec,
      nciPersonalized:   nameMatch
        ? nameMatch.nciGlobal * (this.auth.isSpec() ? 1.15 : 1.0)
        : 0.55
    };
  }

  private extractList(text: string, keywords: string[]): string[] {
    const sentences = text.split(/[.\n]/).map(s => s.trim()).filter(s => s.length > 15);
    const matched   = sentences.filter(s =>
      keywords.some(k => s.toLowerCase().includes(k))
    ).slice(0, 3);
    return matched.length ? matched : ['See full analysis above'];
  }

  private extractSentence(text: string, keywords: string[]): string {
    const sentences = text.split(/[.\n]/).map(s => s.trim()).filter(s => s.length > 20);
    return sentences.find(s => keywords.some(k => s.toLowerCase().includes(k)))
        ?? 'Historical analysis based on NCI archive data.';
  }

  private appendSystem(content: string): void {
    this.messages.update(m => [...m, {
      id: this.uid(), role: 'assistant' as const,
      content, timestamp: new Date(), mode: 'AGENT' as const
    }]);
  }

  private scrollToBottom(): void {
    try { this.containerRef?.nativeElement?.scrollTo({ top: 99999, behavior: 'smooth' }); } catch {}
  }

  private uid(): string {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  }
}
