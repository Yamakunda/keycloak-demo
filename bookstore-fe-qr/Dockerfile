# Stage build: CRA nhúng biến REACT_APP_* từ .env vào bundle tại thời điểm build.
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install -g npm@11 && npm install
COPY . .
RUN npm run build

# Stage runtime: chỉ serve static build (SPA fallback về index.html nhờ serve -s)
FROM node:20-alpine
WORKDIR /app
RUN npm install -g serve@14
COPY --from=build /app/build ./build
EXPOSE 3052
CMD ["serve", "-s", "build", "-l", "3052"]
