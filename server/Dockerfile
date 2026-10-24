# Groma server (API + worker). Build from the repository root:
#   docker build -f server/Dockerfile -t groma-server .
#
# CPU image: API, and a worker for the depth-fusion reconstruction and
# COLMAP without its dense step. The GPU worker (COLMAP dense, MapAnything,
# Prompt Depth Anything, SAM 2) has its own image: server/Dockerfile.gpu.
ARG BASE=python:3.12-slim-bookworm
FROM ${BASE}

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 GROMA_DONNEES=/var/lib/groma

RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 python3-pip python3-venv colmap libgl1 libglib2.0-0 \
 && rm -rf /var/lib/apt/lists/*

RUN python3 -m venv /opt/venv
ENV PATH=/opt/venv/bin:$PATH

WORKDIR /app
COPY core /app/core
COPY server /app/server
RUN pip install --no-cache-dir "/app/core[geo]" "/app/server[s3]"

RUN useradd --system --create-home groma && mkdir -p /var/lib/groma && chown groma /var/lib/groma
USER groma
VOLUME ["/var/lib/groma"]
EXPOSE 8000

CMD ["uvicorn", "groma_server.api:creer_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
