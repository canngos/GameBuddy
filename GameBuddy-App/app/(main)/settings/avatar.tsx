import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../../src/api/auth';
import { profileApi } from '../../../src/api/catalogue';
import type { AvatarUpload } from '../../../src/api/types';
import { AvatarPicker } from '../../../src/pickers/AvatarPicker';
import {
  MAX_BYTES,
  pickFromFiles,
  pickFromLibrary,
  takePhoto,
  type PickOutcome,
} from '../../../src/pickers/pickImage';
import { Avatar, Button, Card, ErrorNotice, Text } from '../../../src/ui';
import { EditScreen } from '../../../src/ui/EditScreen';

/**
 * Two ways to have a picture: upload one, or pick one of ours.
 *
 * Upload comes first because it is what people actually want. The defaults exist so that
 * nobody is forced through a camera roll to finish signing up, not as the headline.
 *
 * The upload deliberately does not go through the Save button. Uploading *is* the save —
 * by the time the server answers it has already stored, screened and possibly published
 * the image, and there is nothing left to confirm. Save applies only to picking a
 * default, which is an ordinary field change.
 */
export default function EditAvatar() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });

  // Nothing is preselected. `/application/get/user/info` returns the avatar's URL, not
  // the catalogue id behind it, so the current choice cannot be matched against the
  // picker's ids without a second lookup. Showing nothing selected is honest; guessing
  // would highlight the wrong one.
  const [avatarId, setAvatarId] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<AvatarUpload | null>(null);
  const [refused, setRefused] = useState<PickOutcome | null>(null);

  /**
   * One mutation for all three sources — which picker to open is a parameter, not a
   * branch.
   *
   * Everything after "the gamer chose a file" is identical whichever way they got there,
   * and three copies of the upload-and-report path would be three places for the outcome
   * handling to drift apart.
   */
  const upload = useMutation({
    mutationFn: async (pick: () => Promise<PickOutcome>) => {
      setRefused(null);
      setOutcome(null);

      const picked = await pick();
      if (picked.kind !== 'picked') {
        // Cancelling is not a failure and shows nothing. A refused permission or an
        // oversized file is worth explaining, and neither is an upload error.
        if (picked.kind !== 'cancelled') setRefused(picked);
        return null;
      }
      return profileApi.uploadAvatar(picked.uri, picked.mimeType);
    },
    onSuccess: (result) => {
      if (!result) return;
      setOutcome(result);
      void queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  const save = useMutation({
    mutationFn: () => authApi.changeAvatar(avatarId!),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      router.back();
    },
  });

  return (
    <EditScreen
      title="Your avatar"
      subtitle="Upload a photo, or pick one of ours."
      onSave={() => save.mutate()}
      saving={save.isPending}
      canSave={!!avatarId}
      error={save.error}
      saveLabel="Use this avatar"
    >
      <View className="gap-7">
        <View className="gap-3">
          <Text variant="overline">YOUR OWN PHOTO</Text>

          <View className="flex-row items-center gap-4">
            <Avatar
              source={outcome?.url ?? me.data?.avatar}
              name={me.data?.username}
              colorSeed={me.data?.userId}
              size={72}
            />
            <View className="flex-1 gap-2">
              {/* Three sources, all on screen rather than hidden behind one button and a
                  sheet. There are only three, each is one tap, and someone who keeps
                  their images in Files should not have to discover that. */}
              <Button
                label="Take a photo"
                variant="secondary"
                size="md"
                disabled={upload.isPending}
                onPress={() => upload.mutate(takePhoto)}
              />
              <Button
                label="Choose from photos"
                variant="secondary"
                size="md"
                disabled={upload.isPending}
                onPress={() => upload.mutate(pickFromLibrary)}
              />
              <Button
                label="Choose a file"
                variant="secondary"
                size="md"
                disabled={upload.isPending}
                onPress={() => upload.mutate(pickFromFiles)}
              />
            </View>
          </View>

          {upload.isPending && <Text variant="caption">Uploading and checking…</Text>}

          {refused && <PickRefusal outcome={refused} />}
          {!!upload.error && <ErrorNotice error={upload.error} />}
          {outcome && <UploadOutcome outcome={outcome} />}

          <Text variant="caption">
            Your photo is checked before anyone else can see it, and location data is
            removed from it automatically.
          </Text>
        </View>

        <View className="gap-3">
          <Text variant="overline">OR PICK ONE OF OURS</Text>
          <AvatarPicker selected={avatarId} onSelect={setAvatarId} size={72} />
        </View>
      </View>
    </EditScreen>
  );
}

/** Why nothing was uploaded, when the reason is not an upload failure. */
function PickRefusal({ outcome }: { outcome: PickOutcome }) {
  if (outcome.kind === 'denied') {
    return (
      <Text variant="caption">
        {outcome.need === 'camera'
          ? "GameBuddy cannot open the camera without permission. You can grant it in your phone's settings, choose an existing photo, or pick one of ours below."
          : "GameBuddy cannot open your photos without permission. You can grant it in your phone's settings, take a photo instead, or pick one of ours below."}
      </Text>
    );
  }

  if (outcome.kind === 'tooLarge') {
    return (
      <Text variant="caption" className="text-danger">
        That file is {(outcome.bytes / 1024 / 1024).toFixed(1)}MB and the limit is{' '}
        {MAX_BYTES / 1024 / 1024}MB. Anything from your camera roll will be well under it.
      </Text>
    );
  }
  return null;
}

/**
 * What actually happened, in the user's terms.
 *
 * All three are worth telling apart. "Uploaded!" for a picture nobody else can see would
 * be untrue, and one message covering both "waiting" and "refused" would make one of them
 * a lie — a delay and a decision are different things to be told.
 */
function UploadOutcome({ outcome }: { outcome: AvatarUpload }) {
  if (outcome.status === 'APPROVED') {
    return (
      <Card className="gap-1">
        <Text variant="bodyStrong">That&apos;s your avatar now</Text>
        <Text variant="caption">Everyone can see it.</Text>
      </Card>
    );
  }

  if (outcome.status === 'PENDING') {
    return (
      <Card className="gap-1">
        <Text variant="bodyStrong">Waiting to be checked</Text>
        <Text variant="caption">
          Someone will look at it shortly. Until then you are the only one who can see it —
          everyone else still sees your old avatar.
        </Text>
      </Card>
    );
  }

  return (
    <Card className="gap-1">
      <Text variant="bodyStrong" className="text-danger">
        That photo was not accepted
      </Text>
      <Text variant="caption">
        It looks like it breaks the rules on sexual content. Nobody else has seen it. Try
        another photo, or pick one of ours.
      </Text>
    </Card>
  );
}
