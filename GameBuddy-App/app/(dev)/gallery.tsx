import { useState } from 'react';
import { Platform, Pressable, ScrollView, Vibration, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Award, Heart, MessageCircle, Search, Settings2, Swords, Zap } from 'lucide-react-native';
import { THEME_OPTIONS, brand, useGradient, useScheme, useThemeColors } from '../../src/theme';
import { CandidateCard } from '../../src/match/CandidateCard';
import { CandidateSheet } from '../../src/match/CandidateSheet';
import { Burst } from '../../src/ui/Burst';
import { GradientSurface, GradientView } from '../../src/ui/Gradient';
import { glow } from '../../src/ui/glow';
import { lift } from '../../src/ui/elevation';
import { useHairline } from '../../src/ui/hairline';
import { probes } from '../../src/ui/haptics';
import {
  Avatar,
  Button,
  Card,
  Checkbox,
  CountUp,
  ErrorNotice,
  Icon,
  LinkRow,
  RowGroup,
  SelectRow,
  Text,
  TextField,
  ToastHost,
  feedback,
  showToast,
} from '../../src/ui';

/**
 * Every shared component, in every variant, on one screen.
 *
 * This exists because the alternative is hunting nineteen components across fifty screens
 * and a login wall every time a token moves. It is also where the three questions that
 * this redesign turns on get answered on a real device — see the spike sections, which
 * are first on purpose.
 *
 * Not linked from anywhere. See `app/(dev)/_layout.tsx` for how to reach it.
 */
export default function Gallery() {
  const [profileOpen, setProfileOpen] = useState(false);

  return (
    <SafeAreaView className="flex-1 bg-canvas">
      <SchemeBar />
      <ScrollView contentContainerClassName="gap-8 px-5 pb-16 pt-4">
        <SpikeShadows />
        <SpikeGradients />
        <TokenStrip />
        <DeckCard onOpenProfile={() => setProfileOpen(true)} />
        <BurstSection />
        <Toasts />
        <Counting />
        <Cues />
        <HapticProbes />
        <Buttons />
        <Fields />
        <Rows />
        <Surfaces />
        <TypeScale />
        <Icons />
      </ScrollView>

      {/* Mounted here as well as in `app/(main)/_layout.tsx`.

          This screen is deliberately outside the route guard — that is what lets it open
          with no session and no backend — which also puts it outside the layout that hosts
          the real one. Without this the TOAST section would push into a store nothing is
          rendering and look broken. The two never coexist: they are in different route
          groups, so only one is ever mounted. */}
      <ToastHost />

      {/* Root-level, like the deck mounts it — see the note in `DeckCard`. */}
      <CandidateSheet
        candidate={profileOpen ? (GALLERY_CANDIDATE as never) : null}
        onDismiss={() => setProfileOpen(false)}
      />
    </SafeAreaView>
  );
}

/**
 * The theme toggle, pinned above the scroll rather than buried in it.
 *
 * Every post-mortem in `src/ui/hairline.ts` and `src/ui/elevation.ts` is about something
 * that only breaks when the theme flips at runtime. If checking that costs a scroll to the
 * top, it stops happening.
 */
function SchemeBar() {
  const preference = useScheme((s) => s.preference);
  const setPreference = useScheme((s) => s.setPreference);
  const hairline = useHairline();

  return (
    <View
      className="flex-row items-center gap-2 border-b border-line bg-surface px-5 py-3"
      style={hairline}
    >
      <Text variant="overline" className="flex-1">
        GALLERY
      </Text>
      {THEME_OPTIONS.map((option) => {
        const active = preference === option.value;
        return (
          <Pressable
            key={option.value}
            onPress={() => setPreference(option.value)}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            className={[
              'rounded-full border px-3 py-1.5',
              active ? 'border-brand bg-brand/10' : 'border-line bg-raised',
            ].join(' ')}
          >
            <Text
              variant="caption"
              className={active ? 'text-brand' : 'text-muted'}
            >
              {option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/**
 * Spike 1 — does Android draw a *coloured* glow?
 *
 * The whole neon language rests on this. `elevation.ts` concluded Android could not, but
 * that was about `elevation` and about a NativeWind class; RN 0.76+ ships `boxShadow` as a
 * real Android style prop. Compare the swatches on a physical Android 8 device — the same
 * device class that produced the original crash — not on an emulator.
 *
 * Expected: rows 3 and 4 show violet and pink light. If they render grey, or square, or
 * not at all, flip `ANDROID_STRATEGY` in `src/ui/glow.ts` and use `Halo` instead.
 */
function SpikeShadows() {
  const gradient = useGradient('primary');

  return (
    <Section title="SPIKE 1 · DEPTH" hint={`Platform: ${Platform.OS} ${Platform.Version}`}>
      <View className="flex-row flex-wrap gap-4">
        <Swatch label="lift sm" style={lift('sm')} />
        <Swatch label="lift lg" style={lift('lg')} />
        <Swatch label="lift lg brand" style={lift('lg', brand.DEFAULT)} />
        <Swatch label="glow soft" style={glow('soft', gradient[0])} />
        <Swatch label="glow strong" style={glow('strong', gradient[0])} />
        <Swatch label="glow accent" style={glow('strong', brand.DEFAULT)} />
        <Swatch label="glow none" style={glow('none', gradient[0])} />
      </View>
      <Text variant="caption">
        Rows 4–6 must show coloured light, not grey and not a hard edge. `glow none` must
        look identical to no style at all — and must not blank when the theme flips.
      </Text>
    </Section>
  );
}

/**
 * Spike 2 — does `cssInterop(LinearGradient)` hold, and do themed stops re-resolve?
 *
 * The second block is the real question: `useGradient` reads the colour scheme, so
 * flipping the theme above must repaint it. If it keeps the old stops, gradients cannot
 * be themed through a hook and have to be passed down as values.
 */
function SpikeGradients() {
  return (
    <Section title="SPIKE 2 · GRADIENT">
      {/* The two violet gradients, side by side, because the difference between them is
          the single easiest thing to get wrong. `primary` runs to cyan and is for
          surfaces; `action` is the one that may carry a white label. See gradients.ts. */}
      <GradientView name="primary" className="h-20 rounded-card" />
      <Text variant="caption">
        primary — surfaces only. White text on the cyan end measures 1.54:1.
      </Text>

      <GradientSurface name="action" className="rounded-full">
        <View className="min-h-touch items-center justify-center px-6">
          <Text className="font-semibold text-[16px] leading-[22px] text-white">
            action — safe under a label
          </Text>
        </View>
      </GradientSurface>

      <View className="flex-row gap-3">
        <GradientView name="accent" className="h-16 flex-1 rounded-card" />
        <GradientView name="gold" className="h-16 flex-1 rounded-card" />
      </View>
      <Text variant="caption">
        `className` on LinearGradient must apply height and radius. Flip the theme: all
        of these must restyle — the light stops are darker, not the same colours.
      </Text>

      {/* The clip/depth split from Gradient.tsx, which is Android-only when it goes
          wrong: overflow-hidden on the same node as the glow eats the glow. */}
      <GradientSurface
        name="primary"
        className="rounded-card"
        style={glow('strong', '#7C4DFF')}
      >
        <View className="items-center px-6 py-5">
          <Text className="font-semibold text-[16px] leading-[22px] text-white">
            Clip inside, glow outside
          </Text>
        </View>
      </GradientSurface>
      <Text variant="caption">
        The glow must be visible *outside* the rounded gradient. If it is clipped, the
        two-node split has regressed.
      </Text>
    </Section>
  );
}

/**
 * Every semantic token as a named swatch.
 *
 * A mistyped or missing CSS variable shows up here as a transparent square, which is a
 * two-second diagnosis. Found on a screen instead, it is an afternoon.
 */
function TokenStrip() {
  const colors = useThemeColors();

  return (
    <Section title="TOKENS">
      <View className="flex-row flex-wrap gap-2">
        {Object.entries(colors).map(([name, value]) => (
          <View key={name} className="w-[88px] gap-1">
            <View
              className="h-12 rounded-field border border-line"
              style={{ backgroundColor: value }}
            />
            <Text variant="caption" numberOfLines={1}>
              {name}
            </Text>
          </View>
        ))}
      </View>

      {/* Class-driven rather than value-driven, so this catches drift between
          global.css and colors.ts — the two are maintained by hand today. */}
      <Text variant="overline">VIA CLASSES</Text>
      <View className="flex-row flex-wrap gap-2">
        {['bg-canvas', 'bg-surface', 'bg-raised', 'bg-field', 'bg-brand', 'bg-danger', 'bg-success'].map(
          (cls) => (
            <View key={cls} className="w-[88px] gap-1">
              <View className={`h-12 rounded-field border border-line ${cls}`} />
              <Text variant="caption" numberOfLines={1}>
                {cls}
              </Text>
            </View>
          ),
        )}
      </View>
      <Text variant="caption">
        Each class swatch must match the value swatch of the same name. A mismatch is
        global.css and colors.ts having drifted.
      </Text>
    </Section>
  );
}

/**
 * The deck card, on a fabricated candidate.
 *
 * Here rather than checked on the real deck, because the deck runs out: an account that has
 * swiped everyone shows an empty state, and the card — the app's front door — then cannot be
 * looked at at all without reseeding somebody's database.
 *
 * The two games are deliberately one *with* cover art and one without, because that is the
 * pair that actually has to look right. Local seed data has `game_icon` NULL for all hundred
 * games until `backfill_game_covers.py` has run, so the fallback is not an edge case here —
 * it is what every developer sees.
 */
/**
 * Deliberately more games and keywords than the card will show.
 *
 * The card caps both lists and hides the rest behind a tap — see `CandidateCard`. A fixture
 * that fitted comfortably would exercise none of that, and the overflow case is the one
 * that shipped broken: the old card scrolled, hid its scrollbar, and sliced the last row
 * through the middle at the card's edge. Eight games and seven keywords keeps that state
 * permanently on screen. The long title is the one that used to overflow the row.
 */
const GALLERY_CANDIDATE = {
  userId: 'gallery-candidate-1',
  gamerUsername: 'NightOwl_TR',
  age: 24,
  country: 'Turkey',
  gender: null,
  avatar: null,
  frame: null,
  favoriteGames: [
    // A 1x1 transparent PNG as a data URI: proves the image path renders and lays out
    // without reaching the network, which the gallery must never need.
    {
      gameName: 'Valorant',
      gameIcon:
        'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    },
    { gameName: 'Elden Ring', gameIcon: null },
    { gameName: 'Apex Legends', gameIcon: null },
    { gameName: 'Atelier Ryza 3: Alchemist of the End & the Secret Key', gameIcon: null },
    { gameName: 'Counter-Strike 2', gameIcon: null },
    { gameName: 'Baldur’s Gate 3', gameIcon: null },
    { gameName: 'Helldivers 2', gameIcon: null },
    { gameName: 'Stardew Valley', gameIcon: null },
  ],
  selectedKeywords: [
    'chill',
    'co-op grinder',
    'night owl',
    'story-driven',
    'competitive',
    'lore nerd',
    'achievement hunter',
  ],
  platforms: ['PC', 'PlayStation'],
} as const;

function DeckCard({ onOpenProfile }: { onOpenProfile: () => void }) {
  return (
    <Section title="DECK CARD" hint="8 games, 7 styles — capped">
      <View className="h-[520px]">
        <CandidateCard candidate={GALLERY_CANDIDATE as never} />
      </View>

      {/* The card's own tap belongs to the deck's gesture detector, which is not here, so
          the sheet gets its own opener. It raises the same component the deck does — and
          from the gallery root, not from inside this section, because the sheet fills its
          parent and a sheet the size of one section is not the thing being tested. */}
      <Pressable
        onPress={onOpenProfile}
        accessibilityRole="button"
        className="mt-3 items-center rounded-full bg-raised py-3 active:opacity-70"
      >
        <Text variant="label" className="text-primary">
          Open full profile sheet
        </Text>
      </Pressable>
    </Section>
  );
}

/**
 * The burst, on its own, with a replay.
 *
 * The celebration itself needs a mutual match — a running backend, two accounts and a
 * swipe — so the animation is almost impossible to look at while working on it. Here it is
 * one tap, with no session and no server, which is the whole reason this gallery exists.
 *
 * This section is also the reason the Lottie version was finally abandoned rather than
 * debugged forever: with a replay button and no backend in the way, "nothing draws" was
 * reproducible in one tap and still could not be explained. Both colours are shown because
 * the burst has two callers with two meanings — accent for a match, gold for coins.
 */
function BurstSection() {
  const colors = useThemeColors();
  const [runs, setRuns] = useState(0);
  const [tint, setTint] = useState<'accent' | 'gold'>('accent');

  const replay = (next: 'accent' | 'gold') => {
    setTint(next);
    setRuns((n) => n + 1);
  };

  return (
    <Section title="BURST" hint="1.1s, plays once">
      {/* On the same near-black the real overlay uses, because that is what the particle
          colours were picked against. */}
      <View className="h-[320px] overflow-hidden rounded-card bg-ink-900">
        <Burst play={runs} color={tint === 'gold' ? colors.gold : brand.DEFAULT} radius={130} />
      </View>
      <View className="mt-3 flex-row gap-2">
        <Pressable
          onPress={() => replay('accent')}
          accessibilityRole="button"
          className="flex-1 items-center rounded-full bg-raised py-3 active:opacity-70"
        >
          <Text variant="label" className="text-primary">
            Replay accent
          </Text>
        </Pressable>
        <Pressable
          onPress={() => replay('gold')}
          accessibilityRole="button"
          className="flex-1 items-center rounded-full bg-raised py-3 active:opacity-70"
        >
          <Text variant="label" className="text-gold">
            Replay gold
          </Text>
        </Pressable>
      </View>
    </Section>
  );
}

function Buttons() {
  return (
    <Section title="BUTTON">
      {(['primary', 'secondary', 'ghost', 'danger'] as const).map((variant) => (
        <View key={variant} className="gap-2">
          <Text variant="overline">{variant.toUpperCase()}</Text>
          <Button label="Idle" variant={variant} />
          <Button label="Loading" variant={variant} loading />
          <Button label="Disabled" variant={variant} disabled />
          <Button label="Medium" variant={variant} size="md" />
        </View>
      ))}
    </Section>
  );
}

function Fields() {
  const [value, setValue] = useState('');

  return (
    <Section title="TEXTFIELD">
      <TextField label="Idle" placeholder="Placeholder" value={value} onChangeText={setValue} />
      <TextField label="With hint" hint="Three to sixteen characters." placeholder="Username" />
      <TextField label="Error" error="That username is taken." defaultValue="Kaan_TR" />
      <TextField label="Secure" secure placeholder="Password" />
    </Section>
  );
}

function Rows() {
  const [checked, setChecked] = useState(true);
  const [selected, setSelected] = useState('dark');

  return (
    <Section title="ROWS">
      {/* All four `position` values in one group — the corner rounding is the part that
          breaks, and it only shows when they are adjacent. */}
      <RowGroup>
        <SelectRow
          label="System"
          hint="Match your phone"
          position="first"
          selected={selected === 'system'}
          onPress={() => setSelected('system')}
        />
        <SelectRow
          label="Light"
          hint="Always light"
          position="middle"
          selected={selected === 'light'}
          onPress={() => setSelected('light')}
        />
        <SelectRow
          label="Dark"
          hint="Always dark"
          position="last"
          selected={selected === 'dark'}
          onPress={() => setSelected('dark')}
        />
      </RowGroup>

      <RowGroup>
        <LinkRow label="Avatar" hint="Catalogue" position="first" onPress={() => {}} />
        <LinkRow label="Games" hint="3 selected" position="middle" onPress={() => {}} />
        <LinkRow label="Play style" hint="5 selected" position="last" onPress={() => {}} />
      </RowGroup>

      <RowGroup>
        <SelectRow label="Single" hint="Only row" position="single" selected onPress={() => {}} />
      </RowGroup>

      <Checkbox
        checked={checked}
        onChange={setChecked}
        accessibilityLabel="I am over 18 and accept the Terms"
      >
        <Text variant="body">I am over 18 and accept the Terms.</Text>
      </Checkbox>
    </Section>
  );
}

function Surfaces() {
  return (
    <Section title="SURFACES">
      <Card className="gap-2">
        <Text variant="heading">Card</Text>
        <Text variant="body" className="text-muted">
          Composes lift() and useHairline(). Both must survive a theme flip.
        </Text>
      </Card>

      <View className="flex-row items-center gap-3">
        {/* source={null} is the interesting case, not a shortcut: no avatar art is
            hosted yet, so the initials fallback is what almost every user actually sees. */}
        <Avatar source={null} name="Kaan_TR" colorSeed="a1" size={56} />
        <Avatar source={null} name="Mira" colorSeed="b2" size={56} />
        <Avatar source={null} name="Volkan" colorSeed="c3" size={56} selected />
        <Avatar source={null} name="Deniz" colorSeed="d4" size={40} />
      </View>

      <ErrorNotice error={new Error('Something went wrong.')} onRetry={() => {}} />
    </Section>
  );
}

function TypeScale() {
  const variants = [
    'hero',
    'display',
    'title',
    'heading',
    'body',
    'bodyStrong',
    'label',
    'caption',
    'button',
    'overline',
  ] as const;

  return (
    <Section title="TYPE">
      {variants.map((variant) => (
        <View key={variant} className="gap-0.5">
          <Text variant="caption">{variant}</Text>
          <Text variant={variant}>Find your GameBuddy</Text>
        </View>
      ))}

      {/* Numerals carry no size of their own — the point is the face and the figures.
          Both rows must stay the same width as the digits change. */}
      <View className="gap-0.5">
        <Text variant="caption">numeral</Text>
        <View className="flex-row items-baseline gap-4">
          <Text variant="numeral" className="text-[28px] leading-[34px]">
            1,284
          </Text>
          <Text variant="numeral" className="text-[20px] leading-[26px] text-brand">
            0123456789
          </Text>
        </View>
      </View>
    </Section>
  );
}

/** Smoke test that lucide renders through the already-installed react-native-svg. */
function Icons() {
  return (
    <Section title="ICONS">
      <View className="flex-row items-center gap-5">
        <Icon as={Heart} tone="accent" />
        <Icon as={Search} tone="content" />
        <Icon as={Settings2} tone="muted" />
        <Icon as={Swords} tone="content" />
        <Icon as={Zap} tone="primary" />
      </View>

      {/* Stroke weight as an emphasis axis. An icon font cannot do this row at all. */}
      <View className="flex-row items-center gap-5">
        <Icon as={Heart} tone="accent" strokeWidth={1.5} />
        <Icon as={Heart} tone="accent" strokeWidth={2} />
        <Icon as={Heart} tone="accent" strokeWidth={2.5} />
        <Icon as={Heart} tone="accent" fill="currentColor" />
        <Icon as={Zap} tone="gold" fill="currentColor" />
      </View>

      <Text variant="caption">
        These replace ⚙︎ 🔍 🎮 ⚡ ↺ and the View-drawn tab icons and chevrons. The second
        row is why an icon font was rejected: stroke weight is what makes a glyph read as
        lit, and a font cannot vary it.
      </Text>
    </Section>
  );
}

/**
 * The toast, raised by hand.
 *
 * The real trigger is a push arriving while the app is open, which needs a backend, a device
 * token and somebody else to act — so this is the only practical way to look at the thing
 * while building it. Pushing two at once is the case worth checking: the queue shows the
 * head and holds the rest, and getting that wrong means the second event is silently
 * replaced by the first rather than shown after it.
 *
 * Note the toast draws *over* this screen from the very top, above the theme bar.
 */
function Toasts() {
  const push = (count: 1 | 2) => {
    showToast({
      id: `demo:message:${Date.now()}`,
      title: 'Kerem',
      body: 'are you on tonight?',
      icon: MessageCircle,
      tone: 'primary',
    });

    if (count === 2) {
      showToast({
        id: `demo:badge:${Date.now()}`,
        title: 'Badge earned',
        body: 'First Blood — 50 coins added.',
        icon: Award,
        tone: 'gold',
      });
    }
  };

  return (
    <Section title="TOAST" hint="4.2s, swipe up to dismiss">
      <View className="flex-row gap-2">
        <Pressable
          onPress={() => push(1)}
          accessibilityRole="button"
          className="flex-1 items-center rounded-full bg-raised py-3 active:opacity-70"
        >
          <Text variant="label" className="text-primary">
            Push one
          </Text>
        </Pressable>
        <Pressable
          onPress={() => push(2)}
          accessibilityRole="button"
          className="flex-1 items-center rounded-full bg-raised py-3 active:opacity-70"
        >
          <Text variant="label" className="text-primary">
            Push two
          </Text>
        </Pressable>
      </View>
      <Text variant="caption">
        Two at once exercises the queue: the second waits for the first rather than replacing
        it. Tapping one runs its action and dismisses.
      </Text>
    </Section>
  );
}

/**
 * The counting balance, without a purchase behind it.
 *
 * Both directions are here because they are different signals and only one of them is a
 * celebration — see `src/market/CoinBalance.tsx` for why coins arriving burst and coins
 * leaving do not.
 */
function Counting() {
  const [value, setValue] = useState(1440);

  return (
    <Section title="COUNT UP" hint="600ms, ease-out">
      <View className="flex-row items-center justify-between rounded-card bg-surface p-5">
        <Text variant="caption">Balance</Text>
        <CountUp value={value} className="text-[22px] leading-[28px] text-gold" />
      </View>
      <View className="flex-row gap-2">
        <Pressable
          onPress={() => setValue((n) => n + 250)}
          accessibilityRole="button"
          className="flex-1 items-center rounded-full bg-raised py-3 active:opacity-70"
        >
          <Text variant="label" className="text-gold">
            Earn 250
          </Text>
        </Pressable>
        <Pressable
          onPress={() => setValue((n) => Math.max(0, n - 200))}
          accessibilityRole="button"
          className="flex-1 items-center rounded-full bg-raised py-3 active:opacity-70"
        >
          <Text variant="label" className="text-muted">
            Spend 200
          </Text>
        </Pressable>
      </View>
    </Section>
  );
}

/**
 * The four cues, on demand.
 *
 * An emulator cannot produce haptics, so these fire the pairing layer rather than the sound
 * module — on a physical device this section is the only place the buzz and the cue can be
 * checked against each other, which is the entire reason `src/ui/feedback.ts` exists.
 * Respects the Settings switch, so silence here with sounds turned off is correct.
 */
function Cues() {
  const cues = [
    { label: 'Match', run: feedback.celebrate },
    { label: 'Purchase', run: feedback.purchase },
    { label: 'Reward', run: feedback.reward },
    { label: 'Receive', run: feedback.receive },
  ];

  return (
    <Section title="SOUND AND HAPTICS" hint="paired">
      <View className="flex-row flex-wrap gap-2">
        {cues.map((cue) => (
          <Pressable
            key={cue.label}
            onPress={cue.run}
            accessibilityRole="button"
            className="items-center rounded-full bg-raised px-5 py-3 active:opacity-70"
          >
            <Text variant="label" className="text-primary">
              {cue.label}
            </Text>
          </Pressable>
        ))}
      </View>
      <Text variant="caption">
        Cues are generated by `scripts/make-sounds.py` and are placeholders. Silent when the
        phone is on silent, and never interrupt music.
      </Text>
    </Section>
  );
}

/**
 * Every haptic primitive, one button each, with its outcome on screen.
 *
 * This exists because the app's haptics did nothing on a real device for the whole life of
 * the feature and nothing ever said so. `performAndroidHapticsAsync` has three ways of
 * failing silently — a null activity, a system setting the app cannot read, and constants
 * that only exist on API 30+ — and two of them **resolve successfully**. So "did it work?"
 * cannot be answered by looking at code or at a log; it has to be answered by a thumb on a
 * device, with the resolved/rejected result next to it.
 *
 * `Vibration.vibrate` is the control. If that is not felt either, the motor or the system
 * vibration switch is off and nothing in the app can help; if it *is* felt and the
 * expo-haptics calls are not, the fault is the route, which is what this diagnoses.
 *
 * Bypasses the preference switch and does not swallow rejections — the opposite of what
 * `fire()` does in the app, deliberately.
 */
function HapticProbes() {
  const [results, setResults] = useState<Record<string, string>>({});

  const run = (name: string, fn: () => Promise<unknown>) => {
    fn().then(
      () => setResults((r) => ({ ...r, [name]: 'resolved' })),
      (error: unknown) =>
        setResults((r) => ({
          ...r,
          [name]: error instanceof Error ? error.name : String(error),
        })),
    );
  };

  return (
    <Section title="HAPTIC PROBES" hint={`API ${String(Platform.Version)}`}>
      <Text variant="caption">
        Tap each and note whether you feel it. “resolved” with nothing felt is the signature
        of a route that reports success and does nothing.
      </Text>

      <View className="gap-2">
        <Pressable
          onPress={() => run('control:Vibration', async () => Vibration.vibrate(60))}
          accessibilityRole="button"
          className="flex-row items-center justify-between rounded-card bg-raised px-4 py-3 active:opacity-70"
        >
          <Text variant="label" className="text-content">
            control · Vibration.vibrate
          </Text>
          <Text variant="caption">{results['control:Vibration'] ?? '—'}</Text>
        </Pressable>

        {probes.map((probe) => (
          <Pressable
            key={probe.name}
            onPress={() => run(probe.name, probe.run)}
            accessibilityRole="button"
            className="flex-row items-center justify-between rounded-card bg-raised px-4 py-3 active:opacity-70"
          >
            <Text variant="label" className="text-content">
              {probe.name}
            </Text>
            <Text variant="caption">{results[probe.name] ?? '—'}</Text>
          </Pressable>
        ))}
      </View>

      <Text variant="caption">
        `impact:*` and `notify:*` go through the vibrator, which is what the app uses now.
        `android:*` is the old route, kept here only so it can be seen resolving while
        nothing happens.
      </Text>
    </Section>
  );
}

function Section({
  title,
  hint,
  children,
}: {
  title: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <View className="gap-3">
      <View className="flex-row items-center justify-between">
        <Text variant="overline">{title}</Text>
        {!!hint && <Text variant="caption">{hint}</Text>}
      </View>
      {children}
    </View>
  );
}

function Swatch({ label, style }: { label: string; style: object }) {
  return (
    <View className="w-[92px] items-center gap-2">
      {/* Generous margin: a glow that is clipped by a tight parent looks like a glow
          that does not work. */}
      <View className="p-3">
        <View className="h-14 w-14 rounded-card bg-surface" style={style} />
      </View>
      <Text variant="caption" numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}
