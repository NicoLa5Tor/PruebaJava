# syntax=docker/dockerfile:1.7
FROM node:24-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY angular.json tsconfig*.json ./
COPY public ./public
COPY src ./src
RUN npx ng build --configuration production

FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production PORT=4000

# The SSR build bundles express and every dependency into server.mjs: no node_modules at runtime.
COPY --from=build /workspace/dist/front ./dist/front

USER node
EXPOSE 4000
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
    CMD wget -qO- http://127.0.0.1:4000/healthz || exit 1
# DEP0169 comes from url.parse() inside http-proxy; it is harmless noise in the logs.
CMD ["node", "--disable-warning=DEP0169", "dist/front/server/server.mjs"]
