// @ts-check
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'astro/config';

/**
 * https://astro.build/config
 *
 * Static output, which is the default and is the point: every page is finished HTML by the
 * time it reaches a crawler, and Cloudflare Pages serves it from the edge with no runtime.
 */
export default defineConfig({
  /**
   * Required, not optional. `site` is what `@astrojs/sitemap` writes absolute URLs from and
   * what `Astro.site` resolves canonical links against — without it the sitemap is emitted
   * with relative paths and is silently useless, which is the sort of failure nobody notices
   * until they wonder why nothing is indexed.
   */
  site: 'https://findgamebuddy.com',

  /**
   * Seven languages on the marketing pages.
   *
   * `prefixDefaultLocale: false` keeps English at the bare paths — `/support`, not
   * `/en/support`. The apex is what gets shared, linked to and printed on a store listing,
   * and sending it through a redirect costs a round trip on the most common entry point of
   * all.
   *
   * `/terms` and `/privacy` are deliberately **not** localised: they are binding documents,
   * and a mistranslated clause is a document that says something nobody intended. Every
   * language links to the English pages, which say so on the page rather than leaving a
   * visitor to work it out.
   */
  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'fi', 'sv', 'de', 'fr', 'es', 'tr'],
    routing: { prefixDefaultLocale: false },
  },

  integrations: [
    sitemap({
      /**
       * Tells Google that the seven copies of a page are translations of one another rather
       * than duplicates competing with each other. Without it, a small site in seven
       * languages looks like thin duplicated content, which is the opposite of the intent.
       */
      i18n: {
        defaultLocale: 'en',
        locales: { en: 'en', fi: 'fi', sv: 'sv', de: 'de', fr: 'fr', es: 'es', tr: 'tr' },
      },
    }),
  ],

  vite: {
    plugins: [tailwindcss()],
    server: {
      fs: {
        /**
         * The terms and privacy policy are imported from `../documentation/legal/`, which is
         * outside this project. Vite refuses to serve files above the project root unless
         * told otherwise, and the refusal in dev looks like a missing file rather than a
         * permission.
         *
         * Reading them from where they already live is the whole point: `/terms` and
         * `/privacy` render the same documents the app links to and a lawyer reviews, rather
         * than a copy that drifts the first time one is amended.
         */
        allow: ['..'],
      },
    },
  },
});
