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
import { useUpper } from '../../../src/i18n/case';
import { useT } from '../../../src/i18n/useT';
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
  const t = useT();
  const upper = useUpper();
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
      title={t.settings.avatarScreen.title}
      subtitle={t.settings.avatarScreen.subtitle}
      onSave={() => save.mutate()}
      saving={save.isPending}
      canSave={!!avatarId}
      error={save.error}
      saveLabel={t.settings.avatarScreen.saveLabel}
    >
      <View className="gap-7">
        <View className="gap-3">
          <Text variant="overline">{upper(t.settings.avatarScreen.yourPhoto)}</Text>

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
                label={t.settings.avatarScreen.takePhoto}
                variant="secondary"
                size="md"
                disabled={upload.isPending}
                onPress={() => upload.mutate(takePhoto)}
              />
              <Button
                label={t.settings.avatarScreen.fromPhotos}
                variant="secondary"
                size="md"
                disabled={upload.isPending}
                onPress={() => upload.mutate(pickFromLibrary)}
              />
              <Button
                label={t.settings.avatarScreen.fromFiles}
                variant="secondary"
                size="md"
                disabled={upload.isPending}
                onPress={() => upload.mutate(pickFromFiles)}
              />
            </View>
          </View>

          {upload.isPending && <Text variant="caption">{t.settings.avatarScreen.uploading}</Text>}

          {refused && <PickRefusal outcome={refused} />}
          {!!upload.error && <ErrorNotice error={upload.error} />}
          {outcome && <UploadOutcome outcome={outcome} />}

          <Text variant="caption">{t.settings.avatarScreen.privacyNote}</Text>
        </View>

        <View className="gap-3">
          <Text variant="overline">{upper(t.settings.avatarScreen.orOurs)}</Text>
          <AvatarPicker selected={avatarId} onSelect={setAvatarId} size={72} />
        </View>
      </View>
    </EditScreen>
  );
}

/** Why nothing was uploaded, when the reason is not an upload failure. */
function PickRefusal({ outcome }: { outcome: PickOutcome }) {
  const t = useT();
  if (outcome.kind === 'denied') {
    return (
      <Text variant="caption">
        {outcome.need === 'camera'
          ? t.settings.avatarScreen.cameraDenied
          : t.settings.avatarScreen.photosDenied}
      </Text>
    );
  }

  if (outcome.kind === 'tooLarge') {
    return (
      <Text variant="caption" className="text-danger">
        {t.settings.avatarScreen.tooLarge(
          (outcome.bytes / 1024 / 1024).toFixed(1),
          MAX_BYTES / 1024 / 1024,
        )}
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
  const t = useT();
  if (outcome.status === 'APPROVED') {
    return (
      <Card className="gap-1">
        <Text variant="bodyStrong">{t.settings.avatarScreen.approvedTitle}</Text>
        <Text variant="caption">{t.settings.avatarScreen.approvedBody}</Text>
      </Card>
    );
  }

  if (outcome.status === 'PENDING') {
    return (
      <Card className="gap-1">
        <Text variant="bodyStrong">{t.settings.avatarScreen.pendingTitle}</Text>
        <Text variant="caption">{t.settings.avatarScreen.pendingBody}</Text>
      </Card>
    );
  }

  return (
    <Card className="gap-1">
      <Text variant="bodyStrong" className="text-danger">
        {t.settings.avatarScreen.rejectedTitle}
      </Text>
      <Text variant="caption">{t.settings.avatarScreen.rejectedBody}</Text>
    </Card>
  );
}
