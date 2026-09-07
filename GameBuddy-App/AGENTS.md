# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v57.0.0/ before writing any code.

# Plugin skills

Three user-scope plugins are installed and their skills auto-trigger. How they map onto
this project:

**Use as written**
- `react-native-skills` — list, animation and rendering rules. Its SKILL.md tells you to
  read `rules/<rule>.md`; **those files are not shipped** (the folder holds only
  `_template.md` and `_sections.md`). The real content is the 76 KB `AGENTS.md` in the
  same skill folder.
- `mobile-app-debugging` — the Android / logcat / profiler half. The Xcode half does not
  apply; this ships Android only.
- `ui-design:interaction-design`, `ui-design:visual-design-foundations`,
  `ui-design:accessibility-compliance` (for React Native a11y props, not WCAG-for-web).

**Ignore — wrong platform**
`ui-design:mobile-ios-design` (SwiftUI), `ui-design:mobile-android-design` (Jetpack
Compose), `ui-design:web-component-design`, `ui-design:responsive-design` (CSS container
queries).

**Read for reasoning, not for imports**
`ui-design:react-native-design` targets React Navigation 6, Reanimated 3 and
StyleSheet/styled-components. This app is expo-router, Reanimated 4.5.1 and NativeWind 4.
Its layout and motion thinking transfers; its code does not.

**Where this repo overrides the plugins**
- Styling is NativeWind `className`. `UI_NOTE.md` §4 outranks any styling advice a plugin
  gives — those traps all fail silently and have each cost real time once already.
- Colour comes from role names in `src/theme/tokens.js`, and `npm run check` gates
  contrast. Do not let `ui-design:design-system-patterns` restructure the token layer.
- `react-native-skills` recommends FlashList and Galeria; neither is a dependency. Any
  native module needs a rebuild, and EAS builds are rationed — propose, never install.
- The `/ui-design:*` commands write code. Treat their output as a draft and re-check it
  against `UI_NOTE.md` before keeping it.
