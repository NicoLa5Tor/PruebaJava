import { Injectable, inject, signal } from '@angular/core';
import { Observable, repeat, retry, share, timer } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api.config';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { LoanStatusChanged } from './loan.models';

const MAX_RECONNECT_DELAY_MS = 30_000;

class StreamUnauthorizedError extends Error {}

/**
 * Live status updates from `GET /loans/me/events` (server-sent events).
 *
 * Uses fetch instead of EventSource because EventSource cannot send the Authorization header, and the access
 * token must stay out of URLs and cookies. The stream reconnects when the server closes it, refreshes the
 * access token on 401, and backs off exponentially on network errors.
 */
@Injectable({ providedIn: 'root' })
export class LoanEventsService {
  private readonly session = inject(AuthSessionService);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly connectedState = signal(false);

  /** True while the stream is open, so the page can tell the user that updates arrive live. */
  readonly connected = this.connectedState.asReadonly();

  /**
   * One connection shared by every subscriber (the loans page and the notification bell); it opens with the
   * first subscriber and closes when the last one leaves.
   */
  private readonly changes = this.connect();

  statusChanges(): Observable<LoanStatusChanged> {
    return this.changes;
  }

  private connect(): Observable<LoanStatusChanged> {
    return this.connectOnce().pipe(
      repeat({ delay: 1_000 }),
      retry({
        resetOnSuccess: true,
        delay: (error: unknown, attempt) =>
          error instanceof StreamUnauthorizedError
            ? this.session.refresh()
            : timer(Math.min(MAX_RECONNECT_DELAY_MS, 1_000 * 2 ** attempt)),
      }),
      share(),
    );
  }

  private connectOnce(): Observable<LoanStatusChanged> {
    return new Observable<LoanStatusChanged>((subscriber) => {
      const controller = new AbortController();
      const read = async () => {
        const response = await fetch(`${this.baseUrl}/loans/me/events`, {
          headers: { Accept: 'text/event-stream', Authorization: `Bearer ${this.session.accessToken() ?? ''}` },
          signal: controller.signal,
        });
        if (response.status === 401) {
          throw new StreamUnauthorizedError('The access token expired.');
        }
        if (!response.ok || !response.body) {
          throw new Error(`The event stream answered ${response.status}.`);
        }
        this.connectedState.set(true);
        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
        let buffer = '';
        for (;;) {
          const { value, done } = await reader.read();
          if (done) {
            break;
          }
          buffer += value;
          const blocks = buffer.split(/\r?\n\r?\n/);
          buffer = blocks.pop() ?? '';
          for (const block of blocks) {
            const message = parseServerSentEvent(block);
            if (message?.event === 'loan-status') {
              subscriber.next(JSON.parse(message.data) as LoanStatusChanged);
            }
          }
        }
        this.connectedState.set(false);
        subscriber.complete();
      };
      read().catch((error: unknown) => {
        this.connectedState.set(false);
        if (!controller.signal.aborted) {
          subscriber.error(error);
        }
      });
      return () => {
        this.connectedState.set(false);
        controller.abort();
      };
    });
  }
}

/** Parses one event block of the `text/event-stream` format; comment-only blocks (heartbeats) return null. */
export function parseServerSentEvent(block: string): { event: string; data: string } | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith(':')) {
      continue;
    }
    const separator = line.indexOf(':');
    const field = separator === -1 ? line : line.slice(0, separator);
    const value = separator === -1 ? '' : line.slice(separator + 1).replace(/^ /, '');
    if (field === 'event') {
      event = value;
    } else if (field === 'data') {
      data.push(value);
    }
  }
  return data.length ? { event, data: data.join('\n') } : null;
}
