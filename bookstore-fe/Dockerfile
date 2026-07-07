# Stage build: CRA nhúng biến REACT_APP_* từ .env vào bundle tại thời điểm build,
# nên .env phải có mặt trong build context (khác razor-app/bookstore-api nhận env lúc runtime).
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
# npm 10.x trong node:20-alpine có bug bắt lỗi optional peer dep (yaml) khi npm ci
RUN npm install -g npm@11 && npm ci
COPY . .
RUN npm run build

# Stage runtime: chỉ serve static build (SPA fallback về index.html nhờ serve -s)
FROM node:20-alpine
WORKDIR /app
RUN npm install -g serve@14
COPY --from=build /app/build ./build
EXPOSE 3002
CMD ["serve", "-s", "build", "-l", "3002"]
