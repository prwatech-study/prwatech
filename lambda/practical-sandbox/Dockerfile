# Lambda container image for the practical-exercise CSV sandbox.
# Ships exactly the allowed modules (pandas/numpy/matplotlib/boto3) and nothing that could
# reach a package index or the network at runtime — combined with a VPC with no NAT/IGW route,
# there is nowhere for the sandboxed code to install anything or exfiltrate to even if it tried.
FROM public.ecr.aws/lambda/python:3.12

# Patch OS packages flagged by ECR scanning (rpm, gawk, glib2, libxml2 CVEs) —
# the base image's own AL2023 layer lags behind upstream fixes.
RUN dnf upgrade -y rpm gawk glib2 libxml2 && dnf clean all

COPY requirements.txt ${LAMBDA_TASK_ROOT}/requirements.txt
RUN pip install --no-cache-dir -r ${LAMBDA_TASK_ROOT}/requirements.txt

COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py

CMD ["handler.handler"]
