import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LoanEventsService } from '../../loans/data-access/loan-events.service';
import { AppNotification, NotificationsStore } from '../data-access/notifications.store';

/** Bell with unread counter and a dropdown inbox; updates live when an admin reviews one of the user's loans. */
@Component({
  selector: 'app-notification-bell',
  imports: [DatePipe],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationBellComponent {
  protected readonly store = inject(NotificationsStore);
  private readonly host = inject(ElementRef<HTMLElement>);
  protected readonly open = signal(false);
  protected readonly ring = signal(false);

  constructor() {
    void this.store.load();
    inject(LoanEventsService)
      .statusChanges()
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        void this.store.load();
        this.ring.set(true);
        setTimeout(() => this.ring.set(false), 1_200);
      });
  }

  protected toggle(): void {
    this.open.update((value) => !value);
  }

  protected read(item: AppNotification): void {
    if (!item.read) {
      void this.store.markRead(item.id);
    }
  }

  @HostListener('document:click', ['$event'])
  protected closeOnOutsideClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    this.open.set(false);
  }
}
