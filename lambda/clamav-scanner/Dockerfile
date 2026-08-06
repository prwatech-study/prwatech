# Verified working recipe (built + ran locally against a real EICAR test file and a clean CSV
# before this was written — see the PR description / conversation for the transcript). The
# `clamav`/`clamav-update` packages in AL2023's default dnf repo are version 0.103.12, which
# ClamAV's own CDN now rejects with a 403 (outdated-client block) — freshclam simply cannot
# update signatures with that package. Installing the official Cisco-Talos rpm release instead
# gets a current version the CDN accepts.
FROM public.ecr.aws/lambda/python:3.12

ARG CLAMAV_VERSION=1.5.3
RUN curl -sL -o /tmp/clamav.rpm \
      "https://github.com/Cisco-Talos/clamav/releases/download/clamav-${CLAMAV_VERSION}/clamav-${CLAMAV_VERSION}.linux.x86_64.rpm" \
    && rpm -ivh /tmp/clamav.rpm \
    && rm -f /tmp/clamav.rpm

COPY requirements.txt ${LAMBDA_TASK_ROOT}/requirements.txt
RUN pip install --no-cache-dir -r ${LAMBDA_TASK_ROOT}/requirements.txt

COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py

CMD ["handler.handler"]
