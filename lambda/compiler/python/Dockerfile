# Full CPython 3.12–3.15 plus pytest and the data/ML libraries used by
# Python Course, Advanced Python, Python Testing, and Machine Learning.
# The Lambda handler itself runs on the base image's Python, not these interpreters.
FROM public.ecr.aws/lambda/python:3.12

RUN dnf install -y tar gzip gcc gcc-c++ make \
        freetype-devel libpng-devel \
    && dnf clean all

COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# uv-managed interpreters refuse direct pip installs. Each version gets its own venv.
RUN uv python install 3.12 3.13 3.14 3.15 \
    && mkdir -p /opt/python \
    && for v in 3.12 3.13 3.14 3.15; do \
         uv venv "/opt/python/${v}" --python "${v}" \
         && uv pip install --python "/opt/python/${v}/bin/python3" --no-cache \
              pytest numpy pandas matplotlib scikit-learn \
         || exit 1; \
       done \
    && test -x /opt/python/3.12/bin/python3 \
    && test -x /opt/python/3.15/bin/python3

# Lambda runs the handler as a non-root user. uv keeps interpreters under /root,
# which is not traversable, so the venv symlinks look missing.
RUN chmod 755 /root && chmod -R a+rX /root /opt/python

COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py
CMD ["handler.handler"]
