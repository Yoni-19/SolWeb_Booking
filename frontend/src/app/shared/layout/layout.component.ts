import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ROL_LABEL } from '../../core/models/cliente.model';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule, MatButtonModule, MatIconModule, MatChipsModule],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent {
  readonly auth = inject(AuthService);
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly router = inject(Router);

  /** Sidenav as overlay on small screens, fixed sidebar on desktop. */
  readonly isMobile = toSignal(this.breakpoints.observe([Breakpoints.Handset, Breakpoints.TabletPortrait]), {
    initialValue: { matches: false, breakpoints: {} },
  });

  readonly drawer = signal(false);

  readonly rolLabel = computed(() => {
    const rol = this.auth.sesion()?.rol;
    return rol ? ROL_LABEL[rol] : '';
  });

  constructor() {
    // On every navigation: scroll the content back to the top and move focus to
    // the page title, so keyboard and screen-reader users land on the new view.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        // Defer one tick so the routed view is already rendered.
        setTimeout(() => {
          window.scrollTo(0, 0);
          document.querySelector('mat-sidenav-content')?.scrollTo({ top: 0 });
          document.querySelector<HTMLElement>('.page-header h1')?.focus({ preventScroll: true });
        });
      });
  }
}
