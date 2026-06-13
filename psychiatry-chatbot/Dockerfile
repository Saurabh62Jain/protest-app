FROM python:3.10-slim

WORKDIR /app

# Install build dependencies for compiling package components if required
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Copy dependencies list
COPY requirements.txt .

# Install dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code and vector storage index
COPY psychiatry_api.py .
COPY connect_memory_with_llm.py .
COPY vectorstore ./vectorstore

EXPOSE 5001

ENTRYPOINT ["python", "-u", "psychiatry_api.py"]
