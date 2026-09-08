# =============================================================================
# Web de WattWise + proxy inverso — nginx:alpine.
#
# El contexto de build es la RAÍZ DEL MONOREPO (docker-compose.yml `build.context: ..`),
# por lo que copiamos ../web (sitio estático) y la configuración del proxy inverso
# docker/nginx/nginx.conf + docker/nginx/default.conf desde este directorio de build.
# =============================================================================

FROM nginx:1.27-alpine

# Configuración del proxy inverso (includes remotos, etc.)
COPY docker/nginx/nginx.conf /etc/nginx/nginx.conf

# Server blocks: sirve los archivos estáticos de ../web/ y proxifica /api + /actuator.
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf

# Recursos web estáticos desde el contexto de build de la raíz del monorepo.
COPY web/ /usr/share/nginx/html/

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]