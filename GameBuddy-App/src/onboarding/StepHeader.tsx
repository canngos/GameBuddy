import { View } from "react-native";
import { useIntroPadding } from "../ui";
import { useUpper } from "../i18n/case";
import { useT } from "../i18n/useT";
import { cn } from "../ui/cn";
import { Text } from "../ui/Text";

type StepHeaderProps = {
  step: number;
  total: number;
  title: string;
  subtitle?: string;
};

/**
 * Shared heading for the onboarding screens.
 *
 * Segmented progress rather than one continuous bar: four steps read as four things
 * to do, where a part-filled bar reads as an unknown amount of work remaining.
 */
export function StepHeader({ step, total, title, subtitle }: StepHeaderProps) {
  const intro = useIntroPadding();
  const t = useT();
  const upper = useUpper();
  return (
    <View className={`gap-2 pb-6 ${intro.heading}`}>
      <View
        className="mb-2 flex-row gap-1.5"
        accessibilityRole="progressbar"
        accessibilityValue={{ min: 0, max: total, now: step }}
      >
        {Array.from({ length: total }, (_, i) => (
          <View
            key={i}
            className={cn(
              "h-1.5 flex-1 rounded-full",
              i < step ? "bg-primary" : "bg-line",
            )}
          />
        ))}
      </View>

      <Text variant="overline">{upper(t.onboarding.stepOf(step, total))}</Text>
      <Text variant="title">{title}</Text>
      {subtitle && (
        <Text variant="body" className="text-muted">
          {subtitle}
        </Text>
      )}
    </View>
  );
}
