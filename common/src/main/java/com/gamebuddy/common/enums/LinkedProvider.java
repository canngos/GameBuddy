package com.gamebuddy.common.enums;

/**
 * An external gaming account a gamer can prove they own.
 *
 * <p>One, for now. The point of the profile is to answer "can I play with this person"; the
 * point of this is to answer "and where do I find them", which every other part of the app
 * deliberately leaves to chat. A Discord handle is <em>contactable</em> — it is enough to
 * start a conversation with somebody, which is what makes it worth verifying and worth
 * defaulting to the private visibility.
 *
 * <p>Steam was here too and was removed in September 2026: its Web API key could not be
 * obtained, and a provider that can only ever fail is worse than one that is absent. This
 * stays an enum with one constant, and the column behind it stays {@code varchar(16)}, so
 * bringing Steam back — or adding anything else — is a deploy rather than a schema change.
 */
public enum LinkedProvider {
    DISCORD;

    // No display label here, unlike Platform. The client owns the wording, because it also
    // owns the brand marks these sit beside — see `PROVIDER_LABELS` in
    // src/profile/ProviderMark.tsx. A second copy on this side would be one nobody reads
    // until the day it disagrees.

    /**
     * Parses a provider name from a path variable, case-insensitively.
     *
     * @return the provider, or null if the name is not one. Callers decide what an unknown
     *     value means, exactly as {@link Platform#from(String)} leaves it to them.
     */
    public static LinkedProvider from(String name) {
        if (name == null) {
            return null;
        }
        for (LinkedProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(name.trim())) {
                return provider;
            }
        }
        return null;
    }
}
