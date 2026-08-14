#!/usr/bin/env node
/**
 * Renders the Open Graph card to `public/og.png`.
 *
 * ## Why a PNG, and why generated
 *
 * The card is what a link to findgamebuddy.com looks like when someone pastes it into
 * Discord, WhatsApp or X — which for an app aimed at gamers is a realistic share path, and
 * the difference between a bare blue URL and something that looks like a product.
 *
 * It has to be a raster image: **most social scrapers do not render SVG Open Graph images**,
 * and the ones that do are not the ones that matter here. So the design is written as SVG
 * because that is a sane way to describe a layout, and rasterised at build time by sharp —
 * which is already a dependency, because `astro:assets` uses it to optimise the screenshots.
 *
 * 1200×630 is the size every platform crops from. Anything important is kept well inside the
 * edges, since several of them crop to a squarer aspect on mobile.
 *
 * Text is drawn as SVG `<text>` with a system-font stack rather than the site's Chakra Petch.
 * Embedding the real font would mean base64-ing a woff2 into this file to gain a typeface
 * nobody examines at card size — the layout and the gradient carry the identity.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const here = dirname(fileURLToPath(import.meta.url));

const WIDTH = 1200;
const HEIGHT = 630;

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}">
  <defs>
    <linearGradient id="brand" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#7C4DFF"/>
      <stop offset="100%" stop-color="#00E5FF"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0%" stop-color="#7C4DFF" stop-opacity="0.55"/>
      <stop offset="100%" stop-color="#7C4DFF" stop-opacity="0"/>
    </radialGradient>
  </defs>

  <!-- The app's canvas colour, so the card and the site are recognisably the same thing. -->
  <rect width="${WIDTH}" height="${HEIGHT}" fill="#0B0B12"/>
  <ellipse cx="880" cy="120" rx="520" ry="360" fill="url(#glow)"/>

  <!-- Mark. Same rounded square and controller as the favicon. -->
  <rect x="80" y="82" width="72" height="72" rx="18" fill="url(#brand)"/>
  <g fill="none" stroke="#FFFFFF" stroke-width="3.8" stroke-linecap="round" stroke-linejoin="round"
     transform="translate(80 82) scale(1.125)">
    <path d="M20 25h24a10 10 0 0 1 9.6 12.8l-2.4 8A6 6 0 0 1 40 47l-3-4H27l-3 4a6 6 0 0 1-10.8-1.2l-2.4-8A10 10 0 0 1 20 25Z"/>
    <path d="M21 33v6M18 36h6"/>
  </g>
  <text x="172" y="132" font-family="Segoe UI, Roboto, Helvetica, Arial, sans-serif"
        font-size="34" font-weight="700" fill="#ECECF5">GameBuddy</text>

  <!-- Headline. Split by hand rather than wrapped: SVG has no text wrapping, and three
       measured lines beat one that runs off the edge on a narrow crop. -->
  <text x="80" y="286" font-family="Segoe UI, Roboto, Helvetica, Arial, sans-serif"
        font-size="76" font-weight="700" fill="#ECECF5">Find people who</text>
  <text x="80" y="374" font-family="Segoe UI, Roboto, Helvetica, Arial, sans-serif"
        font-size="76" font-weight="700" fill="url(#brand)">actually play</text>
  <text x="80" y="462" font-family="Segoe UI, Roboto, Helvetica, Arial, sans-serif"
        font-size="76" font-weight="700" fill="#ECECF5">what you play</text>

  <text x="80" y="536" font-family="Segoe UI, Roboto, Helvetica, Arial, sans-serif"
        font-size="30" fill="#8A8AA3">Matched on your games, your platform, your play style</text>

  <rect x="80" y="566" width="132" height="4" rx="2" fill="url(#brand)"/>
</svg>`;

const out = join(here, '..', 'public', 'og.png');
mkdirSync(dirname(out), { recursive: true });

const buffer = await sharp(Buffer.from(svg)).png({ compressionLevel: 9 }).toBuffer();
writeFileSync(out, buffer);

console.log(`[og] wrote public/og.png — ${WIDTH}×${HEIGHT}, ${(buffer.length / 1024).toFixed(0)} kB`);
