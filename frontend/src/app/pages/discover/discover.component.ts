import {
  Component, OnInit, OnDestroy, inject, signal,
  computed, HostListener, ViewChild, ElementRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { WatchlistService } from '../../services/watchlist.service';
import { Company } from '../../models';

export interface OnboardingPrefs {
  sectors:     string[];
  horizon:     'SHORT' | 'MEDIUM' | 'LONG';
  riskProfile: 'PRUDENT' | 'SPECULATEUR';
}

type DiscoverView = 'onboarding' | 'recommendations' | 'explore';

@Component({
  selector: 'app-discover',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './discover.component.html',
  styleUrls: ['./discover.component.css']
})
export class DiscoverComponent implements OnInit, OnDestroy {
  @ViewChild('exploreSection') exploreRef!: ElementRef;

  private readonly api         = inject(ApiService);
  private readonly router      = inject(Router);
  readonly auth                = inject(AuthService);
  readonly watchlistSvc        = inject(WatchlistService);

  // ── Vue courante ───────────────────────────────────────────────────────────
  view = signal<DiscoverView>('explore');

  // ── Onboarding ─────────────────────────────────────────────────────────────
  readonly availableSectors = [
    'Technology', 'Finance', 'Automotive', 'Social Media',
    'Entertainment', 'E-Commerce', 'Semiconductors', 'Healthcare'
  ];
  prefs: OnboardingPrefs = { sectors: [], horizon: 'MEDIUM', riskProfile: 'PRUDENT' };
  onboardingStep   = signal<1 | 2 | 3>(1);
  onboardingSaving = signal(false);

  // ── Données ────────────────────────────────────────────────────────────────
  allCompanies   = signal<Company[]>([]);
  loading        = signal(true);
  pinningId      = signal<number | null>(null);

  // ── Explore filters ────────────────────────────────────────────────────────
  searchQuery    = signal('');
  selectedSector = signal<string>('ALL');
  sortMode       = signal<'nci' | 'sentiment'>('nci');
  displayedCount = signal(12);
  private readonly PAGE_SIZE = 12;
  private readonly search$   = new Subject<string>();

  // ── Sections explore ───────────────────────────────────────────────────────
  readonly sectors = computed(() => {
    const s = new Set(this.allCompanies().map(c => c.sector).filter(Boolean));
    return ['ALL', ...Array.from(s).sort()];
  });

  readonly baseFiltered = computed(() => {
    let list = this.allCompanies();
    const q   = this.searchQuery().trim().toLowerCase();
    const sec = this.selectedSector();
    if (q)         list = list.filter(c => c.name.toLowerCase().includes(q) || c.ticker.toLowerCase().includes(q));
    if (sec !== 'ALL') list = list.filter(c => c.sector === sec);
    return [...list].sort((a, b) =>
      this.sortMode() === 'nci' ? b.nciGlobal - a.nciGlobal : b.sentimentAvg - a.sentimentAvg
    );
  });

  readonly filtered = computed(() => this.baseFiltered().slice(0, this.displayedCount()));
  readonly hasMore  = computed(() => this.displayedCount() < this.baseFiltered().length);

  readonly topRisky = computed(() =>
    [...this.allCompanies()].filter(c => c.nciLabel === 'LOW')
      .sort((a, b) => a.nciGlobal - b.nciGlobal).slice(0, 6)
  );
  readonly mostConsistent = computed(() =>
    [...this.allCompanies()].filter(c => c.nciLabel === 'HIGH')
      .sort((a, b) => b.nciGlobal - a.nciGlobal).slice(0, 6)
  );
  readonly biggestJumps = computed(() =>
    [...this.allCompanies()]
      .sort((a, b) => Math.abs(b.nciGlobal - 0.5) - Math.abs(a.nciGlobal - 0.5))
      .slice(0, 6)
  );
  readonly bySector = computed(() => {
    const map = new Map<string, Company[]>();
    this.allCompanies().forEach(c => {
      if (!map.has(c.sector)) map.set(c.sector, []);
      map.get(c.sector)!.push(c);
    });
    return Array.from(map.entries()).slice(0, 3);
  });

  // ── Recommandations (après onboarding) ────────────────────────────────────
  recommendations = signal<Company[]>([]);

  ngOnInit(): void {
    this.api.getCompanies().subscribe({
      next: list => { this.allCompanies.set(list); this.loading.set(false); this.checkFirstLogin(); },
      error: () => this.loading.set(false)
    });

    this.search$.pipe(debounceTime(300)).subscribe(q => {
      this.searchQuery.set(q);
      this.displayedCount.set(this.PAGE_SIZE);
    });
  }

  ngOnDestroy(): void {}

  // ── First login detection ──────────────────────────────────────────────────
  private checkFirstLogin(): void {
    if (this.auth.isFirstLogin()) {
      this.view.set('onboarding');
    }
  }

  // ── Infinite scroll ────────────────────────────────────────────────────────
  @HostListener('window:scroll')
  onScroll(): void {
    if (!this.hasMore() || this.view() !== 'explore') return;
    const el = this.exploreRef?.nativeElement;
    if (!el) return;
    if (el.getBoundingClientRect().bottom <= window.innerHeight + 300) {
      this.displayedCount.update(c => c + this.PAGE_SIZE);
    }
  }

  onSearchInput(q: string): void { this.search$.next(q); }

  // ── Onboarding ─────────────────────────────────────────────────────────────
  toggleSector(sector: string): void {
    const idx = this.prefs.sectors.indexOf(sector);
    if (idx > -1) this.prefs.sectors.splice(idx, 1);
    else          this.prefs.sectors.push(sector);
  }
  isSectorSelected(s: string): boolean { return this.prefs.sectors.includes(s); }
  nextStep(): void { if (this.onboardingStep() < 3) this.onboardingStep.update(s => (s + 1) as 1|2|3); }
  prevStep(): void { if (this.onboardingStep() > 1) this.onboardingStep.update(s => (s - 1) as 1|2|3); }

  async finishOnboarding(): Promise<void> {
    this.onboardingSaving.set(true);

    // 1. Mettre à jour le profil si besoin
    if (this.prefs.riskProfile !== this.auth.profileType()) {
      await this.auth.updateProfileType(this.prefs.riskProfile).toPromise();
      this.auth._profile.update(p => p ? { ...p, profileType: this.prefs.riskProfile } : p);
    }

    // 2. Marquer firstLogin = false en DB — CRITIQUE
    await this.auth.markFirstLoginDone().toPromise();
    this.auth._profile.update(p => p ? { ...p, firstLogin: false } : p);

    // 3. Calculer les recommandations sans créer de watchlist automatique
    const recs = this.buildRecommendations();
    this.recommendations.set(recs);

    this.onboardingSaving.set(false);

    // 4. Passer à la vue "recommandations" (même page, contenu change)
    this.view.set('recommendations');
  }

  skipOnboarding(): void {
    // Marquer firstLogin = false pour ne plus revoir l'onboarding
    this.auth.markFirstLoginDone().subscribe();
    this.auth._profile.update(p => p ? { ...p, firstLogin: false } : p);
    this.view.set('explore');
  }

  goToExplore(): void { this.view.set('explore'); }

  /**
   * Génère des recommandations basées sur les préférences.
   * Logique : filtre par secteur → trie selon profil risque + horizon.
   * AUCUNE watchlist automatique — l'utilisateur choisit.
   */
  private buildRecommendations(): Company[] {
    let candidates = [...this.allCompanies()];

    // Filtrer par secteurs sélectionnés
    if (this.prefs.sectors.length > 0) {
      candidates = candidates.filter(c => this.prefs.sectors.includes(c.sector));
    }

    // Scoring selon profil + horizon
    candidates = candidates.map(c => ({
      company: c,
      score: this.computeRecommendationScore(c)
    }))
    .sort((a, b) => b.score - a.score)
    .map(x => x.company);

    return candidates.slice(0, 9);
  }

  private computeRecommendationScore(c: Company): number {
    let score = 0;

    // NCI
    const nci = c.nciGlobal;

    if (this.prefs.riskProfile === 'PRUDENT') {
      // PRUDENT → prefer high NCI (stable narrative)
      score += nci * 60;
      // Penalize low NCI
      if (nci < 0.4) score -= 30;
    } else {
      // SPÉCULATEUR → prefer volatile (NCI around 0.4-0.6)
      const volatility = 1 - Math.abs(nci - 0.5) * 2;
      score += volatility * 40;
      score += nci * 20;
    }

    // Sentiment toujours positif
    score += c.sentimentAvg * 20;

    // Horizon
    if (this.prefs.horizon === 'LONG' && nci >= 0.6)  score += 15;
    if (this.prefs.horizon === 'SHORT' && nci >= 0.4) score += 10;
    if (this.prefs.horizon === 'MEDIUM')              score += 5;

    return score;
  }

  // ── Card actions ────────────────────────────────────────────────────────────
  openCompany(id: number): void { this.router.navigate(['/company', id]); }

  analyzeWithAgent(id: number, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/ai-assistant'], { queryParams: { companyId: id } });
  }

  async pinToWatchlist(company: Company, event: Event): Promise<void> {
    event.stopPropagation();
    if (this.watchlistSvc.isPinned(company.id)) return;
    this.pinningId.set(company.id);
    this.watchlistSvc.pin(company.id).subscribe({
      next: () => this.pinningId.set(null),
      error: () => this.pinningId.set(null)
    });
  }

  isPinned(id: number): boolean  { return this.watchlistSvc.isPinned(id); }
  isPinning(id: number): boolean { return this.pinningId() === id; }

  // ── Helpers ─────────────────────────────────────────────────────────────────
  nciColor(nci: number): string {
    return nci >= 0.7 ? 'var(--green)' : nci >= 0.4 ? 'var(--orange)' : 'var(--red)';
  }
  nciPct(nci: number): number { return Math.round(nci * 100); }
  sentimentLabel(s: number): string { return s >= 0.6 ? 'POSITIVE' : s <= 0.35 ? 'NEGATIVE' : 'NEUTRAL'; }
  sentimentClass(s: number): string { return s >= 0.6 ? 'chip-green' : s <= 0.35 ? 'chip-red' : 'chip-amber'; }
  sectorColor(sector: string): string {
    const m: Record<string, string> = {
      Technology: 'var(--brand)', Finance: 'var(--green)',
      Automotive: 'var(--orange)', 'Social Media': 'var(--cyan)',
      Entertainment: 'var(--amber)', Semiconductors: 'var(--brand)',
      Healthcare: 'var(--green)', 'E-Commerce': 'var(--cyan)'
    };
    return m[sector] ?? 'var(--text-muted)';
  }
  volatilityLabel(nci: number): string {
    const v = Math.abs(nci - 0.5);
    return v > 0.25 ? 'HIGH' : v > 0.1 ? 'MEDIUM' : 'LOW';
  }
  volatilityClass(nci: number): string {
    const v = Math.abs(nci - 0.5);
    return v > 0.25 ? 'chip-red' : v > 0.1 ? 'chip-amber' : 'chip-green';
  }
}
