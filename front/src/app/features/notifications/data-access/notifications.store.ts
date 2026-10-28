import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiClientService } from '../../../core/api/api-client.service';

export type NotificationType = 'LOAN_APPROVED' | 'LOAN_REJECTED';

/** Mirrors the backend NotificationResponse. */
export interface AppNotification {
  id: string;
  loanId: string | null;
  type: NotificationType;
  title: string;
  message: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

/**
 * The signed-in user's inbox. Loaded when the bell appears and refreshed whenever a loan review arrives through
 * the live stream; marking as read is optimistic.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsStore {
  private readonly api = inject(ApiClientService);
  private readonly itemsState = signal<AppNotification[]>([]);
  private readonly loadingState = signal(false);

  readonly items = this.itemsState.asReadonly();
  readonly loading = this.loadingState.asReadonly();
  readonly unreadCount = computed(() => this.itemsState().filter((item) => !item.read).length);

  async load(): Promise<void> {
    this.loadingState.set(true);
    try {
      this.itemsState.set(await firstValueFrom(this.api.get<AppNotification[]>('notifications')));
    } catch {
      // The bell simply stays as it was; the next refresh retries.
    } finally {
      this.loadingState.set(false);
    }
  }

  async markRead(id: string): Promise<void> {
    const previous = this.itemsState();
    this.itemsState.update((items) => items.map((item) => (item.id === id ? { ...item, read: true } : item)));
    try {
      await firstValueFrom(this.api.patch<AppNotification>(`notifications/${id}/read`, null));
    } catch {
      this.itemsState.set(previous);
    }
  }

  async markAllRead(): Promise<void> {
    const previous = this.itemsState();
    this.itemsState.update((items) => items.map((item) => ({ ...item, read: true })));
    try {
      await firstValueFrom(this.api.patch<void>('notifications/read-all', null));
    } catch {
      this.itemsState.set(previous);
    }
  }

  clear(): void {
    this.itemsState.set([]);
  }
}
