-- Removes Steam from account linking.
--
-- Run after upgrade-2026-40-linked-accounts.sql. Idempotent.
--
-- Steam linking shipped alongside Discord in upgrade 40 and never worked outside
-- development: the free Web API key it needs could not be obtained for this account, and a
-- provider whose only possible outcome is an error reads as a broken app rather than an
-- unconfigured one. The Steam client classes, endpoints, configuration and UI are gone in
-- the same change.
--
-- **The tables stay exactly as they are.** `gamer_linked_account.provider` is a
-- `varchar(16)` holding the LinkedProvider enum name rather than a Postgres enum type, for
-- precisely this reason -- adding or dropping a provider is a deploy, not an ALTER TYPE
-- coordinated with a running application. `handle_refreshed_at` also stays: it is nullable,
-- it costs nothing, and it is what a returning Steam integration would use again.
--
-- What does have to happen is the data. `LinkedProvider.from("STEAM")` now returns null and
-- every read path treats an unknown provider as a bad request, so a surviving Steam row
-- would be a profile that fails to load rather than one that quietly omits a badge.

BEGIN;

-- Verified Steam identities on profiles. Deleting these does not touch the Steam account
-- itself -- no token was ever stored, so there is nothing at Valve's end to revoke, and the
-- row was only ever a claim we had checked once.
DELETE FROM gamebuddy.gamer_linked_account WHERE provider = 'STEAM';

-- Half-finished link attempts. These expire ten minutes after they are minted and the
-- hourly sweep would clear them anyway, but the sweep only runs while the application is
-- up and a ticket read back with an unknown provider throws before it can be discarded.
DELETE FROM gamebuddy.account_link_ticket WHERE provider = 'STEAM';

COMMENT ON TABLE gamebuddy.gamer_linked_account IS
    'Provider-verified Discord identities. The handle is fetched, never typed.';

COMMIT;
