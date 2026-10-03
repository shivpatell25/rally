# Cross-platform stabilization · October 3, 2026

Preserve the existing design and features. Redesign only tvOS Settings.

1. macOS: replace route-only idle detection with scoped window activity/lifecycle
   monitoring. An AFK overlay must own its full window bounds, preserve underlying
   navigation/focus and consume the wake event. Rebuild only its broken responsive
   composition; validate small/standard/fullscreen entry, wake and resize.
2. tvOS foundation: inspect every focusable component and enclosing viewport.
   Preserve focus scale, reserve overflow margins, prevent parent clipping, and
   ensure focus-driven scrolling has one owner. Add explicit boundary connections
   where geometric focus cannot bridge video, compact controls and sidebar panels.
3. tvOS Home: keep upcoming identities/layout stable through guide transitions,
   remove redundant geometry/style recomputation on horizontal focus, and validate
   paging, guide, rail boundaries, returning focus and native motion.
4. tvOS Settings: one section selector and one content column; group Account,
   Playback, Appearance, Sources/Streaming, Notifications and App/About. Keep every
   provider, addon, sports/team, alert, accessibility, transfer and diagnostic action.
5. tvOS validation: remote-only tests of each destination, every Game View control
   and panel, settings inputs/actions, initial focus/back restoration, focused-frame
   bounds, 1080p/4K scaling and transition screenshots. Fix demonstrated failures.
6. Android: compare installed/published/original-keystore certificates, then enforce
   intentional signing in local/CI builds. Keep package ID and data. Raise version
   code, validate artifact metadata and updater compatibility before install. The
   debug-signed installed population requires an explicit compatibility/migration
   choice; never silently replace its signing identity.
7. Deliver verified local builds and a report separating executed checks from
   physical device/account limits. No release publishing requested in this task.
