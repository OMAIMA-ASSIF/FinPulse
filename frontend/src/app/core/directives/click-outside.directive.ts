import {
  Directive, ElementRef, EventEmitter, HostListener, Output
} from '@angular/core';

/**
 * Directive réutilisable — émet (clickOutside) quand l'utilisateur clique
 * en dehors de l'élément host.
 *
 * Usage :
 *   <div (clickOutside)="closeMenu()">…</div>
 */
@Directive({
  selector: '[clickOutside]',
  standalone: true
})
export class ClickOutsideDirective {
  @Output() clickOutside = new EventEmitter<void>();

  constructor(private el: ElementRef) {}

  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: EventTarget | null): void {
    if (target instanceof HTMLElement && !this.el.nativeElement.contains(target)) {
      this.clickOutside.emit();
    }
  }
}
