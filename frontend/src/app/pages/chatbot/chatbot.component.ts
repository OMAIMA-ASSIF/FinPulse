import {
  Component, OnInit, AfterViewChecked,
  inject, signal, ViewChild, ElementRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../services/api.service';
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

  private readonly http        = inject(HttpClient);
  private readonly api         = inject(ApiService);
  private readonly strategySvc = inject(StrategyService);
  private readonly watchlistSvc= inject(WatchlistService);
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

    const body: any = { message: text, intent };
    if (sessionId) body.sessionId = sessionId;
    if (companyId) body.companyId = companyId;

    this.http.post<any>(`${environment.apiUrl}/chat-sessions/message`, body).subscribe({
      next: res => {
        // Update active session ID (newly created session)
        if (!this.activeSessionId()) {
          this.activeSessionId.set(res.sessionId);
          this.loadSessions(); // Refresh left panel
        }

        const stratCard = intent === 'STRATEGY'
          ? this.buildStrategyCard(text, res.response) : undefined;

        const aiMsg: ChatMessage = {
          id: this.uid(), role: 'assistant',
          content: res.response,
          timestamp: new Date(),
          mode: res.intent as 'AGENT' | 'STRATEGY',
          strategyCard: stratCard
        };
        this.messages.update(m => [...m.filter(x => x.id !== 'loading'), aiMsg]);
        this.isLoading.set(false);
      },
      error: err => {
        const errMsg: ChatMessage = {
          id: this.uid(), role: 'assistant',
          content: `⚠️ ${err?.error?.message ?? 'Could not reach AI backend. Please try again.'}`,
          timestamp: new Date(), mode: 'AGENT'
        };
        this.messages.update(m => [...m.filter(x => x.id !== 'loading'), errMsg]);
        this.isLoading.set(false);
      }
    });
  }

  // ── Save Strategy ──────────────────────────────────────────────────────────

  async saveStrategy(msg: ChatMessage): Promise<void> {
    if (!msg.strategyCard || this.savedIds().has(msg.id)) return;
    const card  = msg.strategyCard;
    const match = this.watchlistSvc.pinnedCompanies()
      .find(c => c.ticker === card.ticker
              || msg.content.toLowerCase().includes(c.ticker.toLowerCase()));

    if (!match) {
      this.appendSystem(`📌 Please pin **${card.ticker}** to your watchlist first, then save the strategy.`);
      return;
    }

    try {
      await this.strategySvc.create({ companyId: match.id, userArgument: card.thesis });
      this.savedIds.update(s => new Set([...s, msg.id]));
      this.appendSystem(`✅ Strategy for **${match.name}** saved! Monitoring is now active.`);
    } catch {
      this.appendSystem(`❌ Could not save strategy. It may already exist for ${match.ticker}.`);
    }
  }

  cancelStrategy(msg: ChatMessage): void {
    this.messages.update(m => m.map(x =>
      x.id === msg.id ? { ...x, strategyCard: undefined } : x
    ));
  }

  downloadPdf(msg: ChatMessage): void {
    if (!msg.strategyCard) return;
    const c   = msg.strategyCard;
    const txt = `FinPulse Strategy Report\n${'─'.repeat(40)}\n`
      + `Company: ${c.companyName} (${c.ticker})\n`
      + `Recommendation: ${c.recommendation}\n`
      + `NCI: ${Math.round(c.nciPersonalized * 100)}/100\n\n`
      + `Thesis:\n${c.thesis}\n\n`
      + `Bull Case:\n${c.bullCase.map(b => `• ${b}`).join('\n')}\n\n`
      + `Risks:\n${c.risks.map(r => `• ${r}`).join('\n')}\n\n`
      + `Historical Insight:\n${c.historicalInsight}\n\n`
      + `Generated: ${new Date().toLocaleString()}`;

    const blob = new Blob([txt], { type: 'text/plain' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `strategy-${c.ticker}-${Date.now()}.txt`;
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
    return mode === 'STRATEGY' ? '⚡ Strategy Engine' : '🧠 AI Agent';
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
