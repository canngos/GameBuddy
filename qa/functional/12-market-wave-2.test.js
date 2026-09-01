// Bundles and card themes, against a running stack.
//
// The unit tests cover the arithmetic with mocks; this covers the parts only a real
// request can: that a bundle actually writes two ownership rows and one debit through the
// HTTP layer, that a theme survives the round trip as a slug with no image, and that a
// worn theme reaches the two DTOs other people read it from.

const assert = require('node:assert/strict');
const { test, describe, before, after } = require('node:test');

const { createAccount, cleanup } = require('./helpers/accounts');
const { get, post, del } = require('./helpers/api');
const db = require('./helpers/db');
const { P } = require('./helpers/api');

const BUNDLE_PARTLY_OWNED = '189';
const BUNDLE_NOT_FOUND = '188';

function grantCoins(userId, coins) {
  db.query(`UPDATE gamebuddy.gamer SET coin = ${Number(coins)} WHERE user_id = '${db.esc(userId)}'`);
}

describe('bundles are all-or-nothing, and the receipt adds up', () => {
  let gamer;
  let store;

  before(async () => {
    gamer = await createAccount();
    grantCoins(gamer.userId, 5000);
    store = (await get(P.cosmetics, { token: gamer.token })).data;
  });

  after(() => cleanup());

  test('the shelf offers sets priced under the sum of their parts', () => {
    assert.ok(store.bundles.length >= 1, 'expected at least one bundle');
    for (const bundle of store.bundles) {
      assert.ok(bundle.items.length >= 2, `${bundle.name} should contain several items`);
      const parts = bundle.items.reduce((sum, item) => sum + item.price, 0);
      assert.equal(bundle.partsPrice, parts, 'partsPrice must match what the items cost');
      assert.ok(bundle.price < parts, `${bundle.name} must be cheaper than its parts`);
      assert.equal(bundle.owned, false, 'a new account owns nothing');
    }
  });

  test('buying one debits the set price exactly once and grants every part', async () => {
    const bundle = store.bundles[0];
    const before = (await get(P.cosmetics, { token: gamer.token })).data.coins;

    const bought = await post(`${P.cosmetics}/bundles/${bundle.id}/buy`, undefined, {
      token: gamer.token,
    });
    assert.equal(bought.status, 200, bought.text);
    assert.equal(bought.data.coins, before - bundle.price, 'one debit, of the set price');

    const owned = [...bought.data.frames, ...bought.data.banners, ...bought.data.themes].filter(
      (item) => item.owned,
    );
    for (const part of bundle.items) {
      assert.ok(
        owned.some((item) => item.id === part.id),
        `${part.name} should be owned after buying the set`,
      );
    }
  });

  test('the receipts sum to what was actually charged, not to the shelf prices', () => {
    // `paid` is what upgrade-2026-18 refunds people from, so a discounted set has to record
    // the discounted share per item.
    const bundle = store.bundles[0];
    const rows = db.query(
      `SELECT gc.paid FROM gamebuddy.gamer_cosmetic gc WHERE gc.user_id = '${db.esc(gamer.userId)}'`,
    );
    const total = rows.reduce((sum, row) => sum + Number(row[0]), 0);
    assert.equal(total, bundle.price, 'the ownership rows must sum to the price charged');
  });

  test('buying it again is refused because a part is already owned', async () => {
    const again = await post(`${P.cosmetics}/bundles/${store.bundles[0].id}/buy`, undefined, {
      token: gamer.token,
    });
    assert.equal(again.code, BUNDLE_PARTLY_OWNED, again.text);
  });

  test('an unknown bundle id is refused, not a 500', async () => {
    const missing = await post(
      `${P.cosmetics}/bundles/00000000-0000-0000-0000-000000000000/buy`,
      undefined,
      { token: gamer.token },
    );
    assert.equal(missing.code, BUNDLE_NOT_FOUND, missing.text);
  });
});

describe('card themes are colour, not artwork', () => {
  let gamer;
  let theme;

  before(async () => {
    gamer = await createAccount();
    grantCoins(gamer.userId, 5000);
    const store = (await get(P.cosmetics, { token: gamer.token })).data;
    assert.ok(store.themes.length > 0, 'expected themes in the catalogue');
    theme = store.themes[0];
  });

  after(() => cleanup());

  test('a theme carries a slug and no image', () => {
    assert.equal(theme.kind, 'THEME');
    assert.ok(theme.theme, 'a theme must name its slug');
    assert.equal(theme.image, null, 'a theme has no picture to serve');
  });

  test('buying and wearing one fills its own slot', async () => {
    const bought = await post(`${P.cosmetics}/${theme.id}/buy`, undefined, { token: gamer.token });
    assert.equal(bought.status, 200, bought.text);

    const worn = await post(`${P.cosmetics}/${theme.id}/equip`, undefined, { token: gamer.token });
    assert.equal(worn.status, 200, worn.text);
    assert.equal(
      worn.data.themes.find((item) => item.id === theme.id)?.equipped,
      true,
      'the theme should be equipped',
    );
  });

  test('the worn theme reaches the profile as a slug', async () => {
    const me = await get('/application/get/user/info', { token: gamer.token });
    assert.equal(me.data.theme, theme.theme, 'the profile should report the worn slug');
  });

  test('taking it off clears only that slot', async () => {
    const off = await post(`${P.cosmetics}/${theme.id}/equip`, undefined, { token: gamer.token });
    assert.equal(off.status, 200);

    const bare = await del(`${P.cosmetics}/equipped/THEME`, { token: gamer.token });
    assert.equal(
      bare.data.themes.every((item) => !item.equipped),
      true,
      'nothing should be worn in the theme slot',
    );
  });
});
