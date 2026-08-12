package com.gamebuddy.common.enums;

/**
 * What a gamer plays on.
 *
 * <p>Five families rather than individual consoles, and that is the whole design. Nobody
 * looking for someone to play with cares whether the other person is on a PS4 or a PS5 —
 * they care whether they can join the same lobby, and the lobby is drawn around the family.
 * Splitting generations would double the list and halve every match.
 *
 * <p><b>A gamer holds a set of these, not one.</b> Most people play on more than one thing,
 * and a single-choice field would make "PC and Switch" unrepresentable — which does not
 * merely lose information, it makes the platform filter lie: it would hide somebody from a
 * search they genuinely belong in. The cost is that filtering becomes an intersection test
 * rather than an equality one, which is a few more characters of SQL.
 *
 * <p>Order matters and is deliberate. It is the order the picker renders in, roughly by how
 * many players each has, so the common answers are reachable without scrolling.
 */
public enum Platform {
    PC("PC"),
    PLAYSTATION("PlayStation"),
    XBOX("Xbox"),
    SWITCH("Nintendo Switch"),
    MOBILE("Mobile");

    private final String label;

    Platform(String label) {
        this.label = label;
    }

    /**
     * How it is written for a human.
     *
     * <p>Sent to the client rather than left for it to derive, so that "PLAYSTATION"
     * becoming "Playstation" in one screen and "PlayStation" in another is not possible.
     * The names are trademarks and are spelled the way their owners spell them.
     */
    public String label() {
        return label;
    }

    /**
     * Parses a stored or submitted name, case-insensitively.
     *
     * @return the platform, or null if the name is not one — callers decide whether an
     *     unknown value is an error or something to ignore, because those differ: a bad
     *     value in a request is a 400, and a bad value in a row is history to be tolerated.
     */
    public static Platform from(String name) {
        if (name == null) {
            return null;
        }
        for (Platform platform : values()) {
            if (platform.name().equalsIgnoreCase(name.trim())) {
                return platform;
            }
        }
        return null;
    }
}
