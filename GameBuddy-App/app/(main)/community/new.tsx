import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { communityApi } from '../../../src/api/community';
import { Text, TextField } from '../../../src/ui';
import { EditScreen } from '../../../src/ui/EditScreen';

const NAME_MAX = 60;
const DESCRIPTION_MAX = 300;

/**
 * Creating a community. You become its owner and its first member.
 *
 * No avatar or wallpaper is asked for even though the backend accepts both: they are
 * URLs to images nothing can host yet. A picker that produces a field the app cannot
 * fill would be a dead control on the very first screen of the feature.
 */
export default function NewCommunity() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const create = useMutation({
    mutationFn: () => communityApi.create(name.trim(), description.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['communities'] });
      void queryClient.invalidateQueries({ queryKey: ['communityFeed'] });
      // Back to the tab root rather than into the new community: create returns only a
      // message, not the id, so there is nothing to navigate to.
      router.back();
    },
  });

  const ready = name.trim().length > 0 && description.trim().length > 0;

  return (
    <EditScreen
      title="New community"
      subtitle="A place for people who care about the same game. You will own it."
      onSave={() => create.mutate()}
      saving={create.isPending}
      canSave={ready}
      error={create.error}
      saveLabel="Create"
    >
      <View className="gap-5">
        <TextField
          label="Name"
          value={name}
          onChangeText={setName}
          placeholder="Helsinki Valorant"
          maxLength={NAME_MAX}
          autoCapitalize="words"
        />
        <View className="gap-1">
          <TextField
            label="What it is for"
            value={description}
            onChangeText={setDescription}
            placeholder="Ranked squads, evenings, no rage."
            multiline
            maxLength={DESCRIPTION_MAX}
            className="min-h-[96px]"
          />
          <Text variant="caption" className="text-right">
            {description.length}/{DESCRIPTION_MAX}
          </Text>
        </View>
      </View>
    </EditScreen>
  );
}
