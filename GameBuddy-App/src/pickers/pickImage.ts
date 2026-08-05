import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';

/**
 * The three ways to get a picture off a phone.
 *
 * People do not think of these as one thing. A photo you already took is in the gallery;
 * a photo you are about to take needs the camera; and an image that arrived over chat, or
 * was drawn on a desktop and synced, is in Files and often not in the gallery at all.
 * Offering only the gallery quietly excludes the third case, which for an app about
 * gaming identities is exactly where the good avatars are.
 *
 * All three return the same shape so the caller has one code path and one set of states
 * to render.
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
 * Crop-to-square, offered by the two pickers that support it.
 *
 * The server centre-crops whatever arrives, so this is not what makes the avatar square —
 * it is what lets the gamer decide *which* square, rather than having a portrait photo
 * silently lose whichever half it loses.
 */
const EDIT: ImagePicker.ImagePickerOptions = {
  mediaTypes: 'images',
  allowsEditing: true,
  aspect: [1, 1],
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

/**
 * The file system, for images that never reached the gallery.
 *
 * No crop step: the document picker has no editing UI, so these arrive whole and the
 * server's centre-crop decides. That is a real difference from the other two and the
 * reason the button says "file" rather than "photo".
 *
 * `copyToCacheDirectory` is left on. Without it the URI can be a content:// handle that
 * is only valid while the picker is open, and the upload then fails on a file that was
 * there a second ago.
 */
export async function pickFromFiles(): Promise<PickOutcome> {
  const result = await DocumentPicker.getDocumentAsync({
    type: ['image/*'],
    copyToCacheDirectory: true,
    multiple: false,
  });
  if (result.canceled) return { kind: 'cancelled' };

  const asset = result.assets[0];
  if (!asset) return { kind: 'cancelled' };

  // Checked here because this is the one source that can hand over an arbitrary file.
  // The server refuses anything over its multipart limit, but it does so after the whole
  // body has crossed the network — which on a phone is the user watching a progress bar
  // fill up in order to be told no.
  if (asset.size != null && asset.size > MAX_BYTES) {
    return { kind: 'tooLarge', bytes: asset.size };
  }

  return { kind: 'picked', uri: asset.uri, mimeType: asset.mimeType };
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
