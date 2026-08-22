# Tencha

Tencha is an unofficial runtime extension for the Android LINE client.
It is not affiliated with or endorsed by LY Corporation, LINE, Google, or the Vector project.

The implementation is derived from the GPL-3.0 project
[2b-zipper/Knot](https://github.com/2b-zipper/Knot), development commit
`dc271f2a35feb39677ccffe024f8241ef327141f`. The original `LICENSE` is retained and the
combined source is distributed under GPL-3.0.

Tencha uses its own product name, settings storage, control
provider, management UI, diagnostics, compatibility gates, and visual identity. The upstream
project name and logo are not used as Tencha product branding. This notice preserves source
provenance only; it does not imply endorsement by the upstream project.

The APK modifies another process at runtime. Account restrictions, client breakage, and data
loss are possible. Compatibility status is intentionally conservative: hook registration is
reported as `VERIFYING`, not `WORKING`, until runtime execution is observed.
