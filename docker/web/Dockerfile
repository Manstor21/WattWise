# =============================================================================
# WattWise web + reverse proxy — nginx:alpine.
#
# Build context is the MONOREPO ROOT (docker-compose.yml `build.context: ..`),
# so we COPY ../web (static site) and the reverse-proxy config from
# docker/nginx/nginx.conf + docker/nginx/default.conf from this image build dir.
# =============================================================================

FROM nginx:1.27-alpine

# Reverse proxy config (remote includes, etc.)
COPY docker/nginx/nginx.conf /etc/nginx/nginx.conf

# Server blocks: serves ../web/ static files and proxies /api + /actuator.
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf

# Static web assets from the monorepo root build context.
COPY web/ /usr/share/nginx/html/

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]