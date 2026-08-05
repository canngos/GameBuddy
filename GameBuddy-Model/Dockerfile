# Slim, not the full image: the full python:3.10.9 image carries a compiler toolchain and
# a Debian userland this service never uses, which is both ~700MB of pull time on every
# deploy and a much larger set of packages to patch when a CVE lands.
FROM python:3.12-slim AS base

# Dependencies first, in their own layer, so a code change does not reinstall scikit-learn.
WORKDIR /app
COPY requirements.txt .
# --extra-index-url is what keeps this image from being several gigabytes: the default
# PyPI torch wheel bundles the CUDA runtime, and this service will never see a GPU.
RUN pip install --no-cache-dir -r requirements.txt --extra-index-url https://download.pytorch.org/whl/cpu

COPY gamebuddy_model ./gamebuddy_model
COPY GameBuddy-ModelApi/api ./api

# The trained model itself. MODEL_ARTIFACT below points here, and without this the image
# builds and starts perfectly happily but every /predict returns 503 — the service treats a
# missing artefact as "not ready", which is right, but a container that can never become
# ready is not something you want to discover from the backend's error handler.
#
# Baked in rather than mounted: the weights are part of the release. A container whose
# behaviour depends on what happened to be on the host's disk is not reproducible, and
# rolling back the code without rolling back the model is a debugging session nobody enjoys.
COPY artifacts ./artifacts

# The NSFW classifier, baked in for the same reason as the recommender artefact: a
# container that downloads a model on first use has a cold start measured in minutes, and
# fails to moderate anything at all if the host has no outbound network. Downloading at
# build time as the non-root user's cache would land in the wrong home, so it runs after
# the USER switch below.

# Runs as a non-root user. The original ran as root, which for a service that unpickles a
# file means arbitrary code execution lands with full container privileges.
RUN useradd --create-home --uid 10001 appuser && chown -R appuser:appuser /app
USER appuser

# Pre-warms the weights into the image. HF_HOME is set explicitly so the cache lands
# somewhere appuser owns — the default is $HOME/.cache, which is right here but only by
# coincidence, and silently becomes wrong the moment the user changes.
ENV HF_HOME=/home/appuser/.cache/huggingface
RUN python -c "from transformers import pipeline; pipeline('image-classification', model='Falconsai/nsfw_image_detection', device=-1)"

# The ipython/ipykernel install is gone with the notebook execution endpoint it existed for.

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    # Belt and braces with the sys.path guard in main.py. Unpickling the artefact imports
    # gamebuddy_model, and `python api/main.py` puts /app/api on sys.path rather than
    # /app — so without one of these the container starts and immediately dies with
    # ModuleNotFoundError.
    PYTHONPATH=/app \
    MODEL_ARTIFACT=/app/artifacts/recommender.pkl \
    PORT=8000

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health').status==200 else 1)"

CMD ["python", "api/main.py"]
