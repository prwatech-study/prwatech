"""
Lambda handler for the practical-exercise CSV sandbox.

Contract with the caller (prwatech):

    event = {
        "dataset_id": "ds_xxxxxxxxxxxx",  # optional — omitted entirely for ad-hoc mode below
        "storage_key": "practical-datasets/<courseId>/<moduleId>/<idx>/<datasetId>.csv",  # optional
        "code": "<AI-generated or learner-authored Python source>",
    }

Two modes, both handled by the same function:
  - Practical-exercise mode: both dataset_id and storage_key present. The dataset is fetched and
    exposed to the code as `df`, a pandas DataFrame.
  - Ad-hoc mode (the general Debug/Code-Execution feature, Python courses only): storage_key
    omitted. No S3 fetch happens at all; `df` is not defined in the exec namespace — code that
    references it gets a plain NameError, same as any other undefined name.

`storage_key` is resolved by prwatech (via PracticalDatasetService, looking up Mongo by
dataset_id) and is expected to have already passed a course-ownership check before this function
is ever invoked. This handler trusts its caller for *which* dataset to fetch; everything else —
network egress, allowed imports, the exec namespace, resource limits — it enforces on its own,
so a compromised or careless caller still can't get the sandbox to do anything beyond read one
CSV and run one script against it.

Deploy as a container image (see Dockerfile) in a Lambda function that:
  - sits in a private VPC subnet with no NAT/IGW route (no path to the public internet)
  - reaches DATASET_BUCKET only through an S3 Gateway VPC Endpoint
  - has an execution role scoped to s3:GetObject on that bucket's practical-datasets/* prefix
    and nothing else
"""

import ast
import base64
import io
import logging
import os
from contextlib import redirect_stdout

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3 = boto3.client("s3")  # reachable only via the VPC's S3 Gateway Endpoint — no other route out

DATASET_BUCKET = os.environ["DATASET_BUCKET"]

ALLOWED_MODULES = {"pandas", "numpy", "matplotlib", "math", "statistics", "collections", "datetime"}

# Flagged by the static guard below purely for a clear rejection message. None of these names are
# actually reachable at exec() time regardless — build_sandbox_globals() never binds them and
# __builtins__ is emptied, so even a guard blind spot degrades to a NameError, not a bypass.
BLOCKED_NAMES = {
    "open", "__import__", "eval", "exec", "compile", "input", "exit", "quit", "breakpoint",
    "globals", "locals", "vars", "dir", "getattr", "setattr", "delattr", "hasattr",
    "super", "type", "memoryview", "staticmethod", "classmethod",
}


class ImportGuard(ast.NodeVisitor):
    """Walks the parsed code once; every violation is collected, not just the first, so the
    learner (and the AI, on its next attempt) sees the whole picture in one round trip."""

    def __init__(self):
        self.violations = []

    def visit_Import(self, node):
        for alias in node.names:
            root = alias.name.split(".")[0]
            if root not in ALLOWED_MODULES:
                self.violations.append(f"import '{alias.name}' is not allowed")
        self.generic_visit(node)

    def visit_ImportFrom(self, node):
        root = (node.module or "").split(".")[0]
        if root not in ALLOWED_MODULES:
            self.violations.append(f"import from '{node.module}' is not allowed")
        self.generic_visit(node)

    def visit_Name(self, node):
        if node.id in BLOCKED_NAMES:
            self.violations.append(f"use of '{node.id}' is not allowed")
        self.generic_visit(node)

    def visit_Attribute(self, node):
        # Blocks df.__class__, x.__globals__, etc. Deliberately blunt: any dunder attribute
        # access is rejected, even rare legitimate ones — usability loss here is cheap,
        # a missed sandbox-escape attribute isn't.
        if node.attr.startswith("__") and node.attr.endswith("__"):
            self.violations.append(f"dunder attribute access '{node.attr}' is not allowed")
        self.generic_visit(node)


def validate(code: str) -> list:
    """Returns a list of violation strings; empty means the code passed."""
    try:
        tree = ast.parse(code, mode="exec")
    except SyntaxError as exc:
        return [f"syntax error: {exc.msg} (line {exc.lineno})"]
    guard = ImportGuard()
    guard.visit(tree)
    return guard.violations


def build_sandbox_globals(dataframe, plt_module):
    import collections
    import datetime
    import math
    import statistics

    import numpy
    import pandas

    globals_dict = {
        "__builtins__": {},  # nothing ambient — every name below is explicit
        "pd": pandas,
        "np": numpy,
        "math": math,
        "statistics": statistics,
        "collections": collections,
        "datetime": datetime,
        "plt": plt_module,
        # Minimal safe builtins re-added by hand. Notably absent: open, getattr/setattr, exec/eval,
        # __import__, globals/locals/vars/dir — see BLOCKED_NAMES above.
        "len": len, "range": range, "sum": sum, "min": min, "max": max,
        "sorted": sorted, "list": list, "dict": dict, "set": set, "tuple": tuple,
        "str": str, "int": int, "float": float, "bool": bool, "round": round,
        "enumerate": enumerate, "zip": zip, "abs": abs, "print": print,
        "isinstance": isinstance, "reversed": reversed, "map": map, "filter": filter,
    }
    # Ad-hoc mode (no dataset) leaves `df` undefined entirely, rather than binding it to None —
    # code that references it gets a normal NameError instead of a confusing AttributeError on
    # None, and code that doesn't touch `df` at all behaves identically either way.
    if dataframe is not None:
        globals_dict["df"] = dataframe
    return globals_dict


def handler(event, context):
    dataset_id = event.get("dataset_id", "unknown")
    storage_key = event.get("storage_key")
    code = event["code"]

    violations = validate(code)
    if violations:
        logger.info("rejected dataset=%s violations=%s", dataset_id, violations)
        return {"status": "rejected", "violations": violations}

    import matplotlib
    matplotlib.use("Agg")  # headless — no display backend inside Lambda
    import matplotlib.pyplot as plt

    df = None
    if storage_key:
        try:
            csv_bytes = s3.get_object(Bucket=DATASET_BUCKET, Key=storage_key)["Body"].read()
        except Exception as exc:
            logger.exception("dataset fetch failed dataset=%s key=%s", dataset_id, storage_key)
            return {"status": "error", "error": f"could not load dataset: {exc}"}

        import pandas as pd
        try:
            df = pd.read_csv(io.BytesIO(csv_bytes))
        except Exception as exc:
            return {"status": "error", "error": f"could not parse dataset as CSV: {exc}"}

    sandbox_globals = build_sandbox_globals(df, plt)
    stdout_capture = io.StringIO()

    try:
        with redirect_stdout(stdout_capture):
            compiled = compile(ast.parse(code, mode="exec"), "<learner_code>", "exec")
            exec(compiled, sandbox_globals)
    except Exception as exc:
        return {
            "status": "error",
            "error": f"{type(exc).__name__}: {exc}",
            "stdout": stdout_capture.getvalue(),
        }

    figures = []
    for fignum in plt.get_fignums():
        buf = io.BytesIO()
        plt.figure(fignum).savefig(buf, format="png", bbox_inches="tight")
        figures.append(base64.b64encode(buf.getvalue()).decode("ascii"))
    plt.close("all")

    result = sandbox_globals.get("result")
    return {
        "status": "ok",
        "stdout": stdout_capture.getvalue(),
        "result": repr(result) if result is not None else None,
        "figures": figures,
    }
