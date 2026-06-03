import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';


@Component({
  selector: 'app-guide',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './guide.component.html',
  styleUrls: ['./guide.component.css']
})
export class GuideComponent {
  terms = [
    {
      term: 'NCI (Narrative Consistency Index)',
      definition: 'Indice mesurant la cohérence du narratif d\'une entreprise à travers ses communications. Score de 0 à 1, où 1 = narration très cohérente.',
      formula: 'Basé sur l\'analyse sémantique des rapports financiers, communiqués de presse et news.'
    },
    {
      term: 'Sentiment',
      definition: 'Score d\'opinion extrait des news et analyses financières via FinBERT. Range de -1 (très négatif) à +1 (très positif).',
      formula: 'Moyenne pondérée des sentiments des articles récents.'
    },
    {
      term: 'Risk Level',
      definition: 'Classification du risque basée sur le NCI et d\'autres facteurs. LOW_RISK, MEDIUM_RISK, HIGH_RISK.',
      formula: 'Dérivé du NCI: NCI < 0.4 = HIGH_RISK, 0.4-0.7 = MEDIUM_RISK, > 0.7 = LOW_RISK.'
    },
    {
      term: 'Volatility',
      definition: 'Mesure de la variabilité du NCI dans le temps. Indique la stabilité du narratif.',
      formula: 'Écart-type des variations du NCI sur les 12 derniers mois.'
    },
    {
      term: 'Sector Threshold',
      definition: 'Seuil de référence pour le secteur d\'activité. Utilisé pour détecter les anomalies.',
      formula: 'Moyenne sectorielle des NCI ou des scores d\'anomalie.'
    },
    {
      term: 'Anomaly Score',
      definition: 'Score indiquant à quel point un paragraphe s\'écarte de la norme sectorielle.',
      formula: 'Distance vectorielle entre l\'embedding du paragraphe et la moyenne sectorielle.'
    },
    {
      term: 'Strategy',
      definition: 'Thèse d\'investissement personnalisée basée sur votre profil et les données NCI.',
      formula: 'Combinaison de votre argument utilisateur, du NCI personnalisé et des facteurs de risque.'
    },
    {
      term: 'Personalized NCI',
      definition: 'NCI ajusté selon votre profil d\'investisseur (PRUDENT ou SPÉCULATEUR).',
      formula: 'NCI Global × 1.0 (PRUDENT) ou × 1.15 (SPÉCULATEUR).'
    }
  ];
}
