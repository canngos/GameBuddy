import { api } from './client';
import type { CosmeticKind, CosmeticStore } from './types';

/**
 * Frames and banners: the store, and the two things you can do to an item.
 *
 * Every call returns the whole store rather than an acknowledgement. That is the
 * server's contract and it is the right one here: after a purchase the balance, the
 * owned flag and the equipped flag have all moved, and a client that has to refetch to
 * learn that will render a shelf that briefly disagrees with itself — an item shown as
 * still buyable, next to a coin balance that has already dropped.
 */
export const cosmeticsApi = {
  store: () => api.get<CosmeticStore>('/application/cosmetics'),

  buy: (cosmeticId: string) =>
    api.post<CosmeticStore>(`/application/cosmetics/${cosmeticId}/buy`),

  /**
   * Buys a whole set at the set's price.
   *
   * All or nothing: owning any part is refused with `BUNDLE_PARTLY_OWNED` rather than
   * charged for a smaller set.
   */
  buyBundle: (bundleId: string) =>
    api.post<CosmeticStore>(`/application/cosmetics/bundles/${bundleId}/buy`),

  equip: (cosmeticId: string) =>
    api.post<CosmeticStore>(`/application/cosmetics/${cosmeticId}/equip`),

  /**
   * Takes off whatever is in one slot.
   *
   * Keyed by kind, not by the item's id: "remove my frame" is what the gamer means, and
   * it does not require the screen to know what is currently on.
   */
  unequip: (kind: CosmeticKind) =>
    api.delete<CosmeticStore>(`/application/cosmetics/equipped/${kind}`),
};
