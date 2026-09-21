# Shoshi & Luna WAN 2.2 I2V-A14B Worker

RunPod Serverless worker for one guarded owner test and later customer jobs.

## Contract

Input:
- `prompt`: required
- `image_base64`: required JPEG, PNG, or WebP payload
- `seconds`: 1–10, default 10
- `seed`: optional

Output:
- MP4 as `video_base64`
- model, duration, FPS, and seed metadata

## Required endpoint secret

- `HF_TOKEN`: Hugging Face read token

## Hardware

Use H100 80GB as primary. Configure A100 80GB and H200 as compatible fallbacks. Keep active workers at zero until quality and cost are measured.

## Cost controls

The worker enforces a 10-second maximum. The application must additionally enforce one active owner test, a $1.50 estimated ceiling before submission, job timeout, and automatic scale-to-zero.

No API keys belong in this repository.
