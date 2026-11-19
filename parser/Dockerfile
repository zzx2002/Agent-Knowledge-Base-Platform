FROM python:3.11-slim
WORKDIR /app
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/app \
    TZ=Asia/Shanghai

RUN apt-get update \
    && apt-get install -y --no-install-recommends libreoffice tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

COPY parser/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY parser ./parser
EXPOSE 5001
CMD ["python", "-m", "parser.pipeline.worker"]
