import * as ImagePicker from 'expo-image-picker';

/**
 * The two ways to get a picture off a phone.
 *
 * People do not think of these as one thing: a photo you already took is in the gallery, and
 * a photo you are about to take needs the camera. Both return the same shape so the caller
 * has one code path and one set of states to render.
 *
 * There was a third — the system document picker, for images sitting in Files and never
 * added to the gallery. It crashed the app in production, and it is removed rather than
 * repaired: the import is gone too, so this bundle never loads that native module at all,
 * which is what makes the fix reachable over the air instead of needing a new build.
 */

/** What the server will accept, and roughly what its multipart limit allows. */
export const MAX_BYTES = 8 * 1024 * 1024;

export type PickOutcome =
  | { kind: 'picked'; uri: string; mimeType?: string | null }
  /** The picker was dismissed. Not an error, and nothing should be shown for it. */
  | { kind: 'cancelled' }
  | { kind: 'denied'; need: 'photos' | 'camera' }
  | { kind: 'tooLarge'; bytes: number };

/**
 * No `allowsEditing`, deliberately.
 *
 * That flag opened Android's own square editor, and {@link AvatarCropper} now does the job
 * properly — a circle, at the size it is worn, with the gamer's frame drawn on top. Leaving
 * both in would mean cropping twice: once in a square the OS drew, then again in ours, with
 * the second crop confined to whatever the first one kept.
 *
 * Turning it off also makes the three sources consistent for the first time. The document
 * picker never had an editor, so a file arrived whole and the server centre-cropped it;
 * now every path lands in the same place.
 */
const EDIT: ImagePicker.ImagePickerOptions = {
  mediaTypes: 'images',
  // The server re-encodes to 512px square regardless, so a 12MP original is bytes over a
  // mobile connection that are discarded on arrival.
  quality: 0.8,
};

export async function pickFromLibrary(): Promise<PickOutcome> {
  const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!permission.granted) return { kind: 'denied', need: 'photos' };

  return fromImagePicker(await ImagePicker.launchImageLibraryAsync(EDIT));
}

export async function takePhoto(): Promise<PickOutcome> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) return { kind: 'denied', need: 'camera' };

  return fromImagePicker(await ImagePicker.launchCameraAsync(EDIT));
}

function fromImagePicker(result: ImagePicker.ImagePickerResult): PickOutcome {
  if (result.canceled) return { kind: 'cancelled' };

  const asset = result.assets[0];
  if (!asset) return { kind: 'cancelled' };

  if (asset.fileSize != null && asset.fileSize > MAX_BYTES) {
    return { kind: 'tooLarge', bytes: asset.fileSize };
  }
  return { kind: 'picked', uri: asset.uri, mimeType: asset.mimeType };
}
