import { Stack } from "expo-router";
import { RouteGuard } from "../../src/session/RouteGuard";

/**
 * Only a signed-out user belongs here.
 *
 * Redirecting rather than hiding matters: without it, the back gesture from the first
 * screen after verification lands on the verification form again, holding a code that
 * has already been spent.
 */
export default function AuthLayout() {
  return (
    <RouteGuard allow={(s) => s === "signedOut"}>
      <Stack
        screenOptions={{ headerShown: false, animation: "slide_from_right" }}
      />
    </RouteGuard>
  );
}
