FROM python:3.13-slim-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir uv

WORKDIR /app
COPY pom.xml ./
COPY src/ ./src/
RUN mvn -B compile dependency:copy-dependencies -DoutputDirectory=target/dependency

COPY backend/ ./backend/
WORKDIR /app/backend
RUN uv sync --locked --no-dev

CMD [".venv/bin/uvicorn", "app_with_checks:app", "--host", "0.0.0.0", "--port", "8000"]
