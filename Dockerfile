# Stage 1 – install all deps (including dev for build)
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install

# Stage 2 – build frontend assets
FROM deps AS build
COPY . .
RUN npm run build

# Stage 3 – prune to production dependencies
FROM deps AS prod-deps
RUN npm prune --omit=dev

# Stage 4 – runtime image
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=8090
COPY --from=prod-deps /app/node_modules ./node_modules
COPY --from=build /app/package.json ./package.json
COPY --from=build /app/server ./server
COPY --from=build /app/shared ./shared
COPY --from=build /app/dist ./dist
COPY --from=build /app/keys ./keys
COPY --from=build /app/generate_keys.js ./generate_keys.js
EXPOSE 8090
CMD ["node", "server/index.js"]