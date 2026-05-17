FROM python:3.12-slim

WORKDIR /app

COPY requirements-docker.txt .
RUN pip install --no-cache-dir \
    -i https://pypi.tuna.tsinghua.edu.cn/simple \
    --trusted-host pypi.tuna.tsinghua.edu.cn \
    -r requirements-docker.txt

COPY app/ ./app/
COPY scripts/ ./scripts/

RUN mkdir -p /app/models_store

ENV PYTHONUNBUFFERED=1
ENV PREDICTION_DB_HOST=mysql.orange-logistics.svc.cluster.local
ENV PREDICTION_DB_PORT=3306
ENV PREDICTION_DB_NAME=orange_logistics
ENV PREDICTION_DB_USER=root
ENV PREDICTION_DB_PASSWORD=orange…2024

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "2"]
