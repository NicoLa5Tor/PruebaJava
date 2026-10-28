import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * The public login page is rendered on the server (fast first paint, no blank screen). Everything behind
 * authentication renders in the browser: the session lives in memory there and the server never sees it.
 */
export const serverRoutes: ServerRoute[] = [
  { path: 'login', renderMode: RenderMode.Server },
  { path: '**', renderMode: RenderMode.Client },
];
