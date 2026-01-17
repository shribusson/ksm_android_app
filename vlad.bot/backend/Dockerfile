FROM python:3.12-slim

WORKDIR /app

# Install system dependencies
# Install system dependencies (Skipped to avoid network hang - trying pure wheels)
# RUN apt-get update && apt-get install -y --no-install-recommends ...

# Install python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Environment variables
ENV PYTHONPATH=/app
