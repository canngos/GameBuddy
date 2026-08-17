import { useRouter } from 'expo-router';
import type { ReactNode } from 'react';
import { View } from 'react-native';
import { Button } from './Button';
import { ErrorNotice } from './ErrorNotice';
import { Screen } from './Screen';
import { Text } from './Text';

type EditScreenProps = {
  title: string;
  subtitle?: string;
  children: ReactNode;
  /** Runs the save. Navigation back happens on success. */
  onSave: () => void;
  saving?: boolean;
  /** Disables save, e.g. while a minimum has not been met. */
  canSave?: boolean;
  error?: unknown;
  saveLabel?: string;
  /**
   * Off for a screen whose child is its own scrolling list.
   *
   * The picker screens are FlatLists now, and a FlatList inside a ScrollView is the
   * layout React Native warns about — it un-virtualises the list, which is the entire
   * point of it. With this off the title block moves into the child (it takes a `header`)
   * and the child fills the screen.
   */
  scroll?: boolean;
  /**
   * Sits in the pinned footer above the buttons, for a line that has to stay reachable —
   * a selection count, say. Below the children it would be at the end of a three-hundred
   * item list, which is where the games and keywords counts used to be.
   */
  footerNote?: ReactNode;
};

/**
 * Chrome shared by the "change one thing" screens.
 *
 * Save is at the bottom rather than in a header, because these screens are thumbed
 * one-handed and the top of a phone is the hardest place to reach.
 */
export function EditScreen({
  title,
  subtitle,
  children,
  onSave,
  saving = false,
  canSave = true,
  error,
  saveLabel = 'Save',
  scroll = true,
  footerNote,
}: EditScreenProps) {
  const router = useRouter();

  return (
    <Screen
      scroll={scroll}
      edges={['top', 'bottom']}
      // Pinned rather than scrolled past. The games and keywords screens use this and
      // their lists are a hundred rows long; Save at the end of the content meant
      // scrolling the whole catalogue to reach it and back up to keep choosing.
      footer={
        <View className="gap-2">
          {/* A save error belongs beside the button that failed. In the scrolling case it
              also still renders below the content, where it always was. */}
          {!scroll && !!error && <ErrorNotice error={error} />}
          {footerNote}
          <Button label={saveLabel} loading={saving} disabled={!canSave} onPress={onSave} />
          <Button
            label="Cancel"
            variant="ghost"
            disabled={saving}
            onPress={() => router.back()}
          />
        </View>
      }
    >
      {/* The title scrolls with the content when this screen owns the scrolling. When the
          child owns it, the child takes the title through its own `header` — pinning it
          here would cost a picker two rows of permanent viewport. */}
      {scroll && <EditTitle title={title} subtitle={subtitle} />}

      {children}

      {scroll && !!error && (
        <View className="pt-4">
          <ErrorNotice error={error} />
        </View>
      )}
    </Screen>
  );
}

/**
 * The title block, exported so a `scroll={false}` screen can hand it to whatever child
 * owns the scrolling — otherwise the title would simply not be drawn.
 */
export function EditTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <View className="gap-2 pb-6 pt-8">
      <Text variant="title">{title}</Text>
      {subtitle && (
        <Text variant="body" className="text-muted">
          {subtitle}
        </Text>
      )}
    </View>
  );
}
