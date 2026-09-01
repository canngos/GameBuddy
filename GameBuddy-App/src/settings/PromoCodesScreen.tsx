import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Coins, Crown } from 'lucide-react-native';
import { useState } from 'react';
import { View } from 'react-native';
import { promoApi } from '../api/billing';
import type { PromoRedemption, WaitingPromoCode } from '../api/types';
import { ENTITLEMENT_QUERIES } from '../billing/usePurchase';
import { useT, tNow } from '../i18n/useT';
import { BackHeader, Button, Card, ErrorNotice, Icon, Screen, Text, TextField } from '../ui';
import * as feedback from '../ui/feedback';
import { showToast } from '../ui/toast';

/**
 * Codes somebody was given, and the box for one they were given elsewhere.
 *
 * Two halves because there are two ways a code arrives. One is addressed to this account
 * and is already sitting on the server, so the screen can list it and reduce redeeming to
 * a single tap — copying a code out of an email and typing it back in is work the app can
 * simply do for them. The other came from a newsletter or a friend, and for that there is
 * nothing to list, only a field.
 *
 * There is deliberately no way to browse codes that exist. A coupon somebody could look up
 * is not a coupon.
 */
export function PromoCodesScreen() {
  const t = useT();
  const queryClient = useQueryClient();
  const [typed, setTyped] = useState('');
  const [done, setDone] = useState<PromoRedemption | null>(null);

  const mine = useQuery({
    queryKey: ['promo'],
    queryFn: () => promoApi.mine(),
    staleTime: 30_000,
  });

  const redeem = useMutation({
    mutationFn: (code: string) => promoApi.redeem(code),
    onSuccess: (result) => {
      /*
       * The same set a purchase invalidates, imported rather than repeated: a code grants
       * exactly what money grants, and the failure mode of missing one is invisible in
       * testing and obvious to whoever redeemed it — Gold that arrives but leaves the deck
       * capped at yesterday's number reads as the code not having worked.
       */
      ENTITLEMENT_QUERIES.forEach((queryKey) => {
        void queryClient.invalidateQueries({ queryKey });
      });
      void queryClient.invalidateQueries({ queryKey: ['earn'] });
      void queryClient.invalidateQueries({ queryKey: ['promo'] });

      const copy = tNow();
      if (result.kind === 'COIN') {
        feedback.reward();
        showToast({
          id: `promo:${result.coinBalance}`,
          title: copy.settings.promoScreen.successCoinsTitle,
          body: copy.settings.promoScreen.successCoinsBody(result.coinBalance),
          icon: Coins,
          tone: 'gold',
        });
      } else {
        feedback.purchase();
        showToast({
          id: `promo:gold:${result.goldExpiresAt ?? ''}`,
          title: copy.settings.promoScreen.successGoldTitle,
          body: copy.billing.goldYoursBody,
          icon: Crown,
          tone: 'gold',
        });
      }

      setTyped('');
      setDone(result);
    },
  });

  const day = (iso: string) =>
    new Date(iso).toLocaleDateString(t.locale, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });

  const reward = (code: { kind: string; coinAmount: number | null; goldDays: number | null }) =>
    code.kind === 'COIN'
      ? t.settings.promoScreen.rewardCoins(code.coinAmount ?? 0)
      : t.settings.promoScreen.rewardGold(code.goldDays ?? 0);

  /**
   * What happened, on a screen of its own.
   *
   * The same shape the password screen uses, and for a related reason: something has just
   * changed that the person cannot see from here. The balance and the expiry are the
   * server's numbers rather than a local sum, so they are right even when the cached
   * balance was minutes old.
   */
  if (done) {
    return (
      <Screen scroll edges={['top', 'bottom']}>
        <View className="items-center gap-3 pb-6 pt-16">
          <Icon as={done.kind === 'COIN' ? Coins : Crown} size={48} tone="gold" />
          <Text variant="title">
            {done.kind === 'COIN'
              ? t.settings.promoScreen.successCoinsTitle
              : t.settings.promoScreen.successGoldTitle}
          </Text>
          <Text variant="body" className="text-center text-muted">
            {done.kind === 'COIN'
              ? t.settings.promoScreen.successCoinsBody(done.coinBalance)
              : done.goldExpiresAt
                ? t.settings.promoScreen.successGoldBody(day(done.goldExpiresAt))
                : t.settings.promoScreen.successGoldTitle}
          </Text>
        </View>

        <View className="mt-auto pt-10">
          <Button label={t.settings.promoScreen.done} onPress={() => setDone(null)} />
        </View>
      </Screen>
    );
  }

  const waiting = mine.data?.waiting ?? [];
  const used = mine.data?.redeemed ?? [];

  return (
    <Screen scroll edges={['top', 'bottom']}>
      <BackHeader title={t.settings.promoScreen.title} />

      <View className="gap-2 pb-6">
        <Text variant="caption">{t.settings.promoScreen.subtitle}</Text>
      </View>

      {mine.error && <ErrorNotice error={mine.error} onRetry={() => void mine.refetch()} />}
      {redeem.error && <ErrorNotice error={redeem.error} />}

      <Text variant="overline" className="mb-2">
        {t.settings.promoScreen.waiting}
      </Text>

      {waiting.length === 0 ? (
        <Text variant="caption" className="mb-6">
          {t.settings.promoScreen.waitingEmpty}
        </Text>
      ) : (
        <View className="mb-6 gap-3">
          {waiting.map((code: WaitingPromoCode) => (
            <Card key={code.id} className="gap-2">
              <Text variant="bodyStrong">{reward(code)}</Text>
              <Text variant="caption" selectable>
                {code.code}
              </Text>
              <Text variant="caption" className="text-muted">
                {t.settings.promoScreen.validUntil(day(code.expiresAt))}
              </Text>
              <View className="mt-2">
                <Button
                  label={t.settings.promoScreen.redeem}
                  onPress={() => redeem.mutate(code.code)}
                  disabled={redeem.isPending}
                />
              </View>
            </Card>
          ))}
        </View>
      )}

      <Text variant="overline" className="mb-2">
        {t.settings.promoScreen.enterTitle}
      </Text>

      <TextField
        label={t.settings.promoScreen.codeLabel}
        value={typed}
        onChangeText={setTyped}
        // Codes are stored uppercase and punctuation is stripped server-side, so this only
        // has to make typing one feel right.
        autoCapitalize="characters"
        autoCorrect={false}
        hint={t.settings.promoScreen.codeHint}
      />

      <View className="mt-4">
        <Button
          label={t.settings.promoScreen.redeem}
          // Secondary while a waiting code is on screen with a gradient button of its own:
          // one gradient per screen is what makes it mean "this is the action". When
          // nothing is waiting, this is the only thing to do here, so it takes the accent.
          variant={waiting.length > 0 ? 'secondary' : 'primary'}
          onPress={() => redeem.mutate(typed.trim())}
          loading={redeem.isPending}
          disabled={typed.trim().length < 4 || redeem.isPending}
        />
      </View>

      {used.length > 0 && (
        <View className="mt-10 gap-2">
          <Text variant="overline">{t.settings.promoScreen.history}</Text>
          {used.map((code) => (
            <View key={`${code.code}-${code.redeemedAt}`} className="flex-row justify-between">
              <Text variant="caption">{code.code}</Text>
              <Text variant="caption" className="text-muted">
                {reward(code)} · {day(code.redeemedAt)}
              </Text>
            </View>
          ))}
        </View>
      )}
    </Screen>
  );
}
