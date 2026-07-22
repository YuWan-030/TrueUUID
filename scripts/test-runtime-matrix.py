#!/usr/bin/env python3
"""Event-driven runtime acceptance runner for TrueUUID's local login matrix."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import shlex
import signal
import socket
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
import uuid


ROOT = Path(__file__).resolve().parent.parent
TARGETS_FILE = ROOT / "release" / "targets.json"
RESULTS_ROOT = ROOT / "build" / "runtime-acceptance"
READY_RE = re.compile(r'Done \([0-9.]+s\)! For help, type "help"')
CLIENT_CONNECT_RE = re.compile(r"Connecting to 127\.0\.0\.1(?:,|:)\s*[0-9]+")
FATAL_SERVER_RE = re.compile(
    r"Failed to start the minecraft server|Encountered an unexpected exception"
    r"|/FATAL\]|BUILD FAILED|Could not resolve all files"
)
FATAL_CLIENT_RE = re.compile(
    r"(?:\[|/)FATAL\]|Mixin apply failed|Failed to complete lifecycle event"
    r"|Crash report saved to .*-(?:fml|client)\.txt"
)
SCENARIO_MARKERS = {
    "premium": re.compile(r"TRUEUUID_ACCEPTANCE result=premium_join(?:\s|$)"),
    "offline": re.compile(r"TRUEUUID_ACCEPTANCE result=offline_fallback(?:\s|$)"),
    "migrate": re.compile(r"TRUEUUID_ACCEPTANCE result=migration_complete(?:\s|$)"),
    "known-deny": re.compile(r"TRUEUUID_ACCEPTANCE result=known_deny(?:\s|$)"),
}
SCENARIO_EXECUTION_ORDER = ("migrate", "premium", "offline", "known-deny")
TERMINAL_RESULT_RE = re.compile(
    r"TRUEUUID_ACCEPTANCE result=(premium_join|offline_fallback|migration_complete|known_deny"
    r"|migration_failed|migration_rejected|migration_timeout)(?:\s|$)"
)


@dataclass(frozen=True)
class ProcessEvent:
    source: "ManagedProcess"
    sequence: int
    line: str | None


class ManagedProcess:
    """Own one launch tree whose output is streamed to disk and an event queue."""

    def __init__(
        self,
        label: str,
        command: list[str],
        log_path: Path,
        events: queue.Queue[ProcessEvent],
        env: dict[str, str],
        *,
        stdin_pipe: bool,
    ) -> None:
        self.label = label
        self.log_path = log_path
        self.events = events
        self._token = uuid.uuid4().hex
        self._sequence = 0
        self._sequence_lock = threading.Lock()
        log_path.parent.mkdir(parents=True, exist_ok=True)
        self._log = log_path.open("w", encoding="utf-8", errors="replace")
        child_env = env.copy()
        child_env["TRUEUUID_PROCESS_TOKEN"] = self._token
        self.process = subprocess.Popen(
            command,
            cwd=ROOT,
            env=child_env,
            stdin=subprocess.PIPE if stdin_pipe else subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
            start_new_session=True,
        )
        self._reader = threading.Thread(
            target=self._read_output,
            name=f"trueuuid-log-{self.process.pid}",
            daemon=True,
        )
        self._reader.start()

    def _read_output(self) -> None:
        assert self.process.stdout is not None
        try:
            for line in self.process.stdout:
                self._log.write(line)
                self._log.flush()
                with self._sequence_lock:
                    self._sequence += 1
                    sequence = self._sequence
                self.events.put(ProcessEvent(self, sequence, line))
        finally:
            with self._sequence_lock:
                sequence = self._sequence
            self.events.put(ProcessEvent(self, sequence, None))
            self.process.stdout.close()
            self._log.close()

    def sequence(self) -> int:
        with self._sequence_lock:
            return self._sequence

    def poll(self) -> int | None:
        return self.process.poll()

    def _token_pids(self) -> list[int]:
        marker = f"TRUEUUID_PROCESS_TOKEN={self._token}".encode()
        matches: list[int] = []
        proc = Path("/proc")
        try:
            entries = proc.iterdir()
        except OSError:
            return matches
        for entry in entries:
            if not entry.name.isdigit():
                continue
            pid = int(entry.name)
            if pid == os.getpid():
                continue
            try:
                environment = (entry / "environ").read_bytes().split(b"\0")
            except (FileNotFoundError, PermissionError, ProcessLookupError, OSError):
                continue
            if marker in environment:
                matches.append(pid)
        return matches

    def _wait_tree(self, timeout: float) -> bool:
        deadline = time.monotonic() + timeout
        while True:
            root_exited = self.process.poll() is not None
            token_pids = self._token_pids()
            # The launcher itself is authoritative even if an intermediate
            # tool strips our ownership token from a descendant environment.
            # Returning while it is still alive skips every signal below and
            # can leave a Gradle runServer process behind after Ctrl-C.
            if root_exited and not token_pids:
                return True
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return False
            # Bounded process-state polling, not an unconditional lifecycle
            # sleep: return immediately as soon as every token owner exits.
            time.sleep(min(0.05, remaining))

    def _signal_tree(self, sig: signal.Signals) -> None:
        for pid in self._token_pids():
            try:
                os.kill(pid, sig)
            except (ProcessLookupError, PermissionError):
                pass

    def _signal_group(self, sig: signal.Signals) -> None:
        if self.poll() is not None:
            return
        try:
            os.killpg(self.process.pid, sig)
        except ProcessLookupError:
            pass

    def stop(self, *, graceful_stdin: str | None, grace: float) -> None:
        if self.poll() is None and graceful_stdin is not None and self.process.stdin:
            try:
                self.process.stdin.write(graceful_stdin)
                self.process.stdin.flush()
                self.process.stdin.close()
            except (BrokenPipeError, OSError):
                pass
        if not self._wait_tree(grace):
            # Gradle may put its daemon and game JVM in new sessions, so a
            # signal to only the wrapper's process group is insufficient.
            self._signal_tree(signal.SIGINT)
            self._signal_group(signal.SIGINT)
            if not self._wait_tree(8.0):
                self._signal_tree(signal.SIGTERM)
                self._signal_group(signal.SIGTERM)
                if not self._wait_tree(3.0):
                    self._signal_tree(signal.SIGKILL)
                    self._signal_group(signal.SIGKILL)
                    self._wait_tree(3.0)
        self._reader.join(timeout=2.0)

    def kill_now(self) -> None:
        self._signal_tree(signal.SIGKILL)
        self._signal_group(signal.SIGKILL)


class RuntimeMatrix:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.events: queue.Queue[ProcessEvent] = queue.Queue()
        self.build: ManagedProcess | None = None
        self.server: ManagedProcess | None = None
        self.client: ManagedProcess | None = None
        self.current_world_name: str | None = None
        self.current_world_paths: tuple[Path, ...] = ()
        self.current_world_record: Path | None = None
        self.target_artifacts: dict[str, Path] = {}
        self.client_game_log: Path | None = None
        self.client_game_log_position = 0
        self.client_game_log_snapshot: Path | None = None
        self.interrupted = False
        self.failures = 0
        self.targets_by_id = self._load_targets()
        self.scenarios = self._select_scenarios()
        self.requested_targets = self._select_targets()
        self.resume_summaries = self._resolve_resume_summaries()
        self.reuse_evidence = passed_target_evidence(
            self.resume_summaries, self.requested_targets, self.scenarios
        )
        self.reused_targets = [
            target for target in self.requested_targets if target in self.reuse_evidence
        ]
        reused = set(self.reused_targets)
        self.targets = [target for target in self.requested_targets if target not in reused]
        if self.targets:
            require_launch_tools()
        self.run_dir = self._new_run_dir()
        self.summary_path = self.run_dir / "summary.tsv"
        self._summary_file = self.summary_path.open("w", encoding="utf-8", newline="")
        self._summary = csv.writer(self._summary_file, delimiter="\t", lineterminator="\n")
        self._summary.writerow(("target", "scenario", "port", "result", "artifact"))
        self._summary_file.flush()
        if self.resume_summaries:
            for target in self.reused_targets:
                evidence = self.reuse_evidence[target]
                for scenario in self.scenarios:
                    self.write_result(
                        target,
                        scenario,
                        "",
                        "REUSED_PASS",
                        evidence[scenario],
                    )

    @staticmethod
    def _load_targets() -> dict[str, dict[str, object]]:
        try:
            raw = json.loads(TARGETS_FILE.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise SystemExit(f"Could not read {TARGETS_FILE}: {exc}") from exc
        targets: dict[str, dict[str, object]] = {}
        for item in raw.get("targets", []):
            target_id = item.get("id")
            if not isinstance(target_id, str) or target_id in targets:
                raise SystemExit("release/targets.json contains a missing or duplicate target id")
            targets[target_id] = item
        return targets

    def _select_targets(self) -> list[str]:
        selected = (
            list(self.targets_by_id)
            if self.args.targets == "all"
            else split_csv(self.args.targets, "target")
        )
        unknown = [target for target in selected if target not in self.targets_by_id]
        if unknown:
            raise SystemExit(f"Unknown manifest target(s): {', '.join(unknown)}")
        if self.args.start_at:
            if self.args.start_at not in selected:
                raise SystemExit(
                    f"--start-at target is not selected: {self.args.start_at}"
                )
            selected = selected[selected.index(self.args.start_at) :]
        if self.args.port_base + len(selected) - 1 > 65535:
            raise SystemExit("Target ports would exceed 65535; choose a lower --port-base")
        return selected

    def _select_scenarios(self) -> list[str]:
        selected = split_csv(self.args.scenarios, "scenario")
        unknown = [scenario for scenario in selected if scenario not in SCENARIO_MARKERS]
        if unknown:
            raise SystemExit(f"Unknown scenario(s): {', '.join(unknown)}")
        # Migration must see its seeded offline files before a premium join
        # creates the verified-UUID destinations in the shared target world.
        # known-deny must run after a verified join has registered the name.
        return dependency_order_scenarios(selected)

    def _resolve_resume_summaries(self) -> list[Path]:
        resolved: list[Path] = []
        for raw in self.args.resume_from:
            supplied = Path(raw).expanduser()
            candidates = (
                [supplied]
                if supplied.is_absolute()
                else [ROOT / supplied, RESULTS_ROOT / supplied]
            )
            for candidate in candidates:
                summary = candidate / "summary.tsv" if candidate.is_dir() else candidate
                if summary.is_file():
                    canonical = summary.resolve()
                    if canonical not in resolved:
                        resolved.append(canonical)
                    break
            else:
                raise SystemExit(f"Resume summary does not exist: {raw}")
        return resolved

    @staticmethod
    def _new_run_dir() -> Path:
        RESULTS_ROOT.mkdir(parents=True, exist_ok=True)
        stem = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        candidate = RESULTS_ROOT / stem
        suffix = 2
        while candidate.exists():
            candidate = RESULTS_ROOT / f"{stem}-{suffix}"
            suffix += 1
        candidate.mkdir(mode=0o700)
        return candidate

    def write_result(
        self, target: str, scenario: str, port: int | str, result: str, artifact: Path | str
    ) -> None:
        self._summary.writerow((target, scenario, port, result, str(artifact)))
        self._summary_file.flush()

    def case_dir(self, target: str, scenario: str) -> Path:
        path = self.run_dir / target / scenario
        path.mkdir(parents=True, exist_ok=True)
        server_link = path / "server.log"
        if not server_link.exists() and not server_link.is_symlink():
            server_link.symlink_to(Path("..") / "server.log")
        return path

    def drain_events(self) -> None:
        while True:
            try:
                self.events.get_nowait()
            except queue.Empty:
                return

    def start_server(self, target: str, port: int) -> ManagedProcess:
        if self.current_world_name is None:
            raise RuntimeError("server world was not prepared")
        env = os.environ.copy()
        env.update(
            {
                "TRUEUUID_ACCEPTANCE_HOOKS": "1",
                "TRUEUUID_ACCEPTANCE_LOG": "1",
                "TRUEUUID_SERVER_PORT": str(port),
                "TRUEUUID_SERVER_MOTD": f"TrueUUID acceptance {self.run_dir.name}",
                "TRUEUUID_SERVER_LEVEL": self.current_world_name,
            }
        )
        log_path = self.run_dir / target / "server.log"
        self.server = ManagedProcess(
            f"{target} server",
            [str(ROOT / "scripts" / "run-dev-target.sh"), target, "server"],
            log_path,
            self.events,
            env,
            stdin_pipe=True,
        )
        return self.server

    def start_client(
        self, target: str, scenario: str, port: int, log_path: Path
    ) -> ManagedProcess:
        command = [
            str(ROOT / "scripts" / "test-premium-client.sh"),
            "--server",
            f"127.0.0.1:{port}",
        ]
        if scenario == "offline":
            safe_target = re.sub(r"[^A-Za-z0-9]", "", target)
            name = f"TU{safe_target[:8]}Off"
            command.extend(
                ("--offline-name", name, "--offline-uuid", offline_uuid(name))
            )
        elif scenario == "known-deny":
            name = os.environ["TRUEUUID_PREMIUM_NAME"]
            command.extend(("--offline-name", name, "--offline-uuid", offline_uuid(name)))
        command.append(target)
        env = os.environ.copy()
        env["TRUEUUID_ACCEPTANCE_LOG"] = "1"
        env["TRUEUUID_MOD_JAR"] = str(self.target_artifacts[target])
        if scenario == "migrate":
            env["TRUEUUID_TEST_AUTO_CONFIRM_MIGRATION"] = "1"
        test_home = Path(
            env.get("TRUEUUID_TEST_HOME", str(Path.home() / ".local/share/trueuuid-testclient"))
        ).expanduser()
        self.client_game_log = test_home / "work" / target / "logs" / "latest.log"
        self.client_game_log.unlink(missing_ok=True)
        self.client_game_log_position = 0
        self.client_game_log_snapshot = log_path.with_name("game.log")
        self.client = ManagedProcess(
            f"{target} {scenario} client",
            command,
            log_path,
            self.events,
            env,
            stdin_pipe=False,
        )
        return self.client

    def client_game_fatal(self) -> str | None:
        path = self.client_game_log
        if path is None:
            return None
        try:
            size = path.stat().st_size
            if size < self.client_game_log_position:
                self.client_game_log_position = 0
            with path.open("rb") as game_log:
                game_log.seek(self.client_game_log_position)
                data = game_log.read()
                self.client_game_log_position = game_log.tell()
        except (FileNotFoundError, OSError):
            return None
        for raw_line in data.splitlines():
            line = raw_line.decode("utf-8", errors="replace")
            if FATAL_CLIENT_RE.search(line):
                return line.strip()
        return None

    def wait_for_server(self, server: ManagedProcess) -> tuple[bool, str]:
        deadline = time.monotonic() + self.args.startup_timeout
        progress_seen: set[str] = set()
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return False, f"startup timed out after {self.args.startup_timeout:g}s"
            try:
                event = self.events.get(timeout=remaining)
            except queue.Empty:
                return False, f"startup timed out after {self.args.startup_timeout:g}s"
            if event.source is not server:
                continue
            if event.line is None:
                return False, f"server process exited with status {server.process.wait()}"
            progress = server_progress(event.line)
            if progress is not None and progress[0] not in progress_seen:
                progress_seen.add(progress[0])
                print(f"  SERVER {progress[1]}", flush=True)
            if FATAL_SERVER_RE.search(event.line):
                return False, event.line.strip()
            if READY_RE.search(event.line):
                break
        try:
            with socket.create_connection(("127.0.0.1", self.current_port), timeout=2.0):
                return True, ""
        except OSError as exc:
            return False, f"server reported ready but TCP port is unavailable: {exc}"

    def wait_for_marker(
        self,
        server: ManagedProcess,
        client: ManagedProcess,
        scenario: str,
        server_baseline: int,
    ) -> tuple[bool, str]:
        marker = SCENARIO_MARKERS[scenario]
        startup_deadline = time.monotonic() + self.args.client_startup_timeout
        marker_deadline: float | None = None
        progress_seen: set[str] = set()
        while True:
            deadline = marker_deadline if marker_deadline is not None else startup_deadline
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                if marker_deadline is None:
                    return False, (
                        "client did not reach server connection after "
                        f"{self.args.client_startup_timeout:g}s"
                    )
                return False, f"marker timed out after {self.args.timeout:g}s: {marker.pattern}"
            try:
                event = self.events.get(timeout=min(remaining, 0.25))
            except queue.Empty:
                game_fatal = self.client_game_fatal()
                if game_fatal is not None:
                    return False, f"client game fatal: {game_fatal}"
                continue
            if event.source is server:
                if event.line is None:
                    return False, f"server exited with status {server.process.wait()}"
                if FATAL_SERVER_RE.search(event.line):
                    return False, f"server fatal: {event.line.strip()}"
                if event.sequence > server_baseline and marker.search(event.line):
                    return True, ""
                if event.sequence > server_baseline:
                    terminal = TERMINAL_RESULT_RE.search(event.line)
                    if terminal is not None:
                        return False, (
                            f"unexpected result={terminal.group(1)} while waiting for "
                            f"{scenario}"
                        )
            elif event.source is client:
                if event.line is None:
                    if marker_in_log_since(server.log_path, marker, server_baseline):
                        return True, ""
                    return False, f"client exited with status {client.process.wait()} before marker"
                if FATAL_CLIENT_RE.search(event.line):
                    return False, f"client fatal: {event.line.strip()}"
                progress = client_progress(event.line)
                if progress is not None and progress[0] not in progress_seen:
                    progress_seen.add(progress[0])
                    print(f"  CLIENT {progress[1]}", flush=True)
                if marker_deadline is None and CLIENT_CONNECT_RE.search(event.line):
                    marker_deadline = time.monotonic() + self.args.timeout
            game_fatal = self.client_game_fatal()
            if game_fatal is not None:
                return False, f"client game fatal: {game_fatal}"

    def stop_client(self) -> None:
        client, self.client = self.client, None
        if client is not None:
            client.stop(graceful_stdin=None, grace=0.0)
        if self.client_game_log is not None and self.client_game_log_snapshot is not None:
            try:
                if self.client_game_log.is_file():
                    shutil.copy2(self.client_game_log, self.client_game_log_snapshot)
            except OSError as exc:
                print(f"WARNING: could not snapshot client game log: {exc}", file=sys.stderr)
        self.client_game_log = None
        self.client_game_log_snapshot = None
        self.client_game_log_position = 0

    def stop_build(self) -> None:
        build, self.build = self.build, None
        if build is not None:
            build.stop(graceful_stdin=None, grace=0.0)

    def stop_server(self) -> None:
        server, self.server = self.server, None
        if server is not None:
            server.stop(graceful_stdin="stop\n", grace=1.0)
        self.remove_ephemeral_worlds()

    def cleanup(self) -> None:
        self.stop_client()
        self.stop_server()
        self.stop_build()

    def artifact_path(self, target: str) -> Path:
        metadata = self.targets_by_id[target]
        template = metadata.get("artifact")
        if not isinstance(template, str):
            raise RuntimeError(f"target {target} has no artifact path")
        properties = ROOT / "gradle.properties"
        version = ""
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("mod_version="):
                version = line.partition("=")[2].strip()
                break
        if not version:
            raise RuntimeError("gradle.properties has no mod_version")
        artifact = (ROOT / template.replace("%VERSION%", version)).resolve()
        try:
            artifact.relative_to(ROOT.resolve())
        except ValueError as exc:
            raise RuntimeError(f"artifact escapes repository: {artifact}") from exc
        if artifact.suffix != ".jar":
            raise RuntimeError(f"target artifact is not a jar: {artifact}")
        return artifact

    def build_target_artifact(self, target: str) -> tuple[Path | None, str]:
        metadata = self.targets_by_id[target]
        build_task = metadata.get("build_task")
        standalone = metadata.get("standalone", False)
        if not isinstance(standalone, bool):
            return None, f"target {target} has an invalid standalone flag"
        expected_task = "build" if standalone else f":platform:{target}:build"
        if build_task != expected_task:
            return None, f"target {target} has an invalid build_task"
        try:
            artifact = self.artifact_path(target)
        except (OSError, RuntimeError) as exc:
            return None, str(exc)

        # Removing only the manifest-declared generated artifact forces Gradle
        # to package a new jar while preserving incremental compilation.
        try:
            artifact.unlink(missing_ok=True)
        except OSError as exc:
            return None, f"could not remove old target artifact: {exc}"

        build_java = Path(
            os.environ.get(
                "TRUEUUID_BUILD_JAVA",
                os.environ.get(
                    "TRUEUUID_JAVA_HOME", "/usr/lib/jvm/java-21-openjdk-amd64"
                ),
            )
        )
        java = build_java / "bin" / "java"
        if not java.is_file():
            return None, f"JDK 21 not found at {build_java}; set TRUEUUID_BUILD_JAVA"
        env = os.environ.copy()
        env["JAVA_HOME"] = str(build_java)
        env["PATH"] = f"{build_java / 'bin'}:{env.get('PATH', '')}"
        if standalone:
            module = ROOT / "platform" / target
            command = [
                str(module / "gradlew"),
                "-p",
                str(module),
                build_task,
                "-PtrueuuidAcceptanceHooks=true",
                "--no-daemon",
            ]
        else:
            command = [
                str(ROOT / "gradlew"),
                build_task,
                "-PtrueuuidAcceptanceHooks=true",
                "--no-daemon",
            ]
        if os.environ.get("TRUEUUID_OFFLINE"):
            command.append("--offline")
        build_log = self.run_dir / target / "build.log"
        print(f"BUILD {target} with {build_task}", flush=True)
        self.build = ManagedProcess(
            f"{target} build",
            command,
            build_log,
            self.events,
            env,
            stdin_pipe=False,
        )
        build = self.build
        deadline = time.monotonic() + self.args.startup_timeout
        status: int | None = None
        while status is None:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                self.stop_build()
                return None, f"build timed out after {self.args.startup_timeout:g}s"
            try:
                event = self.events.get(timeout=remaining)
            except queue.Empty:
                self.stop_build()
                return None, f"build timed out after {self.args.startup_timeout:g}s"
            if event.source is not build:
                continue
            if event.line is None:
                status = build.process.wait()
            elif event.line.startswith("> Task ") or "BUILD SUCCESSFUL" in event.line:
                print(f"  {event.line.strip()}", flush=True)
        self.build = None
        build.stop(graceful_stdin=None, grace=0.0)
        if status != 0:
            return None, f"build exited with status {status}; see {build_log}"
        if not artifact.is_file():
            return None, f"build did not produce {artifact}"

        snapshot_dir = self.run_dir / target / "artifact"
        snapshot_dir.mkdir(parents=True, exist_ok=True)
        snapshot = snapshot_dir / artifact.name
        shutil.copy2(artifact, snapshot)
        digest = hashlib.sha256(snapshot.read_bytes()).hexdigest()
        (snapshot_dir / "sha256.txt").write_text(
            f"{digest}  {snapshot.name}\n", encoding="ascii"
        )
        self.target_artifacts[target] = snapshot
        print(f"JAR   {target} sha256={digest}", flush=True)

        # Never leave the acceptance-instrumented artifact at the normal
        # manifest path where a later publish command could mistake it for a
        # production build. The immutable run snapshot is the client input.
        artifact.unlink()
        return snapshot, ""

    def prepare_ephemeral_world(self, target: str) -> None:
        safe_run = re.sub(r"[^a-z0-9._-]", "-", self.run_dir.name.lower())
        world_name = f"trueuuid-acceptance-{safe_run}"
        paths = test_world_paths(target, world_name)
        for path in paths:
            if path.exists() or path.is_symlink():
                raise RuntimeError(f"ephemeral world path already exists: {path}")
        self.current_world_name = world_name
        self.current_world_paths = paths
        world_record = self.run_dir / target / "world.txt"
        self.current_world_record = world_record
        world_record.parent.mkdir(parents=True, exist_ok=True)
        world_record.write_text(
            f"level-name={world_name}\nstate=fresh-before-boot\n", encoding="utf-8"
        )
        print(f"WORLD {target} fresh level-name={world_name}", flush=True)

    def active_world(self) -> Path:
        active = [path for path in self.current_world_paths if (path / "level.dat").is_file()]
        if len(active) != 1:
            rendered = ", ".join(str(path) for path in active) or "none"
            raise RuntimeError(f"expected exactly one active ephemeral world, found: {rendered}")
        if self.current_world_record is not None:
            with self.current_world_record.open("a", encoding="utf-8") as record:
                record.write(f"active-path={active[0]}\n")
        return active[0]

    def remove_ephemeral_worlds(self) -> None:
        paths, self.current_world_paths = self.current_world_paths, ()
        self.current_world_name = None
        removed: list[Path] = []
        for path in paths:
            if path.is_symlink():
                print(f"WARNING: refusing to remove symlinked test world: {path}", file=sys.stderr)
                continue
            if path.exists():
                try:
                    shutil.rmtree(path)
                    removed.append(path)
                except OSError as exc:
                    print(f"WARNING: could not remove test world {path}: {exc}", file=sys.stderr)
        if self.current_world_record is not None:
            try:
                with self.current_world_record.open("a", encoding="utf-8") as record:
                    for path in removed:
                        record.write(f"removed-after-stop={path}\n")
            except OSError as exc:
                print(f"WARNING: could not update world audit record: {exc}", file=sys.stderr)
        self.current_world_record = None

    def skip_reason(self, scenario: str) -> str | None:
        if scenario in {"migrate", "known-deny"} and not os.environ.get(
            "TRUEUUID_PREMIUM_NAME"
        ):
            return "TRUEUUID_PREMIUM_NAME is required"
        return None

    def run_target(self, target: str, target_index: int) -> bool:
        preferred_port = self.args.port_base + target_index
        selected_port, reason = select_loopback_port(preferred_port)
        self.current_port = selected_port if selected_port is not None else preferred_port
        runnable: list[str] = []
        for scenario in self.scenarios:
            reason = self.skip_reason(scenario)
            if reason:
                self.write_result(target, scenario, self.current_port, "SKIP", reason)
                print(f"SKIP {target} {scenario}: {reason}", flush=True)
            else:
                runnable.append(scenario)
                self.case_dir(target, scenario)
        if not runnable:
            return True

        if selected_port is None:
            print(f"FAIL {target}: port preflight failed: {reason}", flush=True)
            for scenario in runnable:
                case_dir = self.case_dir(target, scenario)
                self.write_result(target, scenario, self.current_port, "FAIL", case_dir)
                self.failures += 1
            return False
        if selected_port != preferred_port:
            print(
                f"PORT  {target} preferred 127.0.0.1:{preferred_port} is busy; "
                f"using 127.0.0.1:{selected_port}",
                flush=True,
            )

        self.drain_events()
        artifact, reason = self.build_target_artifact(target)
        if artifact is None:
            print(f"FAIL {target}: artifact preflight failed: {reason}", flush=True)
            for scenario in runnable:
                case_dir = self.case_dir(target, scenario)
                self.write_result(target, scenario, self.current_port, "FAIL", case_dir)
                self.failures += 1
            return False
        try:
            self.prepare_ephemeral_world(target)
        except RuntimeError as exc:
            print(f"FAIL {target}: world preflight failed: {exc}", flush=True)
            for scenario in runnable:
                case_dir = self.case_dir(target, scenario)
                self.write_result(target, scenario, self.current_port, "FAIL", case_dir)
                self.failures += 1
            return False

        print(f"BOOT {target} on 127.0.0.1:{self.current_port}", flush=True)
        server = self.start_server(target, self.current_port)
        ready, reason = self.wait_for_server(server)
        if not ready:
            self.stop_server()
            print(f"FAIL {target}: server did not become ready: {reason}", flush=True)
            print_diagnostics(server.log_path, None)
            for scenario in runnable:
                case_dir = self.case_dir(target, scenario)
                self.write_result(target, scenario, self.current_port, "FAIL", case_dir)
                self.failures += 1
            return False
        print(f"READY {target}", flush=True)
        try:
            target_world = self.active_world()
        except (OSError, RuntimeError) as exc:
            self.stop_server()
            print(f"FAIL {target}: active-world verification failed: {exc}", flush=True)
            print_diagnostics(server.log_path, None)
            for scenario in runnable:
                case_dir = self.case_dir(target, scenario)
                self.write_result(target, scenario, self.current_port, "FAIL", case_dir)
                self.failures += 1
            return False
        print(f"ACTIVE {target} world={target_world}", flush=True)

        target_ok = True
        for scenario_index, scenario in enumerate(runnable):
            case_dir = self.case_dir(target, scenario)
            client_log = case_dir / "client.log"
            # Seed immediately before this login. Seeding before the shared
            # target server boots would make an earlier premium scenario see
            # migration data and wait for a confirmation it was not asked to
            # auto-accept.
            if scenario == "migrate":
                try:
                    seed_migration_data(
                        target_world,
                        os.environ["TRUEUUID_PREMIUM_NAME"],
                        case_dir,
                    )
                except (OSError, RuntimeError) as exc:
                    reason = f"could not seed active fresh world: {exc}"
                    print(f"FAIL {target} {scenario}: {reason}", flush=True)
                    self.write_result(target, scenario, self.current_port, "FAIL", case_dir)
                    self.failures += 1
                    target_ok = False
                    if self.args.fail_fast:
                        break
                    continue
            self.drain_events()
            if server.poll() is not None:
                reason = (
                    f"server exited with status {server.process.wait()} between scenarios"
                )
                print(f"FAIL {target} {scenario}: {reason}", flush=True)
                print_diagnostics(server.log_path, client_log)
                self.write_result(target, scenario, self.current_port, "FAIL", case_dir)
                self.failures += 1
                target_ok = False
                for pending in runnable[scenario_index + 1 :]:
                    pending_dir = self.case_dir(target, pending)
                    print(
                        f"FAIL {target} {pending}: target server is unavailable",
                        flush=True,
                    )
                    self.write_result(
                        target, pending, self.current_port, "FAIL", pending_dir
                    )
                    self.failures += 1
                break
            baseline = server.sequence()
            print(f"RUN  {target} {scenario}", flush=True)
            client = self.start_client(target, scenario, self.current_port, client_log)
            passed, reason = self.wait_for_marker(server, client, scenario, baseline)
            if passed:
                print(f"PASS {target} {scenario}", flush=True)
                self.write_result(target, scenario, self.current_port, "PASS", case_dir)
                if self.args.keep_open:
                    print("Marker passed; client and server stay open until Ctrl-C.", flush=True)
                    self.wait_until_process_exit(server, client)
            else:
                print(f"FAIL {target} {scenario}: {reason}", flush=True)
                print_diagnostics(server.log_path, client_log, self.client_game_log)
                self.write_result(target, scenario, self.current_port, "FAIL", case_dir)
                self.failures += 1
                target_ok = False
            self.stop_client()
            if not passed and reason.startswith("server "):
                for pending in runnable[scenario_index + 1 :]:
                    pending_dir = self.case_dir(target, pending)
                    print(
                        f"FAIL {target} {pending}: target server is unavailable",
                        flush=True,
                    )
                    self.write_result(
                        target, pending, self.current_port, "FAIL", pending_dir
                    )
                    self.failures += 1
                break
            if self.args.fail_fast and not passed:
                break
        self.stop_server()
        return target_ok

    def wait_until_process_exit(
        self, server: ManagedProcess, client: ManagedProcess
    ) -> None:
        while True:
            event = self.events.get()
            if event.line is None and event.source in {server, client}:
                return

    def run(self) -> int:
        print(f"TrueUUID runtime acceptance: results in {self.run_dir}", flush=True)
        if self.resume_summaries:
            for summary in self.resume_summaries:
                print(f"Resume source: {summary}", flush=True)
            if self.reused_targets:
                print(
                    "Reusing fully passed targets: " + " ".join(self.reused_targets),
                    flush=True,
                )
        print(f"Targets: {' '.join(self.targets)}", flush=True)
        print(f"Scenarios: {' '.join(self.scenarios)}", flush=True)
        print("Server lifecycle: one boot per target", flush=True)
        try:
            for index, target in enumerate(self.targets):
                target_ok = self.run_target(target, index)
                if self.args.fail_fast and not target_ok:
                    break
        except KeyboardInterrupt:
            self.interrupted = True
            print("\nInterrupted; stopping active client and server...", flush=True)
        finally:
            self.cleanup()
            self._summary_file.close()
        print(f"Summary: {self.summary_path}", flush=True)
        if self.interrupted:
            return 130
        return 1 if self.failures else 0


def split_csv(raw: str, label: str) -> list[str]:
    values = [value.strip() for value in raw.split(",") if value.strip()]
    if not values:
        raise SystemExit(f"At least one {label} is required")
    duplicates = sorted({value for value in values if values.count(value) > 1})
    if duplicates:
        raise SystemExit(f"Duplicate {label}(s): {', '.join(duplicates)}")
    return values


def require_launch_tools() -> None:
    """Validate tools needed only when at least one target will actually run."""
    if shutil.which("jq") is None:
        raise SystemExit("jq is required by the child launcher scripts")
    portablemc = shlex.split(os.environ.get("TRUEUUID_PORTABLEMC", "portablemc"))
    if not portablemc or shutil.which(portablemc[0]) is None:
        raise SystemExit(
            "portablemc is required; install it or set TRUEUUID_PORTABLEMC"
        )


def dependency_order_scenarios(selected: list[str]) -> list[str]:
    selected_set = set(selected)
    return [scenario for scenario in SCENARIO_EXECUTION_ORDER if scenario in selected_set]


def fully_passed_targets(
    summary_path: Path, targets: list[str], scenarios: list[str]
) -> list[str]:
    outcomes: dict[tuple[str, str], list[str]] = {}
    try:
        with summary_path.open(encoding="utf-8", newline="") as summary_file:
            reader = csv.DictReader(summary_file, delimiter="\t")
            required = {"target", "scenario", "result"}
            if reader.fieldnames is None or not required.issubset(reader.fieldnames):
                raise SystemExit(f"Resume summary has an invalid header: {summary_path}")
            for row in reader:
                key = (row["target"], row["scenario"])
                outcomes.setdefault(key, []).append(row["result"])
    except OSError as exc:
        raise SystemExit(f"Could not read resume summary {summary_path}: {exc}") from exc

    reusable: list[str] = []
    passing_results = {"PASS", "REUSED_PASS"}
    for target in targets:
        if all(
            len(outcomes.get((target, scenario), [])) == 1
            and outcomes[(target, scenario)][0] in passing_results
            for scenario in scenarios
        ):
            reusable.append(target)
    return reusable


def passed_target_evidence(
    summary_paths: list[Path], targets: list[str], scenarios: list[str]
) -> dict[str, dict[str, Path]]:
    evidence: dict[str, dict[str, Path]] = {}
    for summary_path in summary_paths:
        for target in fully_passed_targets(summary_path, targets, scenarios):
            resolved = {
                scenario: path
                for scenario in scenarios
                if (path := resolve_pass_evidence(summary_path, target, scenario))
                is not None
            }
            if len(resolved) == len(scenarios):
                evidence[target] = resolved
    return evidence


def resolve_pass_evidence(
    summary_path: Path,
    target: str,
    scenario: str,
    seen: set[tuple[Path, str, str]] | None = None,
) -> Path | None:
    """Resolve a PASS/REUSED_PASS row to an existing original evidence path.

    Older resume summaries synthesized ``<source-run>/<target>/<scenario>``
    instead of retaining the original artifact column. If that synthetic path
    is absent, follow the source run's summary recursively. Cycles, duplicate
    rows, invalid results, and missing evidence fail closed.
    """
    canonical = summary_path.resolve()
    key = (canonical, target, scenario)
    visited = set() if seen is None else seen
    if key in visited:
        return None
    visited.add(key)

    matches: list[dict[str, str]] = []
    try:
        with canonical.open(encoding="utf-8", newline="") as summary_file:
            reader = csv.DictReader(summary_file, delimiter="\t")
            required = {"target", "scenario", "result", "artifact"}
            if reader.fieldnames is None or not required.issubset(reader.fieldnames):
                return None
            matches = [
                row
                for row in reader
                if row["target"] == target and row["scenario"] == scenario
            ]
    except OSError:
        return None

    if len(matches) != 1 or matches[0]["result"] not in {"PASS", "REUSED_PASS"}:
        return None
    raw_artifact = matches[0]["artifact"]
    if not raw_artifact:
        return None
    artifact = Path(raw_artifact).expanduser()
    if not artifact.is_absolute():
        artifact = canonical.parent / artifact
    artifact = artifact.resolve()
    if artifact.exists():
        return artifact

    if (
        matches[0]["result"] == "REUSED_PASS"
        and artifact.name == scenario
        and artifact.parent.name == target
    ):
        source_summary = artifact.parent.parent / "summary.tsv"
        if source_summary.is_file():
            return resolve_pass_evidence(
                source_summary, target, scenario, visited
            )
    return None


def client_progress(line: str) -> tuple[str, str] | None:
    clean = line.strip()
    match = re.search(r"Loading version (.+?)\.\.\.", clean)
    if match:
        version = match.group(1)
        return f"load:{version}", f"loading {version}"
    if "Download starting..." in clean:
        return "download", "downloading missing game/loader files (progress stays in client.log)"
    match = re.search(r"Forge post processing: ([A-Za-z0-9_-]+)", clean)
    if match:
        task = match.group(1)
        return f"forge-post:{task}", f"Forge post-processing {task}"
    return None


def server_progress(line: str) -> tuple[str, str] | None:
    clean = line.strip()
    if re.search(r"> Task :platform:[^:]+:downloadAssets", clean):
        return "assets", "downloading Minecraft assets (details stay in server.log)"
    match = re.search(r"Loaded (\d+) artifacts? from", clean)
    if match:
        return "artifacts", f"resolved {match.group(1)} Minecraft artifacts"
    match = re.search(r"Downloading asset index (\S+)", clean)
    if match:
        return f"asset-index:{match.group(1)}", f"downloading asset index {match.group(1)}"
    return None


def offline_uuid(name: str) -> str:
    digest = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def test_world_paths(target: str, world_name: str) -> tuple[Path, ...]:
    if not re.fullmatch(r"[A-Za-z0-9._-]+", world_name) or world_name in {".", ".."}:
        raise RuntimeError(f"unsafe test world name: {world_name!r}")
    target_root = ROOT / "platform" / target
    return (
        target_root / "run" / world_name,
        target_root / "run" / "server" / world_name,
        target_root / "runs" / "server" / world_name,
    )


def seed_migration_data(world: Path, name: str, case_dir: Path) -> None:
    player_uuid = offline_uuid(name)
    if not (world / "level.dat").is_file():
        raise RuntimeError(f"server world is not active: {world}")
    advancements = world / "advancements"
    stats = world / "stats"
    advancements.mkdir(parents=True, exist_ok=True)
    stats.mkdir(parents=True, exist_ok=True)
    (advancements / f"{player_uuid}.json").write_text("{}\n", encoding="utf-8")
    (stats / f"{player_uuid}.json").write_text(
        '{"stats":{},"DataVersion":0}\n', encoding="utf-8"
    )
    (case_dir / "offline-uuid.txt").write_text(f"{player_uuid}\n", encoding="ascii")


def marker_in_log_since(log_path: Path, marker: re.Pattern[str], baseline: int) -> bool:
    try:
        lines = log_path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return False
    return any(marker.search(line) for line in lines[baseline:])


def tail_lines(path: Path, count: int) -> list[str]:
    try:
        return path.read_text(encoding="utf-8", errors="replace").splitlines()[-count:]
    except OSError:
        return []


def acceptance_lines(path: Path | None) -> list[str]:
    if path is None:
        return []
    return [line for line in tail_lines(path, 400) if "TRUEUUID_ACCEPTANCE" in line][-20:]


def print_diagnostics(
    server_log: Path, client_log: Path | None, client_game_log: Path | None = None
) -> None:
    print("  Server acceptance markers:", flush=True)
    markers = acceptance_lines(server_log)
    print_indented(markers or ["(none)"])
    print("  Client acceptance markers:", flush=True)
    markers = acceptance_lines(client_log)
    print_indented(markers or ["(none)"])
    print("  Server log tail:", flush=True)
    print_indented(tail_lines(server_log, 30) or ["(empty)"])
    if client_log is not None:
        print("  Client log tail:", flush=True)
        print_indented(tail_lines(client_log, 30) or ["(empty)"])
    if client_game_log is not None:
        print("  Client game log tail:", flush=True)
        print_indented(tail_lines(client_game_log, 30) or ["(empty)"])


def print_indented(lines: list[str]) -> None:
    for line in lines:
        print(f"    {line}", flush=True)


def positive_seconds(value: str) -> float:
    try:
        parsed = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("must be a number") from exc
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def valid_port(value: str) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("must be an integer") from exc
    if not 1 <= parsed <= 65535:
        raise argparse.ArgumentTypeError("must be between 1 and 65535")
    return parsed


def select_loopback_port(preferred: int, scan_limit: int = 256) -> tuple[int | None, str]:
    """Select the first bindable loopback port without disturbing its owner."""
    last_error = "no candidate ports"
    upper = min(65535, preferred + scan_limit - 1)
    for port in range(preferred, upper + 1):
        probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            probe.bind(("127.0.0.1", port))
        except OSError as exc:
            last_error = str(exc)
        else:
            return port, ""
        finally:
            probe.close()
    return None, (
        f"no free loopback port in {preferred}-{upper}; last bind error: {last_error}"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run TrueUUID's local premium/offline runtime acceptance matrix.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Scenarios:
  premium     use the cached Microsoft account
  offline     use a target-specific throwaway offline name
  migrate     seed offline data and auto-confirm migration
  known-deny  verify that the premium name cannot fall back offline

The runner starts one server per target and watches process output directly.
It exits as soon as a server/client dies instead of waiting out the timeout.
""",
    )
    parser.add_argument(
        "--targets", default="forge-1.20.1", help='comma-separated ids or "all"'
    )
    parser.add_argument(
        "--start-at",
        metavar="TARGET",
        help="resume the selected target list at this target (inclusive)",
    )
    parser.add_argument(
        "--resume-from",
        action="append",
        default=[],
        metavar="RUN_DIR_OR_SUMMARY",
        help=(
            "reuse targets whose requested scenarios all passed in an earlier "
            "runtime-acceptance summary; repeat to combine runs"
        ),
    )
    parser.add_argument(
        "--scenarios",
        default="premium,offline,migrate,known-deny",
        help="comma-separated scenarios",
    )
    parser.add_argument(
        "--port-base", type=valid_port, default=25565, help="first target port"
    )
    parser.add_argument(
        "--timeout", type=positive_seconds, default=240.0, help="per-login marker timeout"
    )
    parser.add_argument(
        "--startup-timeout",
        type=positive_seconds,
        default=900.0,
        help="per-target first-boot timeout, including asset downloads",
    )
    parser.add_argument(
        "--client-startup-timeout",
        type=positive_seconds,
        default=900.0,
        help="first client install/startup deadline before it connects",
    )
    parser.add_argument("--fail-fast", action="store_true", help="stop after the first failure")
    parser.add_argument(
        "--keep-open",
        action="store_true",
        help="after one passing case, keep its client/server open until Ctrl-C",
    )
    args = parser.parse_args()
    if args.keep_open:
        targets = (
            [
                item["id"]
                for item in json.loads(TARGETS_FILE.read_text())["targets"]
            ]
            if args.targets == "all"
            else split_csv(args.targets, "target")
        )
        if args.start_at:
            if args.start_at not in targets:
                parser.error(f"--start-at target is not selected: {args.start_at}")
            targets = targets[targets.index(args.start_at) :]
        scenarios = split_csv(args.scenarios, "scenario")
        if len(targets) != 1 or len(scenarios) != 1:
            parser.error("--keep-open requires exactly one target and one scenario")
    return args


def install_escalating_signal_handler(runner: RuntimeMatrix) -> None:
    seen = False

    def handler(signum: int, _frame: object) -> None:
        nonlocal seen
        if seen:
            for process in (runner.client, runner.server, runner.build):
                if process is not None:
                    process.kill_now()
            os._exit(128 + signum)
        seen = True
        raise KeyboardInterrupt

    signal.signal(signal.SIGINT, handler)
    signal.signal(signal.SIGTERM, handler)
    # A closed terminal normally delivers SIGHUP rather than SIGINT. Route it
    # through the same cleanup path so server/client sessions are not orphaned.
    if hasattr(signal, "SIGHUP"):
        signal.signal(signal.SIGHUP, handler)


def main() -> int:
    os.umask(0o077)
    args = parse_args()
    runner = RuntimeMatrix(args)
    install_escalating_signal_handler(runner)
    return runner.run()


if __name__ == "__main__":
    sys.exit(main())
