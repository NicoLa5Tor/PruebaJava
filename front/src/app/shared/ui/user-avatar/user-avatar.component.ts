import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

@Component({
  selector: 'app-user-avatar',
  template: `
    @if (avatarUrl() && !imageFailed()) {
      <img
        class="rounded-circle object-fit-cover"
        [src]="avatarUrl()"
        [width]="size()"
        [height]="size()"
        alt=""
        referrerpolicy="no-referrer"
        (error)="imageFailed.set(true)"
      />
    } @else {
      <span
        class="rounded-circle bg-primary-subtle text-primary-emphasis fw-semibold d-inline-flex align-items-center justify-content-center"
        [style.width.px]="size()"
        [style.height.px]="size()"
        [style.font-size.px]="size() * 0.4"
        aria-hidden="true"
        >{{ initials() }}</span
      >
    }
  `,
  host: { class: 'd-inline-flex flex-shrink-0' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserAvatarComponent {
  readonly name = input.required<string>();
  readonly avatarUrl = input<string | null>(null);
  readonly size = input(40);
  protected readonly imageFailed = signal(false);
  protected readonly initials = computed(() => {
    const words = this.name().split(/[\s@._-]+/).filter(Boolean);
    return (words.length > 1 ? words[0][0] + words[1][0] : (words[0] ?? '?').slice(0, 2)).toUpperCase();
  });
}
