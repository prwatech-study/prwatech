# SQLite 3 for the SQL course. Learner SQL runs against an in-memory database.
FROM public.ecr.aws/lambda/python:3.12

RUN dnf install -y sqlite \
    && dnf clean all \
    && sqlite3 --version

COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py
CMD ["handler.handler"]
