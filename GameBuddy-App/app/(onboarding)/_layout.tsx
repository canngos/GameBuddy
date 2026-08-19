import { Stack } from "expo-router";
import { RouteGuard } from "../../src/session/RouteGuard";

/**
 * The two server-side onboarding steps.
 *
 * `needsDetails` covers three screens — profile, games, keywords — because they all
 * feed a single `POST /auth/details`. Only submitting that call advances the status,
 * so the user can move freely between the three without the guard interfering.
 */
export default function OnboardingLayout() {
  return (
    <RouteGuard allow={(s) => s === "needsUsername" || s === "needsDetails"}>
      <Stack
        screenOptions={{ headerShown: false, animation: "slide_from_right" }}
      />
    </RouteGuard>
  );
}
