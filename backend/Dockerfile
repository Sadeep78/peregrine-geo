FROM node:22-alpine AS builder

WORKDIR /app
COPY package*.json ./
COPY protocol/package*.json ./protocol/
COPY backend/package*.json ./backend/

RUN npm install --workspace=protocol --workspace=backend

COPY protocol/ ./protocol/
COPY backend/ ./backend/

RUN npm run build --workspace=protocol
RUN npm run build --workspace=backend

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app /app

EXPOSE 4000
CMD ["npm", "run", "start", "--workspace=backend"]
