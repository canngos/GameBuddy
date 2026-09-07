import { GamerProfileScreen } from '../../../../src/profile/GamerProfileScreen';

/**
 * Somebody else's profile, reached from the top of a conversation.
 *
 * Inside the Messages stack on purpose — see the note in `app/(main)/_layout.tsx` about
 * why a pushed screen belongs in the stack it was opened from. The same screen is also
 * mounted at `/gamer/[userId]` for the friends list; both render the component so there
 * is one implementation and two back stacks.
 */
export default function GamerProfileFromChat() {
  return <GamerProfileScreen afterBlock="/messages" />;
}
