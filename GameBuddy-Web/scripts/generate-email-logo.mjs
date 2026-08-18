#!/usr/bin/env node
/**
 * Renders the Duo-Grip mark to `GameBuddy-backend/src/main/resources/email/logo.png`.
 *
 * ## Why a PNG, and why it lives in the backend
 *
 * **No email client renders SVG.** Gmail, Outlook and Apple Mail all strip it, so the one
 * place the mark cannot be the drawing every other surface uses is the one place it has to
 * be a raster.
 *
 * The file is written into the backend's resources rather than served from this site,
 * because the mail attaches it to the message itself (`cid:` inline) instead of linking to
 * a URL. That makes the asset ship in the same jar as the code that references it: there is
 * no window in which the backend is deployed and the image is not, and no email in the
 * wild pointing at a path a later site redeploy might move. It also renders in clients that
 * block remote images, which is the ordinary state of Outlook for a sender nobody has
 * approved yet.
 *
 * The source is `public/favicon.svg` — the reference copy of the mark — read rather than
 * redrawn, so this cannot drift from the brand the way a fifth hand-copy would. Regenerate
 * whenever the favicon changes; it is not part of `npm run generate`, because it writes
 * outside this project and the mark changes roughly never.
 *
 * Displayed at 56 CSS px and rendered at 3× so it stays sharp on retina, where a
 * 56px-wide asset is visibly soft.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const here = dirname(fileURLToPath(import.meta.url));

const SOURCE = join(here, '..', 'public', 'favicon.svg');
const OUT_DIR = join(here, '..', '..', 'GameBuddy-backend', 'src', 'main', 'resources', 'email');
const OUT = join(OUT_DIR, 'logo.png');

const SIZE = 168;

// A high density before the resize: sharp rasterises the SVG at `density` and then scales,
// so rendering small and enlarging would throw away the curve quality the mark is made of.
const png = await sharp(readFileSync(SOURCE), { density: 384 })
  .resize(SIZE, SIZE)
  .png({ compressionLevel: 9 })
  .toBuffer();

mkdirSync(OUT_DIR, { recursive: true });
writeFileSync(OUT, png);

console.log(`generate-email-logo: ${OUT} (${SIZE}×${SIZE}, ${png.length} bytes)`);
