# Local-mode Spark 3.5 and 4.2 for PySpark and Apache Spark & Scala.
# Not a cluster. First cold start is slow; the function is sized for that.
# Archives are fetched on the host by deploy.sh into vendor/ so the build
# does not download them through the emulated container network.
FROM public.ecr.aws/lambda/python:3.12

COPY vendor/corretto17.tgz /tmp/j17.tgz
COPY vendor/spark35.tgz /tmp/spark35.tgz
COPY vendor/spark42.tgz /tmp/spark42.tgz
COPY vendor/scala213.tgz /tmp/scala2.tgz

RUN dnf install -y tar gzip findutils \
    && dnf clean all \
    && mkdir -p /opt/java /tmp/jdk /opt/spark /tmp/spark /opt/scala /tmp/scala \
    && tar -xzf /tmp/j17.tgz -C /tmp/jdk \
    && mv /tmp/jdk/amazon-corretto-17* /opt/java/17 \
    && tar -xzf /tmp/spark35.tgz -C /tmp/spark \
    && tar -xzf /tmp/spark42.tgz -C /tmp/spark \
    && mv /tmp/spark/spark-3.5* /opt/spark/3.5 \
    && mv /tmp/spark/spark-4.2* /opt/spark/4.2 \
    && tar -xzf /tmp/scala2.tgz -C /tmp/scala \
    && mv /tmp/scala/scala-2.13* /opt/scala/2.13 \
    && rm -rf /tmp/j17.tgz /tmp/spark35.tgz /tmp/spark42.tgz /tmp/scala2.tgz /tmp/jdk /tmp/spark /tmp/scala \
    && test -x /opt/java/17/bin/java \
    && test -x /opt/spark/3.5/bin/spark-submit \
    && test -x /opt/spark/4.2/bin/spark-submit \
    && test -x /opt/scala/2.13/bin/scalac

# spark-class uses bash process substitution (/dev/fd), which the Lambda sandbox does not provide.
# The temp file has to be created before the while loop: the redirect on `done` is opened first.
RUN python3 - <<'PY'
from pathlib import Path
start_marker = "CMD=()"
done_marker = 'done < <(build_command "$@")'
preamble = "_cmd_file=$(mktemp)\nbuild_command \"$@\" > \"$_cmd_file\"\n"
done_line = "done < \"$_cmd_file\"\nrm -f \"$_cmd_file\""
patched = 0
for path in Path("/opt/spark").glob("*/bin/spark-class"):
    text = path.read_text()
    start = text.find(start_marker)
    done = text.find(done_marker)
    if start < 0 or done < 0 or done < start:
        raise SystemExit(f"spark-class pattern missing in {path}")
    text = text[:start] + preamble + text[start:done] + done_line + text[done + len(done_marker):]
    if "/dev/fd" in text or "<(" in text:
        raise SystemExit(f"process substitution remains in {path}")
    path.write_text(text)
    patched += 1
if patched != 2:
    raise SystemExit(f"expected 2 spark-class scripts, patched {patched}")
print("patched", patched)
PY

ENV JAVA_HOME=/opt/java/17
COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py
CMD ["handler.handler"]
