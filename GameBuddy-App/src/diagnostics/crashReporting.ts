import { NativeModules } from 'react-native';

/**
 * Native crash reporting, and the only file in the app that imports Crashlytics.
 *
 * ## Why this exists at all
 *
 * The UI suite caught the app dying to the launcher once in about twenty launches, eleven
 * seconds into startup, on the welcome screen. It was a `SIGSEGV` on the JS thread inside
 * React Native's Fabric renderer — `MountingCoordinator::pullTransaction`, every frame in
 * `libreactnative.so`, no application code anywhere in the backtrace.
 *
 * The thing to understand about that class of failure is that **no JavaScript error handler
 * can see it**. The process is gone before any JS runs, so a `try`/`catch`, an
 * `ErrorBoundary`, or a JS-only reporter records nothing at all. To the person holding the
 * phone the app "just closes"; to us, nothing anywhere says why, and the only trace is a
 * logcat buffer on a device we do not have. That is what this module changes: Crashlytics
 * installs a native NDK signal handler, so the next occurrence arrives as a report with a
 * backtrace instead of as a support message saying the app is broken.
 *
 * It reports; it does not fix. The finding is one occurrence in a debug build, which is not
 * enough to act on — see QA_FINDINGS.md #6. What it turns into is a number, so the decision
 * to chase it can be made on evidence.
 *
 * ## Why the SDK is loaded lazily
 *
 * Native code, exactly like `react-native-purchases` and the AdMob SDK: present in the JS
 * bundle as soon as it is imported, but the native half only exists in a build made after
 * the package was installed. A top-level import would crash every older development build at
 * startup — which would be a poor way to introduce crash reporting. Loading on first use
 * means an older build keeps working, minus this.
 *
 * Note the asymmetry with the rest of the module: the crash *capture* is native and is
 * already running before any of this code executes. Everything below only adds context to a
 * report that would be collected regardless.
 */

type Crashlytics = {
  setUserId(id: string): Promise<void>;
  log(message: string): void;
  recordError(error: Error, jsErrorName?: string): void;
  setAttributes(attributes: Record<string, string>): Promise<void>;
};

let cached: Crashlytics | null | undefined;

/**
 * The Crashlytics instance, or null when this build has no native module for it.
 *
 * Null rather than throwing, everywhere. Losing a breadcrumb is not worth a crash — least of
 * all from the crash reporter.
 */
function crashlytics(): Crashlytics | null {
  if (cached !== undefined) return cached;

  if (!NativeModules.RNFBCrashlyticsModule) {
    cached = null;
    return cached;
  }
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const mod = require('@react-native-firebase/crashlytics');
    cached = (mod.default ? mod.default() : null) as Crashlytics | null;
  } catch {
    cached = null;
  }
  return cached;
}

/** Whether crash reports from this build will reach Crashlytics. */
export function crashReportingAvailable(): boolean {
  return crashlytics() !== null;
}

/**
 * Loads Crashlytics at boot so its JS handlers are installed before anyone signs in.
 *
 * The reason this must run early, and unconditionally, is subtle: the native package installs
 * its global JS-error and unhandled-rejection handlers in its module constructor — the first
 * time {@link crashlytics} requires it — and it captures whatever `ErrorUtils` handler exists
 * at that moment to chain onto. So ours has to be installed first (it is, at module load in
 * `app/_layout.tsx`), and this call has to come right after it. Until this existed, Crashlytics
 * was only loaded once a signed-in user id was set, which left the whole sign-in funnel — the
 * highest-risk surface on day one — blind to JS errors and unhandled rejections. Native crashes
 * were always captured; this is about the JS half.
 *
 * Null-safe on a build without the native module (an older development build): {@link crashlytics}
 * returns null and nothing throws.
 */
export function initCrashReporting(): void {
  if (crashReportingAvailable()) {
    leaveCrashBreadcrumb('boot');
  }
}

/**
 * Ties subsequent crash reports to an account.
 *
 * Worth having because the crash under investigation is intermittent: one report is noise,
 * but "always the same handful of accounts" or "always a first launch" is a lead. The id is
 * the opaque user id the API already uses — never an email or a username, which would put
 * personal data into a third-party dashboard for no diagnostic gain.
 */
export function identifyForCrashReports(userId: string): void {
  void crashlytics()?.setUserId(userId);
}

/**
 * A breadcrumb, attached to whatever crash comes next.
 *
 * The Fabric crash happened during a shadow-tree commit, so the useful question is which
 * screen was mounting. Breadcrumbs answer that where a stack trace full of
 * `libreactnative.so` frames cannot.
 */
export function leaveCrashBreadcrumb(message: string): void {
  crashlytics()?.log(message);
}

/**
 * Records a JavaScript error that was handled, so it is counted rather than lost.
 *
 * Distinct from a crash: the app kept running. Useful for the errors we swallow deliberately
 * — a failed refetch, a malformed payload — which are invisible today.
 */
export function recordHandledError(error: unknown, context?: string): void {
  const reporter = crashlytics();
  if (!reporter) return;
  const err = error instanceof Error ? error : new Error(String(error));
  if (context) reporter.log(context);
  reporter.recordError(err);
}
