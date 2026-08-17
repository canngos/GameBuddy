import { useQuery } from '@tanstack/react-query';
import { memo, useCallback, useMemo } from 'react';
import { ActivityIndicator, Pressable, View, type ViewStyle } from 'react-native';
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
 *
 * **Not virtualized, and that is a decision rather than an omission.** Both callers embed
 * this inside a scrolling screen, alongside an upload button and a heading — so a FlatList
 * here would be a list nested in a ScrollView, which React Native warns about because it
 * un-virtualizes itself and gains nothing. The catalogue is also small. What did cost
 * something was rebuilding every tile on every render of the screen around it, and that is
 * what the memo below removes.
 */
export function AvatarPicker({ selected, onSelect, size = 64, columns }: AvatarPickerProps) {
  const colors = useThemeColors();
  const avatars = useQuery({ queryKey: ['avatars'], queryFn: catalogueApi.avatars });

  // A share of the row rather than a fixed width, so three fit a narrow phone and a wide
  // one without measuring the screen. Memoised because it is a prop on every tile.
  const tileStyle = useMemo<ViewStyle | undefined>(
    () => (columns ? { width: `${100 / columns - 3}%`, alignItems: 'center' } : undefined),
    [columns],
  );

  return (
    <View className="gap-3">
      {avatars.isPending && <ActivityIndicator color={colors.primary} />}
      {avatars.error && (
        <ErrorNotice error={avatars.error} onRetry={() => avatars.refetch()} />
      )}

      <View className="flex-row flex-wrap justify-between gap-y-4">
        {avatars.data?.map((avatar, index) => (
          <AvatarTile
            key={avatar.id}
            id={avatar.id}
            image={avatar.image}
            index={index}
            size={size}
            selected={selected === avatar.id}
            style={tileStyle}
            onSelect={onSelect}
          />
        ))}
      </View>
    </View>
  );
}

const AvatarTile = memo(function AvatarTile({
  id,
  image,
  index,
  size,
  selected,
  style,
  onSelect,
}: {
  id: string;
  image: string | null;
  index: number;
  size: number;
  selected: boolean;
  style: ViewStyle | undefined;
  onSelect: (avatarId: string) => void;
}) {
  const press = useCallback(() => onSelect(id), [onSelect, id]);

  return (
    <Pressable
      onPress={press}
      accessibilityRole="radio"
      accessibilityLabel={`Avatar ${index + 1}`}
      accessibilityState={{ selected }}
      style={style}
      className="active:opacity-70"
    >
      <Avatar
        source={image}
        name={`${index + 1}`}
        // Colour by id, not by the label: consecutive labels hash to
        // neighbouring hues and every option comes out the same shade.
        colorSeed={id}
        size={size}
        selected={selected}
      />
    </Pressable>
  );
});
