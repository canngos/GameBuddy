# GameBuddy Feedback and Issues

## 1. Unnecessary System Notifications While Using the App

- When the other person sends me a message during an active chat, the message also appears as a system notification at the top of my phone.
- If the user is already online and actively using the app, there is no need to send a system notification.
- The in-app notification is sufficient while the app is active.
- System/push notifications should ideally only be triggered when the app is in the background or the user is offline.

## 2. Test Ads Are Not Opening

- The test advertisement does not open.
- The ad loading and display flow should be checked.

## 3. Super Like Usage Is Not Clear

- Super Likes can be purchased, but it is not clear how they should be used.
- The interaction should be more intuitive.
- Consider implementing a gesture similar to Tinder:
  - Swiping the profile card upward should trigger a **Super Like**.

## 4. Keyword Descriptions Need Improvement

- The descriptions of the keywords are currently identical to the keyword names.
- Each keyword should have a meaningful description explaining what the keyword represents.
- More relevant and meaningful keywords could also be added to the system.

## 5. Messages Tab – Collapsible Section Arrow UI Issue

- The downward arrows on the collapsible sections in the **Messages** tab do not appear to fit properly on the screen.
- The right side of the arrow looks slightly cut off.
- The layout and spacing should be adjusted so the icons are fully visible.

## 6. Home Screen Layout Issues on Smaller Phones

- On a **Huawei P20 Lite with a 5.8-inch screen**, the cards on the Home screen do not fit properly.
- The top part of the cards appears to be cut off or incomplete.
- When swiping left or right:
  - Some tags are not visible.
  - The visible user's avatar appears slightly cut off from the top.
- This issue does not occur on tablet-sized screens.
- The responsive layout for smaller phone screens should be reviewed and fixed.

## 7. Gold Membership Exclusive Items Should Not Be Visible in the Store

- Frames and banners that are exclusive to the **Gold Membership** should not be displayed in the regular Store.
- Instead, they should be presented as **exclusive bonus items** on the Gold Membership purchase screen.
- Showing these exclusive rewards during the membership purchase flow could make the subscription more attractive.

## 8. Ignore Leading and Trailing Spaces During Login and Registration

- Leading and trailing spaces should be automatically trimmed during login and registration.
- For example, if the user's password is:

  `test1234`

  and they accidentally enter:

  `test1234 `

  the system currently treats it as an incorrect password.
- Spaces accidentally added at the beginning or end of input fields should be ignored.

## 9. Chat Does Not Automatically Open at the Latest Message

- When opening a chat with a long message history, the conversation does not automatically scroll to the latest message.
- I have to manually scroll down to see the newest messages.
- The chat should automatically open with the latest message visible.

## 10. Friend Request Issue After Withdrawing and Resending

- A friend request was initially visible.
- I withdrew the request and then sent it again, but the other user did not receive the new request.
- The other user also sent me a friend request, but I did not receive theirs either.
- The friend request state may not be updating correctly after withdrawing, resending, or sending requests between the same users.

## 11. Incorrect Message Shown to a Blocked User

- I blocked another account.
- When the blocked account tried to send me a message, they received a message saying something similar to:

  **"This account has blocked you."**

- The wording should clearly reflect the actual situation: the current user is the one who has been blocked by the other account.
- The blocked user should receive a more appropriate and clear warning message.

## 12. Online Status Sometimes Becomes Incorrect

- Both users were online, and their online status initially displayed correctly.
- Later, the status became incorrect:
  - Both users appeared offline.
  - They saw each other's last seen status instead.
  - Messages were still being delivered successfully.
- Closing and reopening the app fixed the issue.
- The cause of the online presence state becoming outdated or disconnected should be investigated.

## 13. Earn Tab Tasks Are Not Localized

- The tasks in the **Earn** tab of the Market/Store are not localized.
- The app language can be changed, but the tasks are still displayed in English.
- All task titles and descriptions should support localization.

## 14. Google Play Subscription Purchase Screen Shows Incorrect Information

- The Google Play purchase popup shows strange subscription information.
- For example:
  - Weekly membership: **$3.99 / 5 min**
  - Monthly membership: **3-minute free trial** and **$7.99 / 5 min**
  - Yearly membership: **$39.99 / 30 min**
- These values appear incorrect and may indicate a configuration issue on the **Google Play Console** side.
- The subscription products, billing periods, free trial configuration, and pricing settings should be checked in Google Play Console.