import {
  ChartColumn,
  createLucideIcon,
  Flag,
  ImagePlus,
  MessageCircle,
  Settings,
  ShoppingBasket,
  Ticket,
  User,
  Users,
  type LucideIcon,
} from 'lucide-react-native';
import { View } from 'react-native';
import { useThemeColors } from '../theme';
import { glow } from './glow';
import { Icon } from './Icon';

export type TabIconName =
  | 'deck'
  | 'lobby'
  | 'messages'
  | 'market'
  | 'profile'
  // The moderator console. Same set, same treatment — it is a different app sharing a
  // binary, not a second design language.
  | 'analytics'
  | 'reports'
  | 'avatars'
  | 'promo'
  | 'settings';

/**
 * Tab bar icons.
 *
 * These were nine glyphs assembled from bordered `View`s — two rotated rectangles for the
 * deck, three bars for analytics, a pennant for reports, a head-and-shoulders built from a
 * circle and a half-capsule. That was a reasonable call when the app bundled no icon set:
 * the old header comment said to switch to `react-native-svg` if the set ever grew past a
 * handful or needed a curve that was not a circle. Both happened. Lucide draws to the same
 * 24×24 / 2px-stroke spec those shapes were eyeballed against, so this is the switch that
 * comment asked for rather than a change of mind.
 *
 * **This takes `focused`, not `color`, and that is the point of the rewrite.**
 *
 * The old signature accepted a `ColorValue` because react-navigation hands its tab tint
 * straight through and that tint can in principle be an opaque platform colour. Passing it
 * to Lucide would not typecheck — Lucide wants a `string` — and casting would be asserting
 * something we cannot know. But the widening was always defensive: the only values that
 * ever arrive are `tabBarActiveTintColor` and `tabBarInactiveTintColor`, both of which this
 * app sets itself, from its own token file, as plain hex.
 *
 * So the honest fix is not to narrow the type — it is to stop taking the colour at all.
 * The navigator says *which state* the tab is in and this file decides what that looks
 * like, which is also what makes the lit active state possible: a glow and a heavier stroke
 * are not things a single `color` prop can express.
 */
/**
 * Two fanned cards, drawn to Lucide's spec (24x24 viewBox, 2px stroke) through Lucide's
 * own factory, so it types and renders exactly like the stock set.
 *
 * The header comment above tells the history: the original hand-built deck glyph *was*
 * two rotated rectangles, the Lucide switch flattened it to `Layers`, and the rotated
 * pair came back by request - it reads as a swipe deck where Layers read as generic
 * stacked sheets. Each card rotates about its own centre so the fan stays symmetric.
 */
const DeckCards = createLucideIcon('DeckCards', [
  ['rect', { x: '4', y: '3', width: '12', height: '17', rx: '2', transform: 'rotate(-6 10 11.5)', key: 'back' }],
  ['rect', { x: '9', y: '4', width: '12', height: '17', rx: '2', transform: 'rotate(6 15 12.5)', key: 'front' }],
]);

const GLYPHS: Record<TabIconName, LucideIcon> = {
  deck: DeckCards,
  // A group of people, not a controller. A lobby is who you are playing with — the deck
  // already carries the "games" meaning, and two game-shaped glyphs in one bar read as
  // the same destination twice.
  lobby: Users,
  messages: MessageCircle,
  market: ShoppingBasket,
  profile: User,
  analytics: ChartColumn,
  reports: Flag,
  avatars: ImagePlus,
  // A ticket, not a gift box: these are coupons somebody types in, and the shop already
  // owns the "something to unwrap" reading with its basket.
  promo: Ticket,
  settings: Settings,
};

type TabIconProps = {
  name: TabIconName;
  /** Straight from react-navigation, which types it `boolean`. No widening needed. */
  focused: boolean;
  size?: number;
};

export function TabIcon({ name, focused, size = 24 }: TabIconProps) {
  const colors = useThemeColors();

  return (
    <View
      className="items-center justify-center"
      style={[
        // The radius is not decoration — nothing here is visibly round. Android draws
        // `boxShadow` around the node's *box*, so without it the active tab wears a square
        // violet halo, which is what it did on first run. A circular box makes the bloom
        // read as light coming off the glyph.
        { width: size, height: size, borderRadius: size / 2 },
        // Always emitted, `'none'` when inactive, because a style whose keys come and go
        // across a theme change stops the subtree painting — see `src/ui/hairline.ts`.
        // A tab bar is the worst possible place to learn that: it is on screen always.
        glow(focused ? 'soft' : 'none', colors.primary),
      ]}
    >
      <Icon
        as={GLYPHS[name]}
        size={size}
        tone={focused ? 'primary' : 'muted'}
        // The icon equivalent of a bold weight. Together with the glow it is what
        // separates the active tab without needing a second asset for it.
        strokeWidth={focused ? 2.5 : 2}
      />
    </View>
  );
}
