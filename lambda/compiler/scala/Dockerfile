# Scala 3 and Scala 2.13, plus a JDK to run them. Spark courses use the spark image instead.
FROM public.ecr.aws/lambda/python:3.12

RUN dnf install -y tar gzip \
    && dnf clean all \
    && mkdir -p /opt/java /tmp/jdk /opt/scala /tmp/scala \
    && curl -fsSL -o /tmp/j17.tgz https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz \
    && tar -xzf /tmp/j17.tgz -C /tmp/jdk \
    && mv /tmp/jdk/amazon-corretto-17* /opt/java/17 \
    && rm -rf /tmp/j17.tgz /tmp/jdk \
    && curl -fL --retry 3 -o /tmp/scala3.tgz \
        https://github.com/scala/scala3/releases/download/3.6.4/scala3-3.6.4.tar.gz \
        || curl -fL --retry 3 -o /tmp/scala3.tgz \
        https://github.com/scala/scala3/releases/download/3.5.2/scala3-3.5.2.tar.gz \
    && curl -fL --retry 3 -o /tmp/scala2.tgz \
        https://github.com/scala/scala/releases/download/v2.13.16/scala-2.13.16.tgz \
        || curl -fL --retry 3 -o /tmp/scala2.tgz \
        https://github.com/scala/scala/releases/download/v2.13.15/scala-2.13.15.tgz \
    && tar -xzf /tmp/scala3.tgz -C /tmp/scala \
    && tar -xzf /tmp/scala2.tgz -C /tmp/scala \
    && mv /tmp/scala/scala3-* /opt/scala/3 \
    && mv /tmp/scala/scala-2.13* /opt/scala/2.13 \
    && rm -rf /tmp/scala3.tgz /tmp/scala2.tgz /tmp/scala \
    && test -x /opt/scala/3/bin/scalac \
    && test -x /opt/scala/2.13/bin/scalac

ENV JAVA_HOME=/opt/java/17
COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py
CMD ["handler.handler"]
