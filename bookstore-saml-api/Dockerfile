FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY src ./src
COPY data ./data
COPY certs ./certs
EXPOSE 3033
CMD ["node", "src/server.js"]
