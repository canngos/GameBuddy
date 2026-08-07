/**
 * Catches errors that no React error boundary can see.
 *
 * An error boundary only catches throws during render, in a lifecycle method, or in a
 * constructor. An error thrown from a `setTimeout` callback, a promise chain or a native
 * module callback reaches none of those — it goes to React Native's global handler, and
 * in a release build that handler destroys the React instance. The process stays alive,
 * the window stays on screen, and everything in it disappears: a white rectangle with no
 * message, no crash report, and nothing in logcat but the teardown.
 *
 * That is exactly how a single `undefined` in the STOMP client's `debug` option took the
 * whole app down. Its watchdog fired from a timer ten seconds after a socket failed to
 * connect, threw where nothing could catch it, and the app died — in release only, since
 * in development the same option held a real function.
 *
 * So: log everything, and only let React Native tear itself down when the error is
 * genuinely fatal.
 *
 * **The trade-off is deliberate and worth being honest about.** Swallowing a non-fatal
 * error means the app carries on in a state its author did not plan for, which can produce
 * odd behaviour later. The alternative is what we just watched happen — a blank screen
 * that reports nothing and cannot be recovered from without force-quitting. For a
 * background timer failing while somebody is reading their messages, carrying on is the
 * better answer nearly every time.
 */
export function installGlobalErrorHandler(): void {
  // ErrorUtils is a React Native global with no ambient type declaration.
  const errorUtils = (globalThis as unknown as { ErrorUtils?: ErrorUtilsShape }).ErrorUtils;
  if (!errorUtils?.setGlobalHandler) return;

  const previous = errorUtils.getGlobalHandler?.();

  errorUtils.setGlobalHandler((error: Error, isFatal?: boolean) => {
    // console.error reaches logcat as a ReactNativeJS line, which is what makes this
    // findable over adb on a device with no debugger attached.
    console.error(
      `[uncaught${isFatal ? ' fatal' : ''}]`,
      error?.name,
      error?.message,
      error?.stack,
    );

    if (isFatal && previous) previous(error, isFatal);
  });
}

type ErrorUtilsShape = {
  setGlobalHandler: (handler: (error: Error, isFatal?: boolean) => void) => void;
  getGlobalHandler?: () => (error: Error, isFatal?: boolean) => void;
};
