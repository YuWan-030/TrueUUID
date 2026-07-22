import importlib.util
import os
from pathlib import Path
import queue
import socket
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "test-runtime-matrix.py"
SPEC = importlib.util.spec_from_file_location("trueuuid_runtime_matrix", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
matrix = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = matrix
SPEC.loader.exec_module(matrix)


class RuntimeMatrixHelpersTest(unittest.TestCase):
    def test_record_era_neoforge_runtime_emits_completed_join_results(self) -> None:
        for version in ("1.21.10", "1.21.11"):
            source = (
                matrix.ROOT
                / f"platform/neoforge-{version}/src/main/java/"
                "cn/alini/trueuuid/server/AdapterRuntime.java"
            )
            contents = source.read_text(encoding="utf-8")
            with self.subTest(version=version):
                self.assertIn('Trueuuid.acceptance("result={}', contents)
                self.assertIn('"premium_join"', contents)
                self.assertIn('"offline_fallback"', contents)

    def test_busy_preferred_port_selects_the_next_free_port(self) -> None:
        listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.addCleanup(listener.close)
        listener.bind(("127.0.0.1", 0))
        occupied = listener.getsockname()[1]
        if occupied == 65535:
            self.skipTest("ephemeral listener used the final TCP port")

        selected, reason = matrix.select_loopback_port(occupied)

        self.assertEqual("", reason)
        self.assertIsNotNone(selected)
        self.assertGreater(selected, occupied)

    def test_neoforge_server_mixins_expose_every_terminal_matrix_result(self) -> None:
        sources = sorted(
            matrix.ROOT.glob(
                "platform/neoforge-*/src/main/java/"
                "cn/alini/trueuuid/mixin/server/ServerLoginMixin.java"
            )
        )
        self.assertTrue(sources)
        required = {
            "result=known_deny",
            "result=migration_complete",
            "result=migration_failed",
            "result=migration_rejected",
            "result=migration_timeout",
        }
        for source in sources:
            contents = source.read_text(encoding="utf-8")
            with self.subTest(source=source):
                self.assertEqual(
                    set(), {marker for marker in required if marker not in contents}
                )

    def test_scenarios_use_dependency_safe_order(self) -> None:
        self.assertEqual(
            ["migrate", "premium", "offline", "known-deny"],
            matrix.dependency_order_scenarios(
                ["premium", "offline", "migrate", "known-deny"]
            ),
        )

    def test_only_terminal_acceptance_results_trigger_fast_failure(self) -> None:
        self.assertIsNone(
            matrix.TERMINAL_RESULT_RE.search(
                "TRUEUUID_ACCEPTANCE result=premium_ready player=PremiumUser"
            )
        )
        self.assertEqual(
            "premium_join",
            matrix.TERMINAL_RESULT_RE.search(
                "TRUEUUID_ACCEPTANCE result=premium_join player=PremiumUser"
            ).group(1),
        )

    def test_resume_reuses_only_complete_target_passes(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            summary = Path(raw_tmp) / "summary.tsv"
            summary.write_text(
                "target\tscenario\tport\tresult\tartifact\n"
                "passed\tpremium\t1\tPASS\ta\n"
                "passed\toffline\t1\tPASS\ta\n"
                "failed\tpremium\t2\tPASS\tb\n"
                "failed\toffline\t2\tFAIL\tb\n"
                "partial\tpremium\t3\tPASS\tc\n",
                encoding="utf-8",
            )

            self.assertEqual(
                ["passed"],
                matrix.fully_passed_targets(
                    summary,
                    ["passed", "failed", "partial", "missing"],
                    ["premium", "offline"],
                ),
            )

    def test_resume_combines_complete_targets_from_multiple_runs(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            root = Path(raw_tmp)
            first = root / "first.tsv"
            second = root / "second.tsv"
            a_premium = root / "evidence" / "a" / "premium"
            a_offline = root / "evidence" / "a" / "offline"
            b_premium = root / "evidence" / "b" / "premium"
            b_offline = root / "evidence" / "b" / "offline"
            for evidence in (a_premium, a_offline, b_premium, b_offline):
                evidence.mkdir(parents=True)
            header = "target\tscenario\tport\tresult\tartifact\n"
            first.write_text(
                header
                + f"a\tpremium\t1\tPASS\t{a_premium}\n"
                + f"a\toffline\t1\tPASS\t{a_offline}\n",
                encoding="utf-8",
            )
            second.write_text(
                header
                + f"b\tpremium\t2\tPASS\t{b_premium}\n"
                + f"b\toffline\t2\tPASS\t{b_offline}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                {
                    "a": {"premium": a_premium, "offline": a_offline},
                    "b": {"premium": b_premium, "offline": b_offline},
                },
                matrix.passed_target_evidence(
                    [first, second], ["a", "b"], ["premium", "offline"]
                ),
            )

    def test_resume_preserves_original_evidence_through_legacy_chains(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            root = Path(raw_tmp)
            original = root / "original"
            intermediate = root / "intermediate"
            latest = root / "latest"
            for run in (original, intermediate, latest):
                run.mkdir()
            original_evidence = original / "target" / "premium"
            original_evidence.mkdir(parents=True)
            header = "target\tscenario\tport\tresult\tartifact\n"
            (original / "summary.tsv").write_text(
                header + f"target\tpremium\t1\tPASS\t{original_evidence}\n",
                encoding="utf-8",
            )
            (intermediate / "summary.tsv").write_text(
                header
                + f"target\tpremium\t\tREUSED_PASS\t{original_evidence}\n",
                encoding="utf-8",
            )
            # This is the broken provenance shape emitted by the old runner:
            # the synthetic intermediate directory does not exist, but its
            # sibling summary still points to the original evidence.
            (latest / "summary.tsv").write_text(
                header
                + "target\tpremium\t\tREUSED_PASS\t"
                + f"{intermediate / 'target' / 'premium'}\n",
                encoding="utf-8",
            )

            self.assertEqual(
                {"target": {"premium": original_evidence}},
                matrix.passed_target_evidence(
                    [latest / "summary.tsv"], ["target"], ["premium"]
                ),
            )

    def test_resume_rejects_missing_original_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            summary = Path(raw_tmp) / "summary.tsv"
            summary.write_text(
                "target\tscenario\tport\tresult\tartifact\n"
                "target\tpremium\t1\tPASS\tmissing\n",
                encoding="utf-8",
            )
            self.assertEqual(
                {},
                matrix.passed_target_evidence(
                    [summary], ["target"], ["premium"]
                ),
            )

    def test_client_install_progress_is_safe_and_concise(self) -> None:
        self.assertEqual(
            ("load:forge-1.21.3-53.1.0", "loading forge-1.21.3-53.1.0"),
            matrix.client_progress("[  ..  ] Loading version forge-1.21.3-53.1.0..."),
        )
        self.assertEqual(
            ("forge-post:download_mojmaps", "Forge post-processing download_mojmaps"),
            matrix.client_progress("[  ..  ] Forge post processing: download_mojmaps..."),
        )
        self.assertIsNone(
            matrix.client_progress("[  ..  ] Authenticating user@example.com with Microsoft...")
        )

    def test_server_asset_progress_is_safe_and_concise(self) -> None:
        self.assertEqual(
            ("assets", "downloading Minecraft assets (details stay in server.log)"),
            matrix.server_progress("> Task :platform:neoforge-1.21.11:downloadAssets"),
        )
        self.assertEqual(
            ("artifacts", "resolved 112 Minecraft artifacts"),
            matrix.server_progress("Loaded 112 artifacts from the Minecraft manifest"),
        )
        self.assertEqual(
            ("asset-index:29", "downloading asset index 29"),
            matrix.server_progress("Downloading asset index 29"),
        )

    def test_forge_thread_fatal_is_detected_immediately(self) -> None:
        self.assertIsNotNone(
            matrix.FATAL_CLIENT_RE.search(
                "[21Jul2026 03:14:15.926] [Render thread/FATAL] Mixin failed"
            )
        )

    def test_client_game_log_fatal_is_tailed_incrementally(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            game_log = Path(raw_tmp) / "latest.log"
            runner = matrix.RuntimeMatrix.__new__(matrix.RuntimeMatrix)
            runner.client_game_log = game_log
            runner.client_game_log_position = 0
            game_log.write_text("[Render thread/INFO] Starting\n", encoding="utf-8")
            self.assertIsNone(runner.client_game_fatal())

            with game_log.open("a", encoding="utf-8") as output:
                output.write("[Render thread/FATAL] Failed to load mod\n")
            self.assertIn("Failed to load mod", runner.client_game_fatal())
            self.assertIsNone(runner.client_game_fatal())

    def test_stop_client_snapshots_the_game_log(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            root = Path(raw_tmp)
            game_log = root / "latest.log"
            snapshot = root / "result" / "game.log"
            snapshot.parent.mkdir()
            game_log.write_text("current game log\n", encoding="utf-8")

            runner = matrix.RuntimeMatrix.__new__(matrix.RuntimeMatrix)
            runner.client = None
            runner.client_game_log = game_log
            runner.client_game_log_position = game_log.stat().st_size
            runner.client_game_log_snapshot = snapshot
            runner.stop_client()

            self.assertEqual("current game log\n", snapshot.read_text(encoding="utf-8"))
            self.assertIsNone(runner.client_game_log)
            self.assertIsNone(runner.client_game_log_snapshot)

    def test_world_name_rejects_paths(self) -> None:
        self.assertEqual(
            "safe-world",
            matrix.test_world_paths("forge-1.20.2", "safe-world")[0].name,
        )
        for unsafe in ("../world", "a/b", ".", "..", ""):
            with self.subTest(unsafe=unsafe):
                with self.assertRaises(RuntimeError):
                    matrix.test_world_paths("forge-1.20.2", unsafe)

    def test_migration_seed_only_writes_the_active_world(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            tmp = Path(raw_tmp)
            world = tmp / "active"
            case = tmp / "case"
            world.mkdir()
            case.mkdir()
            (world / "level.dat").touch()

            matrix.seed_migration_data(world, "PremiumUser", case)

            player_uuid = matrix.offline_uuid("PremiumUser")
            self.assertTrue((world / "advancements" / f"{player_uuid}.json").is_file())
            self.assertTrue((world / "stats" / f"{player_uuid}.json").is_file())
            self.assertEqual(["active", "case"], sorted(path.name for path in tmp.iterdir()))

    def test_process_token_stops_detached_descendant(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            log = Path(raw_tmp) / "process.log"
            child_code = (
                "import subprocess; "
                "child=subprocess.Popen(['sleep','60'], start_new_session=True); "
                "child.wait()"
            )
            process = matrix.ManagedProcess(
                "detached-child-test",
                [sys.executable, "-c", child_code],
                log,
                queue.Queue(),
                os.environ.copy(),
                stdin_pipe=False,
            )
            deadline = time.monotonic() + 3.0
            while len(process._token_pids()) < 2 and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertGreaterEqual(len(process._token_pids()), 2)

            process.stop(graceful_stdin=None, grace=0.0)

            self.assertEqual([], process._token_pids())

    def test_stop_does_not_trust_an_empty_token_scan_while_launcher_lives(self) -> None:
        with tempfile.TemporaryDirectory() as raw_tmp:
            process = matrix.ManagedProcess(
                "token-scan-gap-test",
                [sys.executable, "-c", "import time; time.sleep(60)"],
                Path(raw_tmp) / "process.log",
                queue.Queue(),
                os.environ.copy(),
                stdin_pipe=False,
            )
            # Simulate an intermediate launcher that did not preserve the
            # ownership token. The root process group still must be stopped.
            process._token = "not-present-in-the-child-environment"

            process.stop(graceful_stdin=None, grace=0.0)

            self.assertIsNotNone(process.poll())


if __name__ == "__main__":
    unittest.main()
