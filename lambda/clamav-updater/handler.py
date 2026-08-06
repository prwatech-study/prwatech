"""
Scheduled job (see terraform/clamav.tf's EventBridge rule) that refreshes the ClamAV virus
signature database and publishes it to S3, where clamav-scanner reads it from. Runs unattached
to any VPC — unlike every other function in this system, this one genuinely needs internet
access to reach ClamAV's CDN, so it's kept deliberately separate from the sandbox's zero-egress
network rather than punching a hole in it.
"""

import glob
import logging
import os
import subprocess

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3 = boto3.client("s3")

DB_BUCKET = os.environ["CLAMAV_DB_BUCKET"]
DB_PREFIX = "clamav-db/"
DB_DIR = "/tmp/clamav-db"
FRESHCLAM_CONFIG = "/var/task/freshclam.conf"


def handler(event, context):
    os.makedirs(DB_DIR, exist_ok=True)

    try:
        # Backstop timeout well under the function's own 600s limit — freshclam.conf's own
        # ConnectTimeout/ReceiveTimeout should trigger first, but if they don't for some reason
        # (e.g. a config parsing issue), this still turns a silent platform-kill into a clear,
        # logged TimeoutExpired instead of 10 minutes of nothing.
        result = subprocess.run(
            ["/usr/local/bin/freshclam", f"--config-file={FRESHCLAM_CONFIG}", f"--datadir={DB_DIR}"],
            capture_output=True,
            text=True,
            timeout=300,
        )
    except subprocess.TimeoutExpired as exc:
        logger.error("freshclam did not exit within 300s — stdout so far: %s", exc.stdout)
        return {"status": "error", "error": "freshclam timed out after 300s", "stdout_so_far": exc.stdout}

    logger.info("freshclam rc=%s stdout=%s", result.returncode, result.stdout)
    if result.stderr:
        logger.warning("freshclam stderr=%s", result.stderr)

    # rc 0 = updated, rc 1 = already up to date — both mean the local DB is current and safe to
    # publish. Anything else is a real failure; don't overwrite a known-good published DB with
    # a possibly-partial one.
    if result.returncode not in (0, 1):
        return {
            "status": "error",
            "freshclam_returncode": result.returncode,
            "freshclam_output_tail": result.stdout[-2000:],
        }

    uploaded = []
    for path in glob.glob(os.path.join(DB_DIR, "*.c[vl]d")):  # matches .cvd and .cld
        filename = os.path.basename(path)
        s3.upload_file(path, DB_BUCKET, f"{DB_PREFIX}{filename}")
        uploaded.append(filename)

    if not uploaded:
        return {"status": "error", "error": "freshclam produced no .cvd/.cld files to publish"}

    return {"status": "ok", "uploaded": uploaded}
