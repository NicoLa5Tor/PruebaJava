import { Injectable, signal } from '@angular/core';

export interface RuntimeConfig {
  /** Cloudflare Turnstile site key; null disables the widget. */
  turnstileSiteKey: string | null;
}

/**
 * Settings the SSR server publishes at `/app-config.json` from its environment variables, so the same Docker
 * image runs in every environment without rebuilding.
 */
@Injectable({ providedIn: 'root' })
export class RuntimeConfigService {
  private readonly config = signal<RuntimeConfig>({ turnstileSiteKey: null });

  readonly turnstileSiteKey = () => this.config().turnstileSiteKey;

  async load(): Promise<void> {
    try {
      const response = await fetch('/app-config.json', { cache: 'no-store' });
      if (response.ok) {
        this.config.set({ turnstileSiteKey: null, ...((await response.json()) as Partial<RuntimeConfig>) });
      }
    } catch {
      // `ng serve` without the SSR server has no config endpoint: keep the defaults.
    }
  }
}
