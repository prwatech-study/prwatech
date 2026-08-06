# Same verified ClamAV install as ../clamav-scanner/Dockerfile — see that file's comment for why
# this doesn't use AL2023's dnf clamav package (too old, CDN-blocked).
FROM public.ecr.aws/lambda/python:3.12

ARG CLAMAV_VERSION=1.5.3
RUN curl -sL -o /tmp/clamav.rpm \
      "https://github.com/Cisco-Talos/clamav/releases/download/clamav-${CLAMAV_VERSION}/clamav-${CLAMAV_VERSION}.linux.x86_64.rpm" \
    && rpm -ivh /tmp/clamav.rpm \
    && rm -f /tmp/clamav.rpm

COPY requirements.txt ${LAMBDA_TASK_ROOT}/requirements.txt
RUN pip install --no-cache-dir -r ${LAMBDA_TASK_ROOT}/requirements.txt

COPY freshclam.conf ${LAMBDA_TASK_ROOT}/freshclam.conf
COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py

CMD ["handler.handler"]
