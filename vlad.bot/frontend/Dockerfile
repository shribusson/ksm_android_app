FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json* ./

# Install dependencies if package.json exists, otherwise just echo
RUN if [ -f package.json ]; then npm install; else echo "No package.json yet"; fi

COPY . .

EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host"]
