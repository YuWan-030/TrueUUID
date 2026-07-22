#!/usr/bin/env python3
"""Run PortableMC with compatibility for newer NeoForge installers.

PortableMC 4.4.1 does not define the ROOT processor variable introduced by
NeoForge 21.11. ROOT is the Minecraft installation root (the parent of the
libraries directory). Keep this shim local to the acceptance launcher instead
of modifying the user's installed PortableMC package.
"""

from portablemc.cli import main
from portablemc.forge import ForgeVersion


_original_finalize = ForgeVersion._finalize_forge_internal


def _finalize_with_root(version, watcher):
    info = version._forge_post_info
    if info is not None:
        info.variables.setdefault("ROOT", str(version.context.libraries_dir.parent.absolute()))
    return _original_finalize(version, watcher)


ForgeVersion._finalize_forge_internal = _finalize_with_root


if __name__ == "__main__":
    raise SystemExit(main())
