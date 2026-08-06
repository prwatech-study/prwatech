"""
Synchronous malware scan for admin-uploaded practical-exercise CSVs.

Invoked by prwatech (PracticalDatasetService, via MalwareScanService) *before* a CSV is ever
written to the datasets bucket — there is no async/quarantine step and no window where an
infected file is reachable, because nothing downstream of this call runs until it returns clean.
That's only workable because these are small (<=1MB), infrequent, admin-only uploads; it would
not scale to a public high-volume upload path, which is why this design is deliberately simple
rather than the classic S3-event-trigger + quarantine-bucket pattern.

Contract:
    event = {"content_base64": "<base64 CSV bytes>"}
    returns {"clean": true} | {"clean": false, "threats": [...]} | {"error": "..."}

The virus signature database is not baked into the image — it's refreshed independently by the
clamav-updater function (see ../clamav-updater) and read from S3 here, cached in /tmp across
warm invocations of the same execution environment.
"""

import base64
import logging
import os
import subprocess
import tempfile
import time

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3 = boto3.client("s3")

DB_BUCKET = os.environ["CLAMAV_DB_BUCKET"]
DB_PREFIX = "clamav-db/"
DB_DIR = "/tmp/clamav-db"
# Re-check S3 for a fresher database at most this often per warm container — avoids a
# list+download round trip on every single invocation once a container is warm.
DB_RECHECK_SECONDS = 6 * 60 * 60

_last_synced_at = 0.0


def _sync_database():
    global _last_synced_at
    now = time.time()
    if os.path.isdir(DB_DIR) and os.listdir(DB_DIR) and (now - _last_synced_at) < DB_RECHECK_SECONDS:
        return

    os.makedirs(DB_DIR, exist_ok=True)
    paginator = s3.get_paginator("list_objects_v2")
    found_any = False
    for page in paginator.paginate(Bucket=DB_BUCKET, Prefix=DB_PREFIX):
        for obj in page.get("Contents", []):
            filename = obj["Key"].rsplit("/", 1)[-1]
            if not filename:
                continue
            s3.download_file(DB_BUCKET, obj["Key"], os.path.join(DB_DIR, filename))
            found_any = True

    if not found_any:
        raise RuntimeError(
            f"no virus database found at s3://{DB_BUCKET}/{DB_PREFIX} — "
            "has the clamav-updater function run yet?"
        )
    _last_synced_at = now


def handler(event, context):
    try:
        _sync_database()
    except Exception as exc:
        logger.exception("virus database sync failed")
        return {"error": f"could not sync virus database: {exc}"}

    content_b64 = event.get("content_base64")
    if not content_b64:
        return {"error": "content_base64 is required"}

    try:
        content = base64.b64decode(content_b64, validate=True)
    except Exception:
        return {"error": "content_base64 is not valid base64"}

    fd, scan_path = tempfile.mkstemp(dir="/tmp", suffix=".scan")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(content)

        result = subprocess.run(
            ["/usr/local/bin/clamscan", "--database", DB_DIR, "--no-summary", "--infected", scan_path],
            capture_output=True,
            text=True,
            timeout=70,
        )
    except subprocess.TimeoutExpired:
        return {"error": "scan timed out"}
    finally:
        if os.path.exists(scan_path):
            os.remove(scan_path)

    if result.returncode == 0:
        return {"clean": True}

    if result.returncode == 1:
        threats = []
        for line in result.stdout.splitlines():
            if "FOUND" in line:
                # clamscan line shape: "<path>: <SignatureName> FOUND"
                threats.append(line.split(":", 1)[1].strip().removesuffix(" FOUND"))
        logger.info("threats detected: %s", threats)
        return {"clean": False, "threats": threats}

    logger.error("clamscan failed rc=%s stderr=%s", result.returncode, result.stderr)
    return {"error": f"clamscan failed: {result.stderr.strip()}"}
