# --- Etapa de build ---
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build -- --configuration production

# --- Etapa de servido ---
FROM nginx:1.27-alpine
COPY --from=build /app/dist/ar-connector-panel/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
