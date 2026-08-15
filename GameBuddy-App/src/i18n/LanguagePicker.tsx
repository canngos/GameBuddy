import { Check } from 'lucide-react-native';
import { Modal, Pressable, View } from 'react-native';
import { Icon, Text } from '../ui';
import { cn } from '../ui/cn';
import { Flag } from './Flag';
import { LANG_NAMES, LANGS, type Lang } from './languages';
import { useLangStore } from './store';

/**
 * The language chooser: seven flags, each with the language's own name under it.
 *
 * Flags rather than a list of language names is the whole point of the layout — a grid
 * of discs is scannable to somebody who cannot read any of the words on screen, which
 * is exactly who is opening this. The names stay underneath because a flag is a country
 * and a language is not: Swedish is spoken in Finland, German in Austria, Spanish
 * mostly outside Spain. The flag finds the row, the word confirms it.
 */
export function LanguagePicker({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const lang = useLangStore((s) => s.lang);
  const setLang = useLangStore((s) => s.setLang);

  const choose = (next: Lang) => {
    setLang(next);
    onClose();
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      {/* Tapping the scrim closes it. A picker with no visible way out is a trap on a
          screen somebody may have opened by accident in a language they cannot read. */}
      <Pressable
        onPress={onClose}
        accessibilityRole="button"
        accessibilityLabel="Close"
        className="flex-1 justify-end bg-black/50"
      >
        {/* Stops a tap inside the sheet from closing it. */}
        <Pressable onPress={() => {}} className="rounded-t-[28px] bg-elevated p-6 pb-10">
          <View className="mb-5 h-1 w-10 self-center rounded-full bg-line" />

          <View className="flex-row flex-wrap justify-center gap-4">
            {LANGS.map((code) => {
              const selected = code === lang;
              return (
                <Pressable
                  key={code}
                  onPress={() => choose(code)}
                  accessibilityRole="button"
                  accessibilityState={{ selected }}
                  accessibilityLabel={LANG_NAMES[code]}
                  className="w-20 items-center gap-2 active:opacity-70"
                >
                  <View
                    className={cn(
                      'rounded-full p-1',
                      // The ring is the selection, drawn outside the flag so it never
                      // covers the artwork it is marking.
                      selected ? 'bg-primary' : 'bg-transparent',
                    )}
                  >
                    <Flag lang={code} size={52} />
                  </View>

                  <View className="h-5 flex-row items-center gap-1">
                    {selected && <Icon as={Check} size={12} tone="primary" />}
                    <Text
                      variant="label"
                      numberOfLines={1}
                      className={selected ? 'text-primary' : 'text-content'}
                    >
                      {LANG_NAMES[code]}
                    </Text>
                  </View>
                </Pressable>
              );
            })}
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/**
 * The button that opens the picker: the flag of the language currently in force.
 *
 * A globe would say "language settings live here" without saying what is set; the flag
 * says both. On the welcome screen it is the only control above the fold, which is
 * deliberate — somebody who cannot read the pitch underneath needs to find this first.
 */
export function LanguageButton({ onPress, size = 32 }: { onPress: () => void; size?: number }) {
  const lang = useLangStore((s) => s.lang);

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`Language: ${LANG_NAMES[lang]}`}
      hitSlop={12}
      className="active:opacity-70"
    >
      <Flag lang={lang} size={size} />
    </Pressable>
  );
}
