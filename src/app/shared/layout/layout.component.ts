import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule, MatButtonModule, MatIconModule],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.scss',
})
export class LayoutComponent {
  readonly auth = inject(AuthService);
  private readonly breakpoints = inject(BreakpointObserver);

  /** Sidenav as overlay on small screens, fixed sidebar on desktop. */
  readonly isMobile = toSignal(this.breakpoints.observe([Breakpoints.Handset, Breakpoints.TabletPortrait]), {
    initialValue: { matches: false, breakpoints: {} },
  });

  readonly drawer = signal(false);
}
