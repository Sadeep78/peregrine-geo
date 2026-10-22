FROM node:22-alpine AS builder

WORKDIR /app
COPY package*.json ./
COPY protocol/package*.json ./protocol/
COPY web/package*.json ./web/

RUN npm install --workspace=protocol --workspace=web

COPY protocol/ ./protocol/
COPY web/ ./web/

RUN npm run build --workspace=protocol
RUN npm run build --workspace=web

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app /app

EXPOSE 3000
CMD ["npm", "run", "start", "--workspace=web"]
