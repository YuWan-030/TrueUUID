#!/usr/bin/env python3
"""Run PortableMC with narrow compatibility fixes for acceptance launches.

PortableMC 4.4.1 does not define the ROOT processor variable introduced by
NeoForge 21.11. ROOT is the Minecraft installation root (the parent of the
libraries directory). Keep this shim local to the acceptance launcher instead
of modifying the user's installed PortableMC package.

Forge 49.0.x bootstrap also requires ``libraryDirectory`` to locate the patched
1.20.3 client it generated. The official launcher supplies that system
property; PortableMC 4.4.1 does not.
"""

from portablemc.cli import main
from portablemc.cli import CliRunner
from portablemc.forge import ForgeVersion


_original_finalize = ForgeVersion._finalize_forge_internal


def _finalize_with_root(version, watcher):
    info = version._forge_post_info
    if info is not None:
        info.variables.setdefault("ROOT", str(version.context.libraries_dir.parent.absolute()))
    result = _original_finalize(version, watcher)

    if version.forge_version.startswith("1.20.3-"):
        library_arg = f"-DlibraryDirectory={version.context.libraries_dir.absolute()}"
        merged_jvm_args = version._metadata.setdefault("arguments", {}).setdefault(
            "jvm", []
        )
        if library_arg not in merged_jvm_args:
            merged_jvm_args.append(library_arg)

    return result


_original_process_wait = CliRunner.process_wait


def _wait_and_propagate_exit(runner, process):
    _original_process_wait(runner, process)
    if process.returncode:
        raise SystemExit(process.returncode)


ForgeVersion._finalize_forge_internal = _finalize_with_root
CliRunner.process_wait = _wait_and_propagate_exit


if __name__ == "__main__":
    raise SystemExit(main())
