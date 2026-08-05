import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { communityApi } from '../../../src/api/community';
import { Text, TextField } from '../../../src/ui';
import { EditScreen } from '../../../src/ui/EditScreen';

const TITLE_MAX = 120;
const BODY_MAX = 4000;

/**
 * Writing a post.
 *
 * The title is the only required part — the backend rejects a blank one and accepts a
 * blank body, which suits a community where half the posts are "anyone on tonight?".
 */
export default function ComposePost() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { communityId, name } = useLocalSearchParams<{ communityId: string; name?: string }>();

  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');

  const publish = useMutation({
    mutationFn: () => communityApi.createPost(communityId, title.trim(), body.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['communityPosts', communityId] });
      void queryClient.invalidateQueries({ queryKey: ['communityFeed'] });
      void queryClient.invalidateQueries({ queryKey: ['communities'] });
      router.back();
    },
  });

  return (
    <EditScreen
      title="New post"
      subtitle={name ? `Posting in ${name}` : undefined}
      onSave={() => publish.mutate()}
      saving={publish.isPending}
      canSave={title.trim().length > 0}
      error={publish.error}
      saveLabel="Post"
    >
      <View className="gap-5">
        <TextField
          label="Title"
          value={title}
          onChangeText={setTitle}
          placeholder="Looking for two, ranked, tonight"
          maxLength={TITLE_MAX}
        />
        <View className="gap-1">
          <TextField
            label="Anything else"
            value={body}
            onChangeText={setBody}
            placeholder="Optional."
            multiline
            maxLength={BODY_MAX}
            className="min-h-[140px]"
          />
          <Text variant="caption" className="text-right">
            {body.length}/{BODY_MAX}
          </Text>
        </View>
      </View>
    </EditScreen>
  );
}
