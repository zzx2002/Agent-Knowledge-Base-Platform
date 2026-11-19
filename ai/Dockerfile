FROM python:3.11-slim
ARG SERVICE=embedding
WORKDIR /app
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/app \
    TZ=Asia/Shanghai \
    SERVICE=${SERVICE}

COPY ai/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY ai ./ai
EXPOSE 8001 8002 8003
CMD sh -c "exec python -m ai.${SERVICE}.app"
