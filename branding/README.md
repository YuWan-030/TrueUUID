# Packaged branding

`trueuuid-banner.png` is the canonical 960×325 Mods-screen banner. Adapter
resource roots reference it through relative symlinks so every built JAR uses
the same artwork without storing duplicate PNG files.

`TrueUUID-banner.svg` at the repository root is the separate README banner.

F1xGOD drew the lock icons in
[`BadgeArtwork.java`](../shared/presentation/src/main/java/cn/alini/trueuuid/presentation/BadgeArtwork.java).
Their [artwork terms](lock-icons.txt) allow noncommercial use with credit.
Builds include that note as `META-INF/trueuuid-lock-icons.txt`.
