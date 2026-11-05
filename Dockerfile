# Build de producción del SPA servido por nginx (S13).
# Antes: npx ng build --configuration production  (genera dist/bomerp-frontend/browser)
FROM nginx:1.27-alpine
COPY dist/bomerp-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
