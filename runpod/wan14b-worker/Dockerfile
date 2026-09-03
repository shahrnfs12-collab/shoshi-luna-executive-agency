FROM runpod/pytorch:1.0.2-cu1281-torch280-ubuntu2404

ENV PYTHONUNBUFFERED=1 \
    HF_HOME=/runpod-volume/huggingface \
    MODEL_ID=Wan-AI/Wan2.2-I2V-A14B-Diffusers \
    MAX_SECONDS=10 \
    OUTPUT_FPS=16

WORKDIR /app
COPY requirements.txt .
RUN python -m pip install --no-cache-dir -r requirements.txt
COPY handler.py .
CMD ["python", "-u", "handler.py"]
