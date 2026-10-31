"""
Compile-and-run sandbox for Skillama programming courses.

One image per toolchain family. COMPILER_FAMILY (python, java, native, dotnet, go, scala,
sql, spark) is set on the Lambda. The payload is:

    {"runtime": "java17", "code": "...", "stdin": "", "runner": "script"|"auto"}

Learner code runs in a child process. AWS credential environment variables are removed from
that child. The function sits in a VPC with no internet route, and its role cannot read the
dataset bucket. This file does not fetch S3 objects.

Limits: code and stdin size, wall-clock timeout, process count, captured output size.
"""

import glob
import os
import re
import shutil
import signal
import subprocess
import tempfile

MAX_CODE_BYTES = 100_000
MAX_STDIN_BYTES = 64_000
MAX_OUTPUT_BYTES = 256_000

FAMILIES = {
    "python": {"python3.12", "python3.13", "python3.14", "python3.15"},
    "java": {"java17", "java21", "java25"},
    "native": {"c11", "c17", "cpp17", "cpp20"},
    "dotnet": {"dotnet8", "dotnet10"},
    "go": {"go1.26", "go1.27"},
    "scala": {"scala3", "scala213"},
    "sql": {"sqlite3"},
    "spark": {"spark35-python", "spark42-python", "spark35-scala", "spark42-scala"},
}

JAVA_CLASS = re.compile(r"\bpublic\s+class\s+([A-Za-z_]\w*)")
JAVA_TYPE = re.compile(
    r"(?m)^(?!\s*(?://|/\*|\*))\s*(?:public\s+|private\s+|protected\s+)?"
    r"(?:abstract\s+|final\s+|sealed\s+)*(?:class|interface|enum|record)\s+[A-Za-z_]"
)
JAVA_MAIN = re.compile(r"\bvoid\s+main\s*\(")
SCALA_NAME = re.compile(r"\b(?:object|class)\s+([A-Za-z_]\w*)")
PYTEST_HINT = re.compile(r"(?m)^\s*def\s+test_|^\s*import\s+pytest|^\s*from\s+pytest\b")
SQL_ATTACH = re.compile(r"\battach\b|\bload_extension\b", re.IGNORECASE)


def allowed_runtimes():
    family = os.environ.get("COMPILER_FAMILY", "").strip()
    if not family:
        allowed = set()
        for names in FAMILIES.values():
            allowed |= names
        return allowed
    return set(FAMILIES.get(family, ()))


def run_timeout():
    try:
        return max(1, int(os.environ.get("RUN_TIMEOUT_SEC", "15")))
    except ValueError:
        return 15


def run_nproc():
    try:
        return max(32, int(os.environ.get("RUN_NPROC", "128")))
    except ValueError:
        return 128


def child_env():
    """Environment for learner processes. Drops AWS credentials injected into the Lambda."""
    env = {}
    for key, value in os.environ.items():
        if key.startswith("AWS_") or key.startswith("_AWS"):
            continue
        env[key] = value
    env["HOME"] = "/tmp"
    env["TMPDIR"] = "/tmp"
    env["PYTHONDONTWRITEBYTECODE"] = "1"
    return env


def _limits():
    import resource

    nproc = run_nproc()
    resource.setrlimit(resource.RLIMIT_NPROC, (nproc, nproc))
    # Do not cap RLIMIT_FSIZE. .NET and Spark write temp files larger than a
    # small cap, and the kernel then kills the process with SIGXFSZ. Captured
    # stdout is still truncated, and Lambda ephemeral storage bounds the disk.


def _truncate(data: bytes) -> str:
    if len(data) <= MAX_OUTPUT_BYTES:
        return data.decode("utf-8", errors="replace")
    text = data[:MAX_OUTPUT_BYTES].decode("utf-8", errors="replace")
    return text + "\n... output truncated ..."


def run_command(args, cwd, env, stdin_text, timeout):
    proc = subprocess.Popen(
        args,
        cwd=cwd,
        env=env,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        start_new_session=True,
        preexec_fn=_limits,
    )
    try:
        out, err = proc.communicate(
            input=(stdin_text or "").encode("utf-8"),
            timeout=timeout,
        )
        return proc.returncode, _truncate(out), _truncate(err)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        try:
            out, err = proc.communicate(timeout=3)
        except subprocess.TimeoutExpired:
            proc.kill()
            out, err = proc.communicate()
        message = _truncate(err)
        if "Timed out" not in message:
            message = (message + "\nTimed out.").strip()
        return 124, _truncate(out), message


def finish(returncode, stdout, stderr):
    if returncode == 0:
        combined = stdout
        if stderr.strip():
            combined = (stdout + ("\n" if stdout else "") + stderr).strip()
        return {"status": "ok", "stdout": combined, "stderr": stderr}
    error = stderr.strip() or stdout.strip() or f"Execution failed ({returncode})"
    return {"status": "error", "stdout": stdout, "stderr": stderr, "error": error}


def public_class_name(code: str) -> str:
    match = JAVA_CLASS.search(code or "")
    return match.group(1) if match else "Main"


def prepare_java_source(code: str):
    """Lesson snippets are often one method plus comments. javac needs a type.

    A fragment with no class, interface, enum, or record is wrapped in
    public class Main. Imports and the package line stay outside that class.
    """
    text = (code or "").strip() + "\n"
    if JAVA_TYPE.search(text):
        source = text
    else:
        preamble = []
        body = []
        for line in text.splitlines(keepends=True):
            stripped = line.strip()
            if not body and (stripped.startswith("package ") or stripped.startswith("import ")):
                preamble.append(line if line.endswith("\n") else line + "\n")
                continue
            body.append(line)
        source = "".join(preamble) + "public class Main {\n" + "".join(body) + "}\n"
    return public_class_name(source), source, bool(JAVA_MAIN.search(source))


def scala_type_name(code: str) -> str:
    match = SCALA_NAME.search(code or "")
    return match.group(1) if match else "Main"


def wants_pytest(code: str, runner: str) -> bool:
    if runner == "pytest":
        return True
    if runner == "auto":
        return bool(PYTEST_HINT.search(code or ""))
    return False


def sqlite_rejection(code: str):
    for line in (code or "").splitlines():
        stripped = line.strip()
        if stripped.startswith("."):
            return "SQLite dot-commands are not allowed. Submit SQL only."
    if SQL_ATTACH.search(code or ""):
        return "ATTACH and load_extension are not available in this sandbox."
    return None


def _write(path, text):
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def _missing(path, label):
    if os.path.exists(path):
        return None
    return {"status": "error", "stdout": "", "stderr": "", "error": f"{label} is not installed in this sandbox"}


def run_python(runtime, code, stdin_text, runner, work):
    version = runtime.replace("python", "")
    binary = f"/opt/python/{version}/bin/python3"
    missing = _missing(binary, f"Python {version}")
    if missing:
        return missing
    _write(os.path.join(work, "main.py"), code)
    env = child_env()
    if wants_pytest(code, runner):
        code_rc, out, err = run_command(
            [binary, "-m", "pytest", "main.py", "-q", "--tb=short", "-p", "no:cacheprovider"],
            work, env, "", run_timeout(),
        )
    else:
        code_rc, out, err = run_command([binary, "main.py"], work, env, stdin_text, run_timeout())
    return finish(code_rc, out, err)


def run_java(runtime, code, stdin_text, work):
    major = runtime.replace("java", "")
    home = f"/opt/java/{major}"
    missing = _missing(os.path.join(home, "bin", "javac"), f"Java {major}")
    if missing:
        return missing
    name, source, has_main = prepare_java_source(code)
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
        name = "Main"
    _write(os.path.join(work, f"{name}.java"), source)
    env = child_env()
    env["JAVA_HOME"] = home
    env["PATH"] = os.path.join(home, "bin") + os.pathsep + env.get("PATH", "")
    rc, out, err = run_command(
        [os.path.join(home, "bin", "javac"), f"{name}.java"], work, env, "", run_timeout()
    )
    if rc != 0:
        return finish(rc, out, err or out)
    if not has_main:
        return {
            "status": "ok",
            "stdout": "Compiled successfully. This snippet has no main method, so nothing was run.\n",
            "stderr": "",
        }
    rc, out, err = run_command(
        [os.path.join(home, "bin", "java"), "-cp", work, name], work, env, stdin_text, run_timeout()
    )
    return finish(rc, out, err)


def run_native(runtime, code, stdin_text, work):
    standards = {"c11": "c11", "c17": "c17", "cpp17": "c++17", "cpp20": "c++20"}
    is_cpp = runtime.startswith("cpp")
    compiler = "g++" if is_cpp else "gcc"
    source = "main.cpp" if is_cpp else "main.c"
    _write(os.path.join(work, source), code)
    env = child_env()
    binary = os.path.join(work, "a.out")
    rc, out, err = run_command(
        [compiler, f"-std={standards[runtime]}", "-O0", "-o", binary, source, "-lm"],
        work, env, "", run_timeout(),
    )
    if rc != 0:
        return finish(rc, out, err or out)
    rc, out, err = run_command([binary], work, env, stdin_text, run_timeout())
    return finish(rc, out, err)


def run_dotnet(runtime, code, stdin_text, work):
    major = "8" if runtime == "dotnet8" else "10"
    root = f"/opt/dotnet/{major}"
    template = f"/opt/templates/dotnet{major}"
    dotnet = os.path.join(root, "dotnet")
    missing = _missing(dotnet, f".NET {major}")
    if missing:
        return missing
    if not os.path.isdir(template):
        return _missing(template, f".NET {major} project template")
    shutil.copytree(template, work, dirs_exist_ok=True)
    _write(os.path.join(work, "Program.cs"), code)
    env = child_env()
    env["DOTNET_ROOT"] = root
    # First-run sentinels and NuGet assets were created under /root at image build.
    env["HOME"] = "/root"
    env["DOTNET_CLI_HOME"] = "/root"
    env["DOTNET_SKIP_FIRST_TIME_EXPERIENCE"] = "1"
    env["DOTNET_NOLOGO"] = "1"
    env["DOTNET_CLI_TELEMETRY_OPTOUT"] = "1"
    # Lambda blocks write-and-execute mappings. Without this, the host aborts.
    env["DOTNET_EnableWriteXorExecute"] = "0"
    env["PATH"] = root + os.pathsep + env.get("PATH", "")
    rc, out, err = run_command(
        [dotnet, "run", "--no-restore", "--project", work],
        work, env, stdin_text, run_timeout(),
    )
    return finish(rc, out, err)


def run_go(runtime, code, stdin_text, work):
    version = "1.27" if runtime == "go1.27" else "1.26"
    goroot = f"/opt/go/{version}"
    go_bin = os.path.join(goroot, "bin", "go")
    missing = _missing(go_bin, f"Go {version}")
    if missing:
        return missing
    _write(os.path.join(work, "main.go"), code)
    _write(os.path.join(work, "go.mod"), "module learner\n\ngo 1.22\n")
    env = child_env()
    env["GOROOT"] = goroot
    env["GOPATH"] = "/tmp/gopath"
    env["GOCACHE"] = "/tmp/gocache"
    env["GOPROXY"] = "off"
    env["GO111MODULE"] = "on"
    env["PATH"] = os.path.join(goroot, "bin") + os.pathsep + env.get("PATH", "")
    os.makedirs(env["GOCACHE"], exist_ok=True)
    rc, out, err = run_command([go_bin, "run", "."], work, env, stdin_text, run_timeout())
    return finish(rc, out, err)


def scala_app_jars(home, runtime):
    """Jars needed to run a compiled program.

    The Scala 3 `scala` command is scala-cli, which writes its cache under the
    password-file home directory. That directory is read-only on Lambda, so the
    program is launched with java instead.
    """
    if runtime == "scala213":
        library = os.path.join(home, "lib", "scala-library.jar")
        return [library] if os.path.isfile(library) else []
    patterns = [
        "maven2/org/scala-lang/scala3-library_3/*/scala3-library_3-*.jar",
        "maven2/org/scala-lang/scala-library/*/scala-library-*.jar",
    ]
    jars = []
    for pattern in patterns:
        found = sorted(glob.glob(os.path.join(home, pattern)))
        if found:
            jars.append(found[-1])
    return jars


def run_scala(runtime, code, stdin_text, work):
    home = "/opt/scala/3" if runtime == "scala3" else "/opt/scala/2.13"
    java_home = "/opt/java/17"
    scalac = os.path.join(home, "bin", "scalac")
    missing = _missing(scalac, "Scala")
    if missing:
        return missing
    name = scala_type_name(code)
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
        name = "Main"
    _write(os.path.join(work, f"{name}.scala"), code)
    classes = os.path.join(work, "classes")
    os.makedirs(classes, exist_ok=True)
    env = child_env()
    env["JAVA_HOME"] = java_home
    env["JAVA_OPTS"] = "-Duser.home=/tmp"
    env["PATH"] = os.path.join(java_home, "bin") + os.pathsep + env.get("PATH", "")
    rc, out, err = run_command(
        [scalac, "-J-Duser.home=/tmp", "-d", classes, f"{name}.scala"],
        work, env, "", run_timeout(),
    )
    if rc != 0:
        return finish(rc, out, err or out)
    jars = scala_app_jars(home, runtime)
    expected = 2 if runtime == "scala3" else 1
    if len(jars) < expected:
        return {
            "status": "error",
            "stdout": "",
            "stderr": "",
            "error": "Scala standard library is not installed in this sandbox",
        }
    java_bin = os.path.join(java_home, "bin", "java")
    classpath = os.pathsep.join([classes, *jars])
    rc, out, err = run_command(
        [java_bin, "-Duser.home=/tmp", "-cp", classpath, name],
        work, env, stdin_text, run_timeout(),
    )
    return finish(rc, out, err)


def run_sql(code):
    rejected = sqlite_rejection(code)
    if rejected:
        return {"status": "error", "stdout": "", "stderr": "", "error": rejected}
    missing = _missing("/usr/bin/sqlite3", "SQLite")
    if missing:
        # Amazon Linux also installs it on PATH as sqlite3.
        if shutil.which("sqlite3"):
            binary = shutil.which("sqlite3")
        else:
            return missing
    else:
        binary = "/usr/bin/sqlite3"
    rc, out, err = run_command(
        [binary, "-batch", "-header", ":memory:"],
        "/tmp",
        child_env(),
        code,
        run_timeout(),
    )
    return finish(rc, out, err)


def _spark_home(runtime):
    return "/opt/spark/3.5" if runtime.startswith("spark35") else "/opt/spark/4.2"


def _spark_env(spark_home, work):
    env = child_env()
    env["JAVA_HOME"] = "/opt/java/17"
    env["SPARK_HOME"] = spark_home
    env["SPARK_LOCAL_IP"] = "127.0.0.1"
    env["SPARK_LOCAL_DIRS"] = work
    env["PATH"] = os.path.join(env["JAVA_HOME"], "bin") + os.pathsep + os.path.join(spark_home, "bin") + os.pathsep + env.get("PATH", "")
    env["SPARK_SUBMIT_OPTS"] = "-Duser.home=/tmp"
    return env


def run_spark_python(runtime, code, stdin_text, work):
    spark_home = _spark_home(runtime)
    missing = _missing(os.path.join(spark_home, "bin", "spark-submit"), "Spark")
    if missing:
        return missing
    _write(os.path.join(work, "main.py"), code)
    env = _spark_env(spark_home, work)
    py4j = glob.glob(os.path.join(spark_home, "python", "lib", "py4j-*.zip"))
    pythonpath = os.path.join(spark_home, "python")
    if py4j:
        pythonpath += os.pathsep + py4j[0]
    env["PYTHONPATH"] = pythonpath
    env["PYSPARK_PYTHON"] = "/var/lang/bin/python3.12"
    env["PYSPARK_DRIVER_PYTHON"] = "/var/lang/bin/python3.12"
    env["PYSPARK_SUBMIT_ARGS"] = (
        "--master local[1] "
        "--conf spark.ui.enabled=false "
        "--conf spark.driver.host=127.0.0.1 "
        "--conf spark.driver.bindAddress=127.0.0.1 "
        "pyspark-shell"
    )
    binary = env["PYSPARK_PYTHON"]
    rc, out, err = run_command([binary, "main.py"], work, env, stdin_text, run_timeout())
    return finish(rc, out, err)


def run_spark_scala(runtime, code, stdin_text, work):
    spark_home = _spark_home(runtime)
    scalac = "/opt/scala/2.13/bin/scalac"
    missing = _missing(os.path.join(spark_home, "bin", "spark-submit"), "Spark")
    if missing:
        return missing
    missing = _missing(scalac, "Scala 2.13")
    if missing:
        return missing
    name = scala_type_name(code)
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
        name = "Main"
    _write(os.path.join(work, f"{name}.scala"), code)
    classes = os.path.join(work, "classes")
    os.makedirs(classes, exist_ok=True)
    jars = glob.glob(os.path.join(spark_home, "jars", "*.jar"))
    classpath = os.pathsep.join(jars) if jars else classes
    env = _spark_env(spark_home, work)
    rc, out, err = run_command(
        [scalac, "-classpath", classpath, "-d", classes, f"{name}.scala"],
        work, env, "", run_timeout(),
    )
    if rc != 0:
        return finish(rc, out, err or out)
    rc, out, err = run_command(
        [
            os.path.join(spark_home, "bin", "spark-submit"),
            "--class", name,
            "--master", "local[1]",
            "--conf", "spark.ui.enabled=false",
            "--conf", "spark.driver.host=127.0.0.1",
            "--conf", "spark.driver.bindAddress=127.0.0.1",
            "--conf", "spark.driver.memory=1536m",
            classes,
        ],
        work, env, stdin_text, run_timeout(),
    )
    return finish(rc, out, err)


def dispatch(runtime, code, stdin_text, runner, work):
    if runtime in FAMILIES["python"]:
        return run_python(runtime, code, stdin_text, runner, work)
    if runtime in FAMILIES["java"]:
        return run_java(runtime, code, stdin_text, work)
    if runtime in FAMILIES["native"]:
        return run_native(runtime, code, stdin_text, work)
    if runtime in FAMILIES["dotnet"]:
        return run_dotnet(runtime, code, stdin_text, work)
    if runtime in FAMILIES["go"]:
        return run_go(runtime, code, stdin_text, work)
    if runtime in FAMILIES["scala"]:
        return run_scala(runtime, code, stdin_text, work)
    if runtime in FAMILIES["sql"]:
        return run_sql(code)
    if runtime.endswith("-python"):
        return run_spark_python(runtime, code, stdin_text, work)
    if runtime.endswith("-scala"):
        return run_spark_scala(runtime, code, stdin_text, work)
    return {"status": "error", "stdout": "", "stderr": "", "error": f"Unknown runtime '{runtime}'"}


def handler(event, context):
    event = event or {}
    runtime = str(event.get("runtime") or "").strip()
    code = event.get("code")
    if not isinstance(code, str) or not code.strip():
        return {"status": "error", "stdout": "", "stderr": "", "error": "code is required"}
    if len(code.encode("utf-8")) > MAX_CODE_BYTES:
        return {"status": "error", "stdout": "", "stderr": "", "error": "code is too large"}
    stdin_text = event.get("stdin") or ""
    if not isinstance(stdin_text, str):
        stdin_text = str(stdin_text)
    if len(stdin_text.encode("utf-8")) > MAX_STDIN_BYTES:
        return {"status": "error", "stdout": "", "stderr": "", "error": "stdin is too large"}
    if runtime not in allowed_runtimes():
        return {"status": "error", "stdout": "", "stderr": "", "error": f"runtime '{runtime}' is not available"}
    runner = str(event.get("runner") or "script")
    work = tempfile.mkdtemp(prefix="compile-")
    try:
        return dispatch(runtime, code, stdin_text, runner, work)
    except Exception as exc:
        return {"status": "error", "stdout": "", "stderr": "", "error": f"Execution failed: {exc}"}
    finally:
        shutil.rmtree(work, ignore_errors=True)
