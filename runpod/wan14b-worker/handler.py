import base64
import io
import os
import tempfile
import threading
from pathlib import Path

import runpod
import torch
from diffusers import WanImageToVideoPipeline
from diffusers.utils import export_to_video
from PIL import Image

MODEL_ID = os.getenv("MODEL_ID", "Wan-AI/Wan2.2-I2V-A14B-Diffusers")
MAX_SECONDS = min(int(os.getenv("MAX_SECONDS", "10")), 10)
OUTPUT_FPS = int(os.getenv("OUTPUT_FPS", "16"))
_PIPELINE = None
_LOAD_LOCK = threading.Lock()


def _pipeline():
    global _PIPELINE
    if _PIPELINE is None:
        with _LOAD_LOCK:
            if _PIPELINE is None:
                token = os.getenv("HF_TOKEN")
                if not token:
                    raise RuntimeError("HF_TOKEN is not configured")
                pipe = WanImageToVideoPipeline.from_pretrained(
                    MODEL_ID,
                    torch_dtype=torch.bfloat16,
                    token=token,
                    low_cpu_mem_usage=True,
                )
                pipe.enable_model_cpu_offload()
                pipe.vae.enable_tiling()
                pipe.vae.enable_slicing()
                _PIPELINE = pipe
    return _PIPELINE


def _decode_image(value):
    if not isinstance(value, str) or not value:
        raise ValueError("image_base64 is required")
    if "," in value and value.startswith("data:"):
        value = value.split(",", 1)[1]
    raw = base64.b64decode(value, validate=True)
    if len(raw) > 8 * 1024 * 1024:
        raise ValueError("image exceeds 8MB")
    image = Image.open(io.BytesIO(raw)).convert("RGB")
    image.thumbnail((1280, 1280), Image.Resampling.LANCZOS)
    return image


def handler(job):
    data = job.get("input") or {}
    prompt = str(data.get("prompt") or "").strip()
    if not prompt or len(prompt) > 10000:
        return {"error": "invalid_prompt"}
    seconds = int(data.get("seconds", 10))
    if seconds < 1 or seconds > MAX_SECONDS:
        return {"error": "duration_limit", "max_seconds": MAX_SECONDS}

    image = _decode_image(data.get("image_base64"))
    negative = str(data.get("negative_prompt") or (
        "identity drift, face distortion, extra fingers, malformed hands, "
        "duplicate people, flicker, temporal inconsistency, text, watermark"
    ))[:3000]
    seed = int(data.get("seed", 20260903))
    frames = seconds * OUTPUT_FPS + 1

    generator = torch.Generator(device="cpu").manual_seed(seed)
    result = _pipeline()(
        image=image,
        prompt=prompt,
        negative_prompt=negative,
        num_frames=frames,
        num_inference_steps=40,
        guidance_scale=5.0,
        generator=generator,
    )

    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "wan14b.mp4"
        export_to_video(result.frames[0], str(path), fps=OUTPUT_FPS)
        encoded = base64.b64encode(path.read_bytes()).decode("ascii")

    return {
        "ok": True,
        "model": MODEL_ID,
        "seconds": seconds,
        "fps": OUTPUT_FPS,
        "seed": seed,
        "video_base64": encoded,
        "content_type": "video/mp4",
    }


runpod.serverless.start({"handler": handler})
