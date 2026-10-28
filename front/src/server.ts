import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import type { IncomingMessage } from 'node:http';
import { join } from 'node:path';
import { createProxyMiddleware } from 'http-proxy-middleware';

/**
 * Backend-for-frontend: this Node server is the only public entry point. It renders the Angular app and
 * forwards `/api/v1/**` to the Spring Boot API over the private Docker network, so the API is never
 * published to the internet and the browser only ever talks to one origin (cookies stay first-party).
 */
const backendUrl = process.env['BACKEND_URL'] ?? 'http://localhost:8080';
const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
app.disable('x-powered-by');
const angularApp = new AngularNodeAppEngine();

app.use((_req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
  next();
});

/** Liveness probe for Docker and nginx. */
app.get('/healthz', (_req, res) => {
  res.json({ status: 'UP' });
});

/** Settings read at runtime, so one image serves every environment (e.g. the Turnstile site key). */
app.get('/app-config.json', (_req, res) => {
  res.setHeader('Cache-Control', 'no-store');
  res.json({ turnstileSiteKey: process.env['TURNSTILE_SITE_KEY'] || null });
});

app.use(
  createProxyMiddleware({
    target: backendUrl,
    pathFilter: '/api/v1',
    // Keep the public Host so the API builds correct Location headers; xfwd adds X-Forwarded-Proto/For.
    changeOrigin: false,
    xfwd: true,
    // Server-sent event streams stay open for up to 30 minutes on the backend.
    proxyTimeout: 31 * 60 * 1000,
    timeout: 31 * 60 * 1000,
    on: {
      proxyReq: (proxyRequest, request: IncomingMessage) => {
        proxyRequest.setHeader('X-Real-IP', clientIp(request));
      },
      error: (_error, _request, response) => {
        if ('writeHead' in response && !response.headersSent) {
          response.writeHead(502, { 'Content-Type': 'application/problem+json' });
          response.end(
            JSON.stringify({ title: 'Bad gateway', status: 502, detail: 'The API is temporarily unavailable.' }),
          );
        }
      },
    },
  }),
);

/** Static assets have hashed names, so they can be cached for a year. */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) => (response ? writeResponseToNodeResponse(response, res) : next()))
    .catch(next);
});

/** Cloudflare puts the visitor's address in CF-Connecting-IP; nginx forwards it untouched. */
function clientIp(request: IncomingMessage): string {
  const cloudflare = request.headers['cf-connecting-ip'];
  if (typeof cloudflare === 'string' && cloudflare) {
    return cloudflare;
  }
  const forwarded = request.headers['x-real-ip'];
  if (typeof forwarded === 'string' && forwarded) {
    return forwarded;
  }
  return request.socket.remoteAddress ?? 'unknown';
}

if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }
    console.log(`SSR server listening on http://localhost:${port}, proxying /api/v1 to ${backendUrl}`);
  });
}

/** Request handler used by the Angular CLI (for dev-server and during build). */
export const reqHandler = createNodeRequestHandler(app);
