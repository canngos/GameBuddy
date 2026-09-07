package com.gamebuddy.common.enums;

/**
 * An external identity somebody can sign in to GameBuddy with.
 *
 * <p><b>Deliberately not {@link LinkedProvider}.</b> The two enums name the same companies
 * and mean entirely different things, and merging them would be the kind of tidiness that
 * costs an account.
 *
 * <ul>
 *   <li>A {@code LinkedProvider} is a <em>profile</em> fact: a handle other people can see,
 *       with a visibility setting, freely unlinkable from the settings screen because
 *       removing it costs nothing but a badge.
 *   <li>An {@code AuthProvider} is a <em>credential</em>. It is never displayed, and removing
 *       the last one when no password is set would lock somebody out of their own account.
 * </ul>
 *
 * <p>The same Discord snowflake can legitimately appear under both, and that is not
 * duplication: one says "this is where to find me", the other says "this is how I get in".
 *
 * <p>Apple belongs here eventually — the App Store requires it once any other social sign-in
 * exists — which is the other reason this is its own type. Steam never will: it is an OpenID
 * badge, not a way in.
 */
public enum AuthProvider {
    GOOGLE,
    DISCORD;

    /**
     * Parses a provider name from a path variable, case-insensitively.
     *
     * @return the provider, or null if the name is not one. Callers decide what an unknown
     *     value means, exactly as {@link LinkedProvider#from(String)} leaves it to them.
     */
    public static AuthProvider from(String name) {
        if (name == null) {
            return null;
        }
        for (AuthProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(name.trim())) {
                return provider;
            }
        }
        return null;
    }
}
