# Go 1.27 (default) and Go 1.26. Third-party modules are disabled at run time (GOPROXY=off).
FROM public.ecr.aws/lambda/python:3.12

RUN dnf install -y tar gzip \
    && dnf clean all \
    && mkdir -p /opt/go/1.27 /opt/go/1.26 \
    && curl -fsSL -o /tmp/go127.tgz https://go.dev/dl/go1.27.3.linux-amd64.tar.gz \
    && curl -fsSL -o /tmp/go126.tgz https://go.dev/dl/go1.26.10.linux-amd64.tar.gz \
    && tar -xzf /tmp/go127.tgz -C /opt/go/1.27 --strip-components=1 \
    && tar -xzf /tmp/go126.tgz -C /opt/go/1.26 --strip-components=1 \
    && rm /tmp/go127.tgz /tmp/go126.tgz \
    && test -x /opt/go/1.27/bin/go \
    && test -x /opt/go/1.26/bin/go

COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py
CMD ["handler.handler"]
