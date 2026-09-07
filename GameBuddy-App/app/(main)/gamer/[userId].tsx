import { GamerProfileScreen } from '../../../src/profile/GamerProfileScreen';

/**
 * Somebody else's profile, reached from the friends list.
 *
 * A second mount of the same screen rather than a link into the Messages stack: pushing
 * `/messages/gamer/[userId]` from here would switch tabs, and backing out would land in
 * the inbox rather than back among the friends — the exact fault the layout notes warn
 * about for screens pushed across stacks.
 */
export default function GamerProfileFromFriends() {
  return <GamerProfileScreen afterBlock="/friends" />;
}
