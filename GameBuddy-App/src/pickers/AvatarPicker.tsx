import { useQuery } from '@tanstack/react-query';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { catalogueApi } from '../api/catalogue';
import { useThemeColors } from '../theme';
import { Avatar } from '../ui/Avatar';
import { ErrorNotice } from '../ui/ErrorNotice';

type AvatarPickerProps = {
  selected: string | null;
  onSelect: (avatarId: string) => void;
  size?: number;
  /**
   * How many to a row. Unset keeps the original wrap, which is right where the picker is
   * one control among several; the onboarding step sets 3 so the tiles are big enough to
   * actually look at.
   */
  columns?: number;
};

/**
 * The avatars this gamer may actually choose.
 *
 * `/application/get/avatars` returns the free ones plus anything bought, so everything
 * shown here is selectable — a special avatar that has not been purchased is simply
 * absent rather than shown and then refused. Special avatars are bought in Market.
 */
export function AvatarPicker({ selected, onSelect, size = 64, columns }: AvatarPickerProps) {
  const colors = useThemeColors();
  const avatars = useQuery({ queryKey: ['avatars'], queryFn: catalogueApi.avatars });

  return (
    <View className="gap-3">
      {avatars.isPending && <ActivityIndicator color={colors.primary} />}
      {avatars.error && (
        <ErrorNotice error={avatars.error} onRetry={() => avatars.refetch()} />
      )}

      <View className="flex-row flex-wrap justify-between gap-y-4">
        {avatars.data?.map((avatar, index) => (
          <Pressable
            key={avatar.id}
            onPress={() => onSelect(avatar.id)}
            accessibilityRole="radio"
            accessibilityLabel={`Avatar ${index + 1}`}
            accessibilityState={{ selected: selected === avatar.id }}
            // A share of the row rather than a fixed width, so three fit a narrow phone
            // and a wide one without measuring the screen.
            style={columns ? { width: `${100 / columns - 3}%`, alignItems: 'center' } : undefined}
            className="active:opacity-70"
          >
            <Avatar
              source={avatar.image}
              name={`${index + 1}`}
              // Colour by id, not by the label: consecutive labels hash to
              // neighbouring hues and every option comes out the same shade.
              colorSeed={avatar.id}
              size={size}
              selected={selected === avatar.id}
            />
          </Pressable>
        ))}
      </View>
    </View>
  );
}
