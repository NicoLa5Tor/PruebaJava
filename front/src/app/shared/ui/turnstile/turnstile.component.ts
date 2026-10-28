import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';

interface TurnstileApi {
  render(container: HTMLElement, options: Record<string, unknown>): string;
  reset(widgetId: string): void;
  remove(widgetId: string): void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

const SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
let scriptLoading: Promise<TurnstileApi> | null = null;

function loadTurnstile(): Promise<TurnstileApi> {
  scriptLoading ??= new Promise<TurnstileApi>((resolve, reject) => {
    if (window.turnstile) {
      resolve(window.turnstile);
      return;
    }
    const script = document.createElement('script');
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () => (window.turnstile ? resolve(window.turnstile) : reject(new Error('Turnstile missing')));
    script.onerror = () => {
      scriptLoading = null;
      reject(new Error('Turnstile could not be loaded'));
    };
    document.head.appendChild(script);
  });
  return scriptLoading;
}

/** Cloudflare Turnstile challenge; emits a one-time token the backend verifies before signing in. */
@Component({
  selector: 'app-turnstile',
  template: '<div #container></div>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TurnstileComponent {
  readonly siteKey = input.required<string>();
  /** Emits the token when solved and null when it expires or fails. */
  readonly tokenChange = output<string | null>();

  private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
  private api: TurnstileApi | null = null;
  private widgetId: string | null = null;

  constructor() {
    afterNextRender(() => {
      loadTurnstile()
        .then((api) => {
          this.api = api;
          this.widgetId = api.render(this.container().nativeElement, {
            sitekey: this.siteKey(),
            language: 'es',
            callback: (token: string) => this.tokenChange.emit(token),
            'expired-callback': () => this.tokenChange.emit(null),
            'error-callback': () => this.tokenChange.emit(null),
          });
        })
        .catch(() => this.tokenChange.emit(null));
    });
    inject(DestroyRef).onDestroy(() => {
      if (this.api && this.widgetId) {
        this.api.remove(this.widgetId);
      }
    });
  }

  /** Tokens are single-use: call after every submit so the next attempt gets a fresh one. */
  reset(): void {
    if (this.api && this.widgetId) {
      this.api.reset(this.widgetId);
      this.tokenChange.emit(null);
    }
  }
}
