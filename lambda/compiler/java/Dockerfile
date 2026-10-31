# Amazon Corretto 17, 21, and 25. Learner code is compiled with javac and run with java.
FROM public.ecr.aws/lambda/python:3.12

RUN dnf install -y tar gzip \
    && dnf clean all \
    && mkdir -p /opt/java /tmp/jdks \
    && curl -fsSL -o /tmp/j17.tgz https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz \
    && curl -fsSL -o /tmp/j21.tgz https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz \
    && curl -fsSL -o /tmp/j25.tgz https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz \
    && tar -xzf /tmp/j17.tgz -C /tmp/jdks \
    && tar -xzf /tmp/j21.tgz -C /tmp/jdks \
    && tar -xzf /tmp/j25.tgz -C /tmp/jdks \
    && mv /tmp/jdks/amazon-corretto-17* /opt/java/17 \
    && mv /tmp/jdks/amazon-corretto-21* /opt/java/21 \
    && mv /tmp/jdks/amazon-corretto-25* /opt/java/25 \
    && rm -rf /tmp/j17.tgz /tmp/j21.tgz /tmp/j25.tgz /tmp/jdks \
    && test -x /opt/java/17/bin/javac \
    && test -x /opt/java/21/bin/javac \
    && test -x /opt/java/25/bin/javac

COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py
CMD ["handler.handler"]
