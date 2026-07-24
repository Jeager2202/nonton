#!/usr/bin/env python3
"""
NONTON SMARTY FIXED EDITION — APK Builder WebApp
Audit-driven repair by Smarty for Jeager22.

Goals:
- Start reliably from .bat and optionally auto-open browser.
- Build APK locally without Android Studio using Gradle Wrapper + Android SDK command-line tools.
- Show complete live feed/log in the browser while SDK/Gradle/GitHub steps run.
- Support GitHub Actions build if user supplies a token.
"""

from __future__ import annotations

from flask import Flask, jsonify, render_template_string, request, send_file
from pathlib import Path
from datetime import datetime
import argparse
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import webbrowser
import zipfile

try:
    import requests
except ImportError:  # pragma: no cover - bootstrap path
    subprocess.check_call([sys.executable, "-m", "pip", "install", "requests", "--quiet"])
    import requests

APP_VERSION = "NONTON SMARTY FIXED 2026-07-24"
PROJECT_ROOT = Path(__file__).resolve().parent.parent
ANDROID_API = "34"
BUILD_TOOLS = "34.0.0"

app = Flask(__name__)
app.secret_key = "nonton-smarty-fixed-jeager22"

jobs: dict[str, dict] = {}
TEMP_APKS: dict[str, str] = {}

HTML = r"""
<!doctype html>
<html lang="id">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>NONTON • Smarty Fixed APK Builder</title>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@500;700;900&family=Rajdhani:wght@400;500;700&display=swap');
    :root{--cyan:#00f0ff;--mag:#ff00aa;--green:#00ff9d;--red:#ff3366;--bg:#050508;--panel:#0b0b14;--line:#24243a;--text:#e8e8f0;}
    *{box-sizing:border-box} body{margin:0;background:radial-gradient(circle at 15% -10%,rgba(0,240,255,.18),transparent 28%),radial-gradient(circle at 90% 10%,rgba(255,0,170,.13),transparent 26%),var(--bg);color:var(--text);font-family:Rajdhani,system-ui,sans-serif;}
    body:before{content:"";position:fixed;inset:0;pointer-events:none;background:linear-gradient(rgba(0,240,255,.025) 1px,transparent 1px),linear-gradient(90deg,rgba(0,240,255,.025) 1px,transparent 1px);background-size:48px 48px;mask-image:linear-gradient(to bottom,black,transparent 95%)}
    .wrap{max-width:1220px;margin:auto;padding:22px}.hero{text-align:center;border-bottom:2px solid var(--cyan);padding:22px 10px 18px;background:rgba(5,5,8,.86);box-shadow:0 0 42px rgba(0,240,255,.16)}
    h1{font-family:Orbitron,sans-serif;font-size:clamp(34px,6vw,62px);letter-spacing:8px;margin:0;background:linear-gradient(90deg,var(--cyan),var(--mag),#8b5cff);-webkit-background-clip:text;color:transparent;text-shadow:0 0 38px rgba(0,240,255,.38)}
    .sub{font-family:Orbitron,sans-serif;color:var(--mag);font-size:12px;letter-spacing:5px;margin-top:8px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:18px}.card{background:rgba(11,11,20,.94);border:1px solid var(--line);border-radius:18px;padding:20px;box-shadow:0 0 28px rgba(0,0,0,.35), inset 0 0 0 1px rgba(255,255,255,.02)}
    @media(max-width:900px){.grid{grid-template-columns:1fr}.wrap{padding:12px}.card{padding:16px}}
    .title{font-family:Orbitron,sans-serif;color:var(--cyan);letter-spacing:2px;font-weight:800;margin-bottom:12px}.muted{color:#9b9bb2}.pill{display:inline-block;border:1px solid var(--line);border-radius:999px;padding:4px 10px;margin:3px;color:var(--cyan);background:#11111e;font-size:12px}
    button,.btn{width:100%;border:none;border-radius:10px;padding:14px 18px;font-family:Orbitron,sans-serif;font-weight:900;letter-spacing:1px;cursor:pointer;color:#050508;background:linear-gradient(45deg,var(--cyan),#8b5cff);box-shadow:0 0 22px rgba(0,240,255,.35);margin:7px 0;text-decoration:none;text-align:center;display:inline-block}
    button.secondary{background:#11111e;color:var(--cyan);border:1px solid var(--cyan);box-shadow:0 0 14px rgba(0,240,255,.18)} button.danger{background:linear-gradient(45deg,var(--red),var(--mag));color:white} button:disabled{opacity:.55;cursor:not-allowed}
    input{width:100%;background:#11111e;border:1px solid var(--line);border-radius:10px;color:var(--text);padding:13px 14px;margin:7px 0;font-size:16px;outline:none} input:focus{border-color:var(--cyan);box-shadow:0 0 0 3px rgba(0,240,255,.12)}
    .progress{height:10px;background:#171725;border:1px solid var(--line);border-radius:999px;overflow:hidden;margin:10px 0}.bar{height:100%;width:0;background:linear-gradient(90deg,var(--cyan),var(--mag));box-shadow:0 0 16px var(--cyan);transition:.35s}
    .status{border-left:5px solid var(--cyan);background:#10101c;border-radius:10px;padding:14px 16px;white-space:pre-wrap;line-height:1.45}.status.err{border-color:var(--red)}.status.ok{border-color:var(--green)}
    .stats{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin:10px 0}.stat{background:#090911;border:1px solid var(--line);border-radius:12px;padding:10px}.k{font-size:11px;color:#8e8ea5;letter-spacing:1px}.v{font-family:ui-monospace,Consolas,monospace;color:var(--cyan);word-break:break-all}
    @media(max-width:700px){.stats{grid-template-columns:1fr 1fr}}
    pre{margin:0;min-height:320px;max-height:520px;overflow:auto;background:#050508;border:1px solid var(--line);border-radius:12px;padding:14px;color:#dcecff;font-family:ui-monospace,Consolas,monospace;font-size:12.5px;line-height:1.45;white-space:pre-wrap}.loghead{display:flex;gap:8px;align-items:center;justify-content:space-between;margin-bottom:8px}.tiny{font-size:12px;color:#777}.tabs{display:flex;gap:8px;flex-wrap:wrap}.tabs button{width:auto;min-width:150px;padding:11px 14px}.hidden{display:none!important}.download{background:linear-gradient(45deg,var(--green),var(--cyan));font-size:17px}
  </style>
</head>
<body>
  <div class="hero"><h1>NONTON</h1><div class="sub">SMARTY FIXED EDITION • APK BUILDER TANPA ANDROID STUDIO</div></div>
  <div class="wrap">
    <div class="card">
      <div class="title">📡 CONTROL CENTER</div>
      <div class="tabs">
        <button onclick="startLocalBuild()" id="btnLocal">⚡ BUILD APK LOKAL</button>
        <button class="secondary" onclick="checkEnv()">🔍 CHECK ENV</button>
        <button class="secondary" onclick="toggleGitHub()">🔥 GITHUB ACTIONS</button>
      </div>
      <div class="progress"><div class="bar" id="bar"></div></div>
      <div class="stats">
        <div class="stat"><div class="k">JOB</div><div class="v" id="job">-</div></div>
        <div class="stat"><div class="k">PHASE</div><div class="v" id="phase">IDLE</div></div>
        <div class="stat"><div class="k">PROGRESS</div><div class="v" id="progress">0%</div></div>
        <div class="stat"><div class="k">SPEED / ETA</div><div class="v" id="speed">-</div></div>
      </div>
      <div class="status" id="status">Ready. Klik BUILD APK LOKAL untuk membuat nonton.apk.</div>
      <div id="downloadBox" class="hidden"><a id="downloadLink" class="btn download" href="#">⬇ DOWNLOAD NONTON.APK</a></div>
    </div>

    <div id="githubBox" class="card hidden">
      <div class="title">🔥 BUILD VIA GITHUB ACTIONS</div>
      <div class="muted">Token tidak disimpan permanen oleh webapp. Repo akan dibuat/dipush lalu workflow <b>android-build.yml</b> dijalankan.</div>
      <input id="ghToken" type="password" placeholder="GitHub Personal Access Token: repo + workflow" />
      <input id="ghRepo" value="nonton" placeholder="Nama repository" />
      <button onclick="startGitHubBuild()">🚀 PUSH + RUN ACTIONS + AMBIL APK</button>
    </div>

    <div class="grid">
      <div class="card">
        <div class="title">✅ HASIL AUDIT PERBAIKAN</div>
        <span class="pill">Gradle Wrapper lengkap</span><span class="pill">Root build.gradle.kts ada</span><span class="pill">Android source ada</span><span class="pill">Live log streaming</span><span class="pill">Auto browser fixed</span><span class="pill">GitHub workflow added</span>
        <p class="muted">Source Android NONTON asli sudah digabung dengan build system Smarty: Gradle wrapper valid, Kotlin/Room/Media3 ready, live log streaming, dan launcher auto-browser memakai health-check.</p>
      </div>
      <div class="card">
        <div class="title">💡 SMARTY VISION</div>
        <p class="muted">Tahap berikutnya yang paling visioner: queue build multi-user, template app visual, signing APK otomatis, build AAB, cache SDK/Gradle, dan dashboard artifact history.</p>
      </div>
    </div>

    <div class="card" style="margin-top:18px">
      <div class="loghead"><div class="title" style="margin:0">🧾 LIVE FEED + KOLOM LOG LENGKAP</div><button class="secondary" style="width:auto" onclick="copyLog()">COPY LOG</button></div>
      <pre id="log">Log akan muncul di sini secara realtime...</pre>
      <div class="tiny">Polling 1 detik • log disimpan di memori job selama server berjalan.</div>
    </div>
  </div>

<script>
let currentJob=null, timer=null;
function el(id){return document.getElementById(id)}
function setStatus(txt, cls=''){ el('status').className='status '+cls; el('status').textContent=txt || '-'; }
function setProgress(p){ p=Math.max(0,Math.min(100,Number(p||0))); el('bar').style.width=p+'%'; el('progress').textContent=Math.round(p)+'%'; }
function toggleGitHub(){ el('githubBox').classList.toggle('hidden'); }
function resetUi(){ el('downloadBox').classList.add('hidden'); el('downloadLink').href='#'; el('log').textContent='Memulai...\n'; setProgress(0); el('speed').textContent='-'; }
async function startLocalBuild(){
  resetUi(); el('btnLocal').disabled=true; setStatus('Mengirim perintah build lokal...');
  try{ const r=await fetch('/api/local_build',{method:'POST'}); const d=await r.json(); if(!d.success) throw new Error(d.error||'Gagal start'); currentJob=d.job_id; el('job').textContent=currentJob; poll(); }
  catch(e){ setStatus('❌ '+e.message,'err'); el('btnLocal').disabled=false; }
}
async function startGitHubBuild(){
  resetUi(); const fd=new FormData(); fd.append('token', el('ghToken').value.trim()); fd.append('repo', el('ghRepo').value.trim()||'nonton');
  if(!fd.get('token')){ alert('Masukkan token GitHub dulu.'); return; }
  setStatus('Mengirim perintah build GitHub Actions...');
  try{ const r=await fetch('/api/github_build',{method:'POST',body:fd}); const d=await r.json(); if(!d.success) throw new Error(d.error||'Gagal start'); currentJob=d.job_id; el('job').textContent=currentJob; poll(); }
  catch(e){ setStatus('❌ '+e.message,'err'); }
}
async function checkEnv(){
  resetUi(); setStatus('Mengecek environment...');
  try{ const r=await fetch('/api/check_env'); const d=await r.json(); el('log').textContent=d.message; setStatus(d.ok?'✅ Environment siap / hampir siap':'⚠️ Ada item yang perlu diperhatikan', d.ok?'ok':'err'); }
  catch(e){ setStatus('❌ '+e.message,'err'); }
}
async function poll(){
  if(timer) clearInterval(timer);
  timer=setInterval(async()=>{
    if(!currentJob) return;
    try{
      const r=await fetch('/api/status/'+currentJob); const d=await r.json();
      if(!d || !Object.keys(d).length) return;
      el('phase').textContent=d.phase||'-'; setProgress(d.progress||0); setStatus(d.status||'-', d.error?'err':(d.apk_url?'ok':''));
      if(d.download_stats){ el('speed').textContent=(d.download_stats.speed||'-')+' / '+(d.download_stats.eta||'-'); }
      else { el('speed').textContent=d.speed_eta||'-'; }
      if(d.log!==undefined){ el('log').textContent=d.log; el('log').scrollTop=el('log').scrollHeight; }
      if(d.apk_url){ el('downloadBox').classList.remove('hidden'); el('downloadLink').href=d.apk_url; el('downloadLink').download='nonton.apk'; clearInterval(timer); el('btnLocal').disabled=false; }
      if(d.error){ clearInterval(timer); el('btnLocal').disabled=false; }
    }catch(e){}
  },1000);
}
function copyLog(){ navigator.clipboard.writeText(el('log').textContent||''); }
</script>
</body>
</html>
"""


def now_stamp() -> str:
    return datetime.now().strftime("%H:%M:%S")


def new_job(prefix: str) -> tuple[str, dict]:
    job_id = f"{prefix}_{int(time.time())}_{len(jobs)+1}"
    job = {
        "job_id": job_id,
        "phase": "INIT",
        "status": "Starting...",
        "progress": 1,
        "log": f"[{now_stamp()}] {APP_VERSION}\n[{now_stamp()}] Project root: {PROJECT_ROOT}\n",
        "download_stats": None,
        "speed_eta": "-",
    }
    jobs[job_id] = job
    return job_id, job


def append_log(job: dict, message: str) -> None:
    if not message:
        return
    lines = str(message).replace("\r\n", "\n").replace("\r", "\n").split("\n")
    stamped = []
    for line in lines:
        if line == "":
            continue
        stamped.append(f"[{now_stamp()}] {line}")
    if stamped:
        job["log"] = (job.get("log", "") + "\n".join(stamped) + "\n")[-120000:]


def set_phase(job: dict, phase: str, status: str | None = None, progress: int | float | None = None) -> None:
    job["phase"] = phase
    if status is not None:
        job["status"] = status
        append_log(job, status)
    if progress is not None:
        job["progress"] = progress


def format_bytes(value: float | int) -> str:
    n = float(value or 0)
    for unit in ["B", "KB", "MB", "GB", "TB"]:
        if n < 1024 or unit == "TB":
            return f"{n:.1f} {unit}"
        n /= 1024
    return f"{n:.1f} TB"


def update_download(job: dict, filename: str, downloaded: int, total: int, started: float, force: bool = False) -> None:
    elapsed = max(0.1, time.time() - started)
    speed = downloaded / elapsed
    percent = (downloaded * 100 / total) if total else 0
    eta = "--"
    if total and speed > 0:
        rem = max(0, total - downloaded) / speed
        eta = f"{int(rem // 60)}m {int(rem % 60)}s"
    job["download_stats"] = {
        "filename": filename,
        "downloaded": downloaded,
        "total": total,
        "percent": round(percent, 1),
        "speed": f"{format_bytes(speed)}/s",
        "eta": eta,
    }
    job["speed_eta"] = f"{format_bytes(speed)}/s / {eta}"
    last = job.get("_last_download_log", 0)
    if force or time.time() - last > 1.0:
        append_log(job, f"📥 {filename}: {format_bytes(downloaded)} / {format_bytes(total)} ({percent:.1f}%) @ {format_bytes(speed)}/s ETA {eta}")
        job["_last_download_log"] = time.time()


def run_capture(cmd, cwd: str | Path | None = None, timeout: int = 60, env: dict | None = None) -> tuple[bool, str, int]:
    try:
        shell = isinstance(cmd, str)
        run_env = os.environ.copy()
        if env:
            run_env.update(env)
        result = subprocess.run(
            cmd,
            cwd=str(cwd) if cwd else None,
            shell=shell,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
            env=run_env,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0,
        )
        return result.returncode == 0, result.stdout or "", result.returncode
    except Exception as exc:
        return False, str(exc), 999


def run_stream(cmd, job: dict, cwd: str | Path | None = None, timeout: int = 900, env: dict | None = None, input_text: str | None = None) -> tuple[bool, str, int]:
    append_log(job, f"$ {cmd if isinstance(cmd, str) else ' '.join(map(str, cmd))}")
    shell = isinstance(cmd, str)
    run_env = os.environ.copy()
    if env:
        run_env.update(env)
    output_tail: list[str] = []
    start = time.time()
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=str(cwd) if cwd else None,
            shell=shell,
            stdin=subprocess.PIPE if input_text else None,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            env=run_env,
            bufsize=1,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0,
        )
        if input_text and proc.stdin:
            try:
                proc.stdin.write(input_text)
                proc.stdin.close()
            except Exception:
                pass
        assert proc.stdout is not None
        while True:
            if timeout and time.time() - start > timeout:
                proc.kill()
                append_log(job, f"❌ Timeout setelah {timeout} detik. Proses dihentikan.")
                return False, "\n".join(output_tail[-400:]), 124
            line = proc.stdout.readline()
            if line:
                clean = line.rstrip("\n")
                output_tail.append(clean)
                output_tail = output_tail[-500:]
                append_log(job, clean)
            elif proc.poll() is not None:
                break
            else:
                time.sleep(0.05)
        code = proc.wait()
        append_log(job, f"Command exit code: {code}")
        return code == 0, "\n".join(output_tail[-500:]), code
    except Exception as exc:
        append_log(job, f"❌ Command error: {exc}")
        return False, str(exc), 999


def cmd_quote(arg: str | Path) -> str:
    s = str(arg)
    return '"' + s.replace('"', '""') + '"'


def windows_cmd(exe: str | Path, args: list[str | Path]) -> str:
    return 'cmd /c "' + cmd_quote(exe) + (" " + " ".join(cmd_quote(a) for a in args) if args else "") + '"'


def any_exists(*paths: Path) -> tuple[bool, str]:
    for path in paths:
        if path.exists():
            return True, str(path)
    return False, " / ".join(str(p) for p in paths)


def project_checks() -> list[tuple[str, bool, str]]:
    checks: list[tuple[str, bool, str]] = []
    flexible = [
        ("settings.gradle(.kts)", PROJECT_ROOT / "settings.gradle", PROJECT_ROOT / "settings.gradle.kts"),
        ("root build.gradle(.kts)", PROJECT_ROOT / "build.gradle", PROJECT_ROOT / "build.gradle.kts"),
        ("app/build.gradle(.kts)", PROJECT_ROOT / "app" / "build.gradle", PROJECT_ROOT / "app" / "build.gradle.kts"),
        ("MainActivity Kotlin/Java", PROJECT_ROOT / "app" / "src" / "main" / "java" / "com" / "jeager22" / "nonton" / "MainActivity.kt", PROJECT_ROOT / "app" / "src" / "main" / "java" / "com" / "jeager22" / "nonton" / "MainActivity.java"),
    ]
    for label, *paths in flexible:
        ok, where = any_exists(*paths)
        checks.append((label, ok, where))

    required = [
        ("gradlew.bat", PROJECT_ROOT / "gradlew.bat"),
        ("gradlew", PROJECT_ROOT / "gradlew"),
        ("gradle-wrapper.jar", PROJECT_ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"),
        ("gradle-wrapper.properties", PROJECT_ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"),
        ("AndroidManifest.xml", PROJECT_ROOT / "app" / "src" / "main" / "AndroidManifest.xml"),
        ("assets/app/index.html", PROJECT_ROOT / "app" / "src" / "main" / "assets" / "app" / "index.html"),
        ("GitHub Actions workflow", PROJECT_ROOT / ".github" / "workflows" / "android-build.yml"),
    ]
    for label, path in required:
        checks.append((label, path.exists(), str(path)))
    jar_path = PROJECT_ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"
    jar_ok = False
    if jar_path.exists():
        try:
            with zipfile.ZipFile(jar_path) as zf:
                jar_ok = "org/gradle/wrapper/GradleWrapperMain.class" in zf.namelist()
        except Exception:
            jar_ok = False
    checks.append(("GradleWrapperMain.class inside wrapper jar", jar_ok, str(jar_path)))
    return checks


def validate_project(job: dict) -> None:
    set_phase(job, "PREFLIGHT", "Memvalidasi struktur project Android...", 8)
    bad = []
    for label, ok, path in project_checks():
        append_log(job, f"{'✅' if ok else '❌'} {label}: {path}")
        if not ok:
            bad.append(label)
    if bad:
        raise RuntimeError("Project belum lengkap: " + ", ".join(bad))


def java_info() -> tuple[bool, str]:
    ok, out, _ = run_capture(["java", "-version"], timeout=15)
    text = out.strip()
    return ok, text


def parse_java_major(text: str) -> int | None:
    m = re.search(r'version "(\d+)(?:\.(\d+))?', text or "")
    if not m:
        m = re.search(r'javac\s+(\d+)(?:\.(\d+))?', text or "")
    if not m:
        return None
    first = int(m.group(1))
    if first == 1 and m.group(2):
        return int(m.group(2))
    return first


def require_jdk17(job: dict | None = None) -> tuple[bool, str]:
    ok_java, info = java_info()
    javac_ok, javac_out, _ = run_capture(["javac", "-version"], timeout=15)
    combined = (info or "") + "\n" + (javac_out or "")
    major = parse_java_major(combined)
    if job:
        append_log(job, info or "java -version tidak memberi output")
        append_log(job, (javac_out or "javac tidak ditemukan").strip())
    if not ok_java or not javac_ok:
        return False, "Java/JDK lengkap tidak ditemukan. Install JDK 17 lalu masukkan ke PATH."
    if major is None or major < 17:
        return False, f"Project NONTON asli memakai Android Gradle Plugin 8.5.2 dan Kotlin; butuh JDK 17. Terdeteksi: {combined.strip()}"
    return True, combined.strip()


def path_has_platform(sdk: Path) -> bool:
    return (sdk / "platforms" / f"android-{ANDROID_API}" / "android.jar").exists()


def path_has_build_tools(sdk: Path) -> bool:
    if os.name == "nt":
        return (sdk / "build-tools" / BUILD_TOOLS / "aapt2.exe").exists() or (sdk / "build-tools" / BUILD_TOOLS / "aapt.exe").exists()
    return (sdk / "build-tools" / BUILD_TOOLS / "aapt2").exists() or (sdk / "build-tools" / BUILD_TOOLS / "aapt").exists()


def sdkmanager_path(sdk: Path) -> Path:
    name = "sdkmanager.bat" if os.name == "nt" else "sdkmanager"
    return sdk / "cmdline-tools" / "latest" / "bin" / name


def sdk_candidates() -> list[Path]:
    home = Path.home()
    cands: list[Path] = []
    if os.name == "nt":
        cands.append(Path("C:/Android/Sdk"))
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        val = os.environ.get(var)
        if val:
            cands.append(Path(val))
    if os.name == "nt":
        local = os.environ.get("LOCALAPPDATA")
        user = os.environ.get("USERPROFILE")
        if local:
            cands.append(Path(local) / "Android" / "Sdk")
        if user:
            cands.append(Path(user) / "AppData" / "Local" / "Android" / "Sdk")
            cands.append(Path(user) / "android-sdk")
    else:
        cands.extend([home / "Android" / "Sdk", home / "android-sdk", Path("/opt/android-sdk")])
    # preserve order, remove duplicates/empty
    seen = set()
    out = []
    for c in cands:
        try:
            key = str(c.resolve()) if c.exists() else str(c)
        except Exception:
            key = str(c)
        if key not in seen and str(c) not in (".", ""):
            seen.add(key)
            out.append(c)
    return out


def commandline_tools_url() -> str:
    if os.name == "nt":
        return "https://dl.google.com/android/repository/commandlinetools-win-9477386_latest.zip"
    if sys.platform == "darwin":
        return "https://dl.google.com/android/repository/commandlinetools-mac-9477386_latest.zip"
    return "https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip"


def download_file(url: str, dest: Path, job: dict, label: str) -> None:
    started = time.time()
    with requests.get(url, stream=True, timeout=300) as r:
        r.raise_for_status()
        total = int(r.headers.get("content-length", 0) or 0)
        done = 0
        with dest.open("wb") as f:
            for chunk in r.iter_content(chunk_size=1024 * 128):
                if chunk:
                    f.write(chunk)
                    done += len(chunk)
                    update_download(job, label, done, total, started)
    update_download(job, label, dest.stat().st_size, dest.stat().st_size, started, force=True)


def install_cmdline_tools(sdk: Path, job: dict, force: bool = False) -> Path:
    set_phase(job, "SDK TOOLS", "Menyiapkan Android command-line tools...", 18)
    sdk.mkdir(parents=True, exist_ok=True)
    manager = sdkmanager_path(sdk)
    if manager.exists() and not force:
        append_log(job, f"✅ sdkmanager ditemukan: {manager}")
        return manager
    if force and manager.exists():
        append_log(job, "⚠️ Reinstall command-line tools kompatibel Java 11/17 karena sdkmanager lama/baru bermasalah...")
        shutil.rmtree(manager.parent.parent, ignore_errors=True)

    url = commandline_tools_url()
    zip_path = sdk / "cmdline-tools.zip"
    tmp = sdk / "cmdline-tools-tmp"
    append_log(job, f"Download: {url}")
    download_file(url, zip_path, job, "android-commandline-tools.zip")

    shutil.rmtree(tmp, ignore_errors=True)
    tmp.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(tmp)
    zip_path.unlink(missing_ok=True)

    latest = sdk / "cmdline-tools" / "latest"
    shutil.rmtree(latest, ignore_errors=True)
    latest.parent.mkdir(parents=True, exist_ok=True)

    src = tmp / "cmdline-tools"
    if src.exists():
        shutil.move(str(src), str(latest))
    else:
        latest.mkdir(parents=True, exist_ok=True)
        for item in tmp.iterdir():
            shutil.move(str(item), str(latest / item.name))
    shutil.rmtree(tmp, ignore_errors=True)

    manager = sdkmanager_path(sdk)
    if os.name != "nt":
        try:
            manager.chmod(0o755)
        except Exception:
            pass
    if not manager.exists():
        raise RuntimeError(f"sdkmanager tidak ditemukan setelah extract: {manager}")
    append_log(job, f"✅ sdkmanager ready: {manager}")
    return manager


def run_sdkmanager(sdk: Path, manager: Path, args: list[str], job: dict, timeout: int = 600, accept_input: bool = False) -> tuple[bool, str]:
    if os.name == "nt":
        cmd = windows_cmd(manager, [f"--sdk_root={sdk}"] + args)
    else:
        cmd = [str(manager), f"--sdk_root={sdk}"] + args
    ok, out, _ = run_stream(cmd, job=job, timeout=timeout, input_text=("y\n" * 80 if accept_input else None))
    return ok, out


def ensure_android_sdk(job: dict) -> Path:
    set_phase(job, "SDK DETECT", "Mendeteksi Android SDK...", 14)
    candidates = sdk_candidates()
    # Fallback mengikuti prioritas kandidat pertama: Windows C:/Android/Sdk lebih dulu; di Linux/mac env ANDROID_HOME/ANDROID_SDK_ROOT lebih dulu.
    chosen: Path | None = candidates[0] if candidates else None
    for sdk in candidates:
        append_log(job, f"Cek SDK kandidat: {sdk}")
        if path_has_platform(sdk) and path_has_build_tools(sdk):
            append_log(job, f"✅ SDK lengkap ditemukan: {sdk}")
            return sdk
        if sdk.exists() and (sdkmanager_path(sdk).exists() or (sdk / "platform-tools").exists() or (sdk / "cmdline-tools").exists()):
            chosen = chosen or sdk
    if chosen is None:
        if os.name == "nt":
            chosen = Path(os.environ.get("USERPROFILE", str(Path.home()))) / "android-sdk"
        else:
            chosen = Path.home() / "android-sdk"
    append_log(job, f"SDK akan dipakai/disiapkan di: {chosen}")

    manager = install_cmdline_tools(chosen, job)
    set_phase(job, "SDK LICENSE", "Menerima lisensi Android SDK...", 24)
    ok_lic, out_lic = run_sdkmanager(chosen, manager, ["--licenses"], job, timeout=240, accept_input=True)
    set_phase(job, "SDK PACKAGES", f"Install platform-tools, android-{ANDROID_API}, build-tools {BUILD_TOOLS}...", 28)
    ok, out_inst = run_sdkmanager(chosen, manager, ["platform-tools", f"platforms;android-{ANDROID_API}", f"build-tools;{BUILD_TOOLS}"], job, timeout=900, accept_input=True)
    combined_sdk_out = (out_lic or "") + "\n" + (out_inst or "")
    if (not ok or not ok_lic) and "UnsupportedClassVersionError" in combined_sdk_out:
        append_log(job, "⚠️ sdkmanager butuh Java lebih baru. Smarty akan reinstall command-line tools versi kompatibel lalu retry...")
        manager = install_cmdline_tools(chosen, job, force=True)
        ok_lic, out_lic = run_sdkmanager(chosen, manager, ["--licenses"], job, timeout=240, accept_input=True)
        ok, out_inst = run_sdkmanager(chosen, manager, ["platform-tools", f"platforms;android-{ANDROID_API}", f"build-tools;{BUILD_TOOLS}"], job, timeout=900, accept_input=True)
    if not ok:
        append_log(job, "⚠️ sdkmanager mengembalikan error. Build tetap dicoba jika package sudah ada.")
    if not path_has_platform(chosen):
        raise RuntimeError(f"Android platform android-{ANDROID_API} belum tersedia di {chosen}")
    if not path_has_build_tools(chosen):
        raise RuntimeError(f"Build-tools {BUILD_TOOLS} belum tersedia di {chosen}")
    return chosen


def write_local_properties(sdk: Path, job: dict) -> None:
    sdk_dir = str(sdk).replace("\\", "/")
    local_props = PROJECT_ROOT / "local.properties"
    local_props.write_text(f"sdk.dir={sdk_dir}\n", encoding="utf-8")
    append_log(job, f"✅ local.properties ditulis: sdk.dir={sdk_dir}")


def gradle_command() -> str | list[str]:
    if os.name == "nt":
        return windows_cmd(PROJECT_ROOT / "gradlew.bat", ["clean", "assembleDebug", "--stacktrace", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx768m"])
    gradlew = PROJECT_ROOT / "gradlew"
    try:
        gradlew.chmod(0o755)
    except Exception:
        pass
    return ["./gradlew", "clean", "assembleDebug", "--stacktrace", "--no-daemon", "-Dorg.gradle.jvmargs=-Xmx768m"]


def find_apk() -> Path | None:
    apk_dir = PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "debug"
    if not apk_dir.exists():
        return None
    apks = sorted(apk_dir.glob("*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
    return apks[0] if apks else None


def local_build_worker(job_id: str) -> None:
    job = jobs[job_id]
    try:
        validate_project(job)
        set_phase(job, "JAVA", "Mengecek Java/JDK...", 10)
        jdk_ok, jdk_msg = require_jdk17(job)
        if not jdk_ok:
            raise RuntimeError(jdk_msg)

        sdk = ensure_android_sdk(job)
        os.environ["ANDROID_HOME"] = str(sdk)
        os.environ["ANDROID_SDK_ROOT"] = str(sdk)
        gradle_home = Path.home() / ".gradle-nonton"
        temp_dir = PROJECT_ROOT / ".tmp"
        gradle_home.mkdir(parents=True, exist_ok=True)
        temp_dir.mkdir(parents=True, exist_ok=True)
        os.environ.setdefault("GRADLE_USER_HOME", str(gradle_home))
        os.environ["GRADLE_OPTS"] = (os.environ.get("GRADLE_OPTS", "") + f" -Djava.io.tmpdir={temp_dir}").strip()
        append_log(job, f"✅ GRADLE_USER_HOME: {os.environ.get('GRADLE_USER_HOME')}")
        append_log(job, f"✅ Gradle temp dir: {temp_dir}")
        write_local_properties(sdk, job)

        set_phase(job, "GRADLE", "Menjalankan Gradle clean assembleDebug...", 45)
        ok, tail, code = run_stream(gradle_command(), job, cwd=PROJECT_ROOT, timeout=1800)
        if not ok:
            job["progress"] = 95
            raise RuntimeError(f"Gradle build failed (exit code {code}). Lihat log lengkap di bawah.")

        set_phase(job, "APK", "Mencari dan menyalin APK...", 96)
        apk = find_apk()
        if not apk:
            raise RuntimeError("Build selesai tetapi APK tidak ditemukan di app/build/outputs/apk/debug")
        root_apk = PROJECT_ROOT / "nonton.apk"
        shutil.copy2(apk, root_apk)
        TEMP_APKS[job_id] = str(root_apk)
        job["apk_url"] = f"/download/{job_id}"
        set_phase(job, "COMPLETE", f"✅ LOCAL BUILD COMPLETE! APK ready: {root_apk}", 100)
        job["download_stats"] = None
    except Exception as exc:
        job["error"] = str(exc)
        job["status"] = "❌ " + str(exc)
        append_log(job, "❌ " + str(exc))


def github_headers(token: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Nonton-Smarty-Builder",
    }


def ensure_github_repo(token: str, repo: str, job: dict) -> str:
    headers = github_headers(token)
    r = requests.get("https://api.github.com/user", headers=headers, timeout=30)
    if r.status_code != 200:
        raise RuntimeError(f"Token GitHub tidak valid / scope kurang. HTTP {r.status_code}: {r.text[:180]}")
    username = r.json()["login"]
    append_log(job, f"✅ Authenticated as GitHub user: {username}")
    url = f"https://api.github.com/repos/{username}/{repo}"
    r = requests.get(url, headers=headers, timeout=30)
    if r.status_code == 404:
        append_log(job, f"Repo {repo} belum ada. Membuat repo baru...")
        cr = requests.post("https://api.github.com/user/repos", headers=headers, json={"name": repo, "private": False, "auto_init": False}, timeout=30)
        if cr.status_code not in (200, 201):
            raise RuntimeError(f"Gagal membuat repo. HTTP {cr.status_code}: {cr.text[:200]}")
    elif r.status_code != 200:
        raise RuntimeError(f"Gagal cek repo. HTTP {r.status_code}: {r.text[:200]}")
    return username


def git_run(args: list[str], job: dict, timeout: int = 180, hide: bool = False) -> tuple[bool, str, int]:
    display = "git " + " ".join("***" if hide and "token" in a.lower() else a for a in args)
    append_log(job, "$ " + display)
    ok, out, code = run_capture(["git"] + args, cwd=PROJECT_ROOT, timeout=timeout)
    if out:
        append_log(job, out[-6000:])
    return ok, out, code


def push_to_github(token: str, username: str, repo: str, job: dict) -> None:
    ok, out, _ = run_capture(["git", "--version"], timeout=15)
    if not ok:
        raise RuntimeError("Git tidak ditemukan. Install Git for Windows untuk memakai metode GitHub Actions.")
    git_run(["init"], job)
    git_run(["checkout", "-B", "main"], job)
    git_run(["config", "user.name", "Nonton Smarty Builder"], job)
    git_run(["config", "user.email", "builder@nonton.local"], job)
    git_run(["add", "."], job)
    ok, out, code = git_run(["commit", "-m", f"Smarty fixed build {datetime.now().isoformat(timespec='seconds')}"], job)
    if not ok and "nothing to commit" not in out.lower():
        raise RuntimeError("Git commit gagal: " + out[-500:])
    git_run(["remote", "remove", "origin"], job)
    remote = f"https://x-access-token:{token}@github.com/{username}/{repo}.git"
    ok, out, _ = git_run(["remote", "add", "origin", remote], job, hide=True)
    if not ok:
        raise RuntimeError("Gagal set remote GitHub")
    ok, out, code = git_run(["push", "-u", "origin", "main", "--force"], job, timeout=300, hide=True)
    git_run(["remote", "set-url", "origin", f"https://github.com/{username}/{repo}.git"], job)
    if not ok:
        raise RuntimeError("Git push gagal: " + out[-1200:])


def github_build_worker(job_id: str, token: str, repo: str) -> None:
    job = jobs[job_id]
    try:
        validate_project(job)
        set_phase(job, "GITHUB AUTH", "Autentikasi GitHub dan memastikan repo...", 12)
        username = ensure_github_repo(token, repo, job)
        set_phase(job, "GIT PUSH", "Push project lengkap ke GitHub...", 28)
        push_to_github(token, username, repo, job)

        headers = github_headers(token)
        set_phase(job, "ACTIONS", "Trigger GitHub Actions workflow...", 48)
        dispatch_url = f"https://api.github.com/repos/{username}/{repo}/actions/workflows/android-build.yml/dispatches"
        dr = requests.post(dispatch_url, headers=headers, json={"ref": "main"}, timeout=30)
        if dr.status_code not in (204, 201, 200):
            raise RuntimeError(f"Gagal dispatch workflow. HTTP {dr.status_code}: {dr.text[:300]}")
        append_log(job, "✅ Workflow dispatched. Menunggu runner GitHub...")

        runs_url = f"https://api.github.com/repos/{username}/{repo}/actions/workflows/android-build.yml/runs?branch=main&per_page=5"
        selected_run = None
        for attempt in range(80):
            time.sleep(6)
            rr = requests.get(runs_url, headers=headers, timeout=30)
            if rr.status_code != 200:
                append_log(job, f"⚠️ Poll runs HTTP {rr.status_code}: {rr.text[:160]}")
                continue
            runs = rr.json().get("workflow_runs", [])
            if not runs:
                append_log(job, "Menunggu workflow run muncul...")
                continue
            run = runs[0]
            selected_run = run
            status = run.get("status")
            conclusion = run.get("conclusion")
            job["progress"] = min(92, 52 + attempt * 0.7)
            append_log(job, f"Run #{run.get('run_number')} status={status} conclusion={conclusion}")
            if status == "completed":
                if conclusion != "success":
                    raise RuntimeError(f"GitHub Actions gagal: conclusion={conclusion}. Buka tab Actions di repo untuk log detail.")
                break
        if not selected_run or selected_run.get("status") != "completed":
            raise RuntimeError("Timeout menunggu GitHub Actions selesai.")

        set_phase(job, "ARTIFACT", "Mengunduh APK artifact dari GitHub Actions...", 94)
        artifacts = requests.get(selected_run["artifacts_url"], headers=headers, timeout=30)
        if artifacts.status_code != 200:
            raise RuntimeError(f"Gagal list artifacts: HTTP {artifacts.status_code}")
        items = artifacts.json().get("artifacts", [])
        if not items:
            raise RuntimeError("Workflow sukses tapi artifact APK tidak ditemukan.")
        artifact = next((a for a in items if "apk" in a.get("name", "").lower()), items[0])
        download_url = artifact["archive_download_url"]
        zip_dest = Path(tempfile.gettempdir()) / f"nonton_artifact_{job_id}.zip"
        started = time.time()
        with requests.get(download_url, headers=headers, stream=True, timeout=300) as r:
            r.raise_for_status()
            total = int(r.headers.get("content-length", 0) or 0)
            done = 0
            with zip_dest.open("wb") as f:
                for chunk in r.iter_content(1024 * 128):
                    if chunk:
                        f.write(chunk)
                        done += len(chunk)
                        update_download(job, artifact.get("name", "artifact.zip"), done, total, started)
        extract_dir = Path(tempfile.gettempdir()) / f"nonton_artifact_{job_id}"
        shutil.rmtree(extract_dir, ignore_errors=True)
        extract_dir.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(zip_dest) as zf:
            zf.extractall(extract_dir)
        apks = list(extract_dir.rglob("*.apk"))
        if not apks:
            raise RuntimeError("Artifact terunduh tetapi tidak berisi APK.")
        root_apk = PROJECT_ROOT / "nonton.apk"
        shutil.copy2(apks[0], root_apk)
        TEMP_APKS[job_id] = str(root_apk)
        job["apk_url"] = f"/download/{job_id}"
        job["download_stats"] = None
        set_phase(job, "COMPLETE", f"✅ GITHUB BUILD COMPLETE! APK ready: {root_apk}", 100)
    except Exception as exc:
        job["error"] = str(exc)
        job["status"] = "❌ " + str(exc)
        append_log(job, "❌ " + str(exc))


@app.route("/")
def index():
    return render_template_string(HTML)


@app.route("/health")
def health():
    return jsonify({"ok": True, "version": APP_VERSION, "project_root": str(PROJECT_ROOT)})


@app.route("/api/check_env")
def api_check_env():
    lines = [f"{APP_VERSION}", f"Project root: {PROJECT_ROOT}", "", "PROJECT FILES:"]
    ok = True
    for label, state, path in project_checks():
        lines.append(f"{'OK ' if state else 'BAD'} - {label}: {path}")
        ok = ok and state
    lines.append("\nJAVA/JDK:")
    java_ok, info = java_info()
    lines.append(info if info else "Java tidak ditemukan")
    javac_ok, javac_out, _ = run_capture(["javac", "-version"], timeout=15)
    lines.append((javac_out or "javac tidak ditemukan").strip())
    ok = ok and java_ok and javac_ok
    lines.append("\nANDROID SDK CANDIDATES:")
    any_ready = False
    for sdk in sdk_candidates():
        ready = path_has_platform(sdk) and path_has_build_tools(sdk)
        any_ready = any_ready or ready
        lines.append(f"{'READY' if ready else 'CHECK'} - {sdk} | platform={path_has_platform(sdk)} build-tools={path_has_build_tools(sdk)} sdkmanager={sdkmanager_path(sdk).exists()}")
    lines.append("\nGRADLE:")
    wrapper = PROJECT_ROOT / "gradlew.bat" if os.name == "nt" else PROJECT_ROOT / "gradlew"
    lines.append(f"Wrapper: {wrapper} exists={wrapper.exists()}")
    lines.append("\nCatatan: Jika SDK belum READY, BUILD APK LOKAL akan otomatis download command-line tools dan install package.")
    return jsonify({"ok": bool(ok), "message": "\n".join(lines)})


@app.route("/api/local_build", methods=["POST"])
def api_local_build():
    job_id, _job = new_job("local")
    threading.Thread(target=local_build_worker, args=(job_id,), daemon=True).start()
    return jsonify({"success": True, "job_id": job_id})


@app.route("/api/github_build", methods=["POST"])
def api_github_build():
    token = (request.form.get("token") or "").strip()
    repo = (request.form.get("repo") or "nonton").strip()
    if not token:
        return jsonify({"success": False, "error": "GitHub token kosong"}), 400
    if not re.match(r"^[A-Za-z0-9_.-]+$", repo):
        return jsonify({"success": False, "error": "Nama repo hanya boleh huruf/angka/underscore/dot/dash"}), 400
    job_id, _job = new_job("github")
    threading.Thread(target=github_build_worker, args=(job_id, token, repo), daemon=True).start()
    return jsonify({"success": True, "job_id": job_id})


@app.route("/api/status/<job_id>")
def api_status(job_id: str):
    return jsonify(jobs.get(job_id, {}))


@app.route("/download/<job_id>")
def download(job_id: str):
    path = TEMP_APKS.get(job_id)
    if path and Path(path).exists():
        return send_file(path, as_attachment=True, download_name="nonton.apk")
    return "APK not found", 404


def is_port_open(host: str, port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.4)
        return s.connect_ex((host, port)) == 0


def open_browser_later(port: int, delay: float = 1.4) -> None:
    def _open():
        time.sleep(delay)
        webbrowser.open(f"http://127.0.0.1:{port}")
    threading.Thread(target=_open, daemon=True).start()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5000)
    parser.add_argument("--open-browser", action="store_true")
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()

    print(f"🚀 {APP_VERSION}")
    print(f"📁 Project root: {PROJECT_ROOT}")
    print(f"🌐 URL: http://127.0.0.1:{args.port}")
    if is_port_open("127.0.0.1", args.port):
        print(f"⚠️ Port {args.port} sudah aktif. Jika ini server NONTON lama, tutup window lama atau pakai --port lain.")
    if args.open_browser and not args.no_browser:
        open_browser_later(args.port)
    app.run(host=args.host, port=args.port, debug=False, threaded=True)


if __name__ == "__main__":
    main()
