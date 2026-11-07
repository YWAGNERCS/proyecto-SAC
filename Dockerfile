# Build de producción del SPA servido por nginx (S13).
# Antes: npm run build:prod  (compila en dist/bomerp-frontend/browser y verifica las huellas de la CSP)
FROM nginx:1.27-alpine
COPY dist/bomerp-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Cabeceras de seguridad (auditoría M11): fuera de conf.d para que nginx no las cargue como un sitio aparte
COPY cabeceras-seguridad.conf /etc/nginx/snippets/cabeceras-seguridad.conf
EXPOSE 80
