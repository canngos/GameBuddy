# GameBuddy Feedback and Issues

## 1. Rename "Keywords" to a More User-Friendly Concept

- The current "Keywords" appear to represent the user's **gaming stereotype, personality, or play style** rather than actual keywords.
- The term **Keywords** should not be shown to users in the Settings or profile UI.
- Consider using a more user-friendly name that better represents the feature, such as a category related to:
  - Play Style
  - Gaming Style
  - Player Type
  - Gaming Personality
- The terminology should clearly communicate what these selections represent.

## 2. Home Screen Is Still Cut Off on 5.8-Inch Phones

- As proof check the "feedback-v3-homepage-proof.png" screenshot.
- The layout issue on smaller phones is still present.
- On a **5.8-inch phone**, the content on the Home screen still appears to be cut off from the top.
- The responsive layout should be tested again specifically on smaller screen sizes and adjusted accordingly.

## 3. Daily Streak Timer Is Incorrect

- I claimed the daily streak reward, and it showed that the next reward would be available in **19 hours**.
- The daily streak should reset based on a proper **24-hour period** or a clearly defined daily reset time.
- The current timer behavior appears incorrect and should be reviewed.

## 4. Super Like Is Missing in the Production Environment

- The **Super Like** feature is not available in the production version of the Home tab.
- An **EAS Update** was deployed, but a new build was not created.
- Investigate whether the missing feature could be related to using an OTA/EAS Update instead of creating a new build.
- Also verify the feature locally on the emulator to ensure that it is working correctly in the current codebase.

## 5. Chat Still Does Not Open at the Latest Message

- The issue with chats not automatically scrolling to the latest message is still present.
- When opening a conversation, the latest message is not immediately visible.
- The user still has to manually scroll down.
- This should also be tested and reproduced on the emulator.
- The chat should always open with the latest message visible.

## 6. Online Status Does Not Restore When Returning to the App

- When I open the app, my status correctly changes to **Online**.
- When I lock the phone, the status correctly changes to **Last Seen** or offline.
- However, when I unlock the phone and return to the app, my status does not change back to **Online**.
- If the app becomes active and visible again, the user's presence status should automatically update back to **Online**.

## 7. Withdrawn Friend Requests Remain Visible

- Sending a friend request works correctly, and the request appears on the other user's side.
- However, after withdrawing the request, it still remains visible in the other user's **Messages** tab.
- The withdrawn request only disappears after the user enters a chat and then returns to the Messages tab.
- The request should disappear immediately after it has been withdrawn without requiring any additional navigation or refresh.

### Additional Issues

- If the other user tries to accept the already withdrawn request, an error message is shown. This behavior is understandable, but the withdrawn request should not still be visible in the first place.
- After sending a new friend request again, the new request appears correctly.
- However, the previous error message remains visible underneath the new request.
- This creates a confusing and incorrect UI state.

### Error Message Behavior

- Error messages should only remain visible for a limited period of time.
- They should automatically disappear after a reasonable duration.
- Old error messages should also be cleared when the underlying state changes, such as when a new friend request is successfully received.
- Showing an old error message after a new request has arrived is especially incorrect.

## 8. Add Friend Button Has No Function After Becoming Friends

- In the chat screen, tapping the **Add Friend** button after both users are already friends does nothing.
- Once users become friends, this button should either:
  - Change into a different action, such as **Remove Friend**, or
  - Open a menu with friendship-related actions.
- The button should not remain visible without any functionality.