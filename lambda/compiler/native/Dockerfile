# gcc and g++ for C11/C17 and C++17/C++20. One image, runtime id picks the standard.
FROM public.ecr.aws/lambda/python:3.12

RUN dnf install -y gcc gcc-c++ \
    && dnf clean all \
    && gcc --version \
    && g++ --version

COPY handler.py ${LAMBDA_TASK_ROOT}/handler.py
CMD ["handler.handler"]
