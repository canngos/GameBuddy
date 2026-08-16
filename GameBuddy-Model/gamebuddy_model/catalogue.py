"""The game and keyword catalogue, and the latent taste archetypes behind it.

The archetypes are the point of this file. A synthetic population is only useful for
training if there is something real to learn from it, and that means the games a gamer
lists must be *evidence of* a hidden disposition rather than the disposition itself. Here
each archetype is a distribution over games and keywords; a gamer draws a mixture over
archetypes, and their profile is sampled from that mixture. The recommender never sees
the mixture — recovering it from the sampled profile is the entire job.

Two things below carry the structure that makes the population *organic* rather than
merely random:

**``WARMTH``** says which archetypes get along. Without it, affinity is the plain cosine
of two mixture vectors, which knows nothing about what the archetypes mean — so the only
way to be compatible is to be nearly the same, and whatever cross-genre matching survives
is an artifact of Dirichlet noise rather than taste. Measured on the population that
produced, battle-royale and competitive-FPS players were the *coldest* pair in the whole
match graph despite sharing Apex Legends and Warzone in the catalogue below. That is the
content signal and the label disagreeing about what related taste is, and it caps how
well any model can do.

**``VIBE_KEYWORDS``** separates *how* someone plays from *what* they play. Keywords used
to be sampled from the same mixture as games, which made them a second noisy copy of the
genre signal rather than an axis of their own. Splitting them out is what lets a chill,
no-mic Counter-Strike player be genuinely closer to a chill Stardew player than to the
tryhard ranked grinder who plays the same shooter.

Game titles are real so the seeded database looks like something a person would recognise.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

# ---------------------------------------------------------------------------
# Games, grouped by the archetype that most strongly implies them.
# ---------------------------------------------------------------------------
# A game may appear under more than one archetype, and should: overlap between
# neighbouring tastes is what makes the problem non-trivial. A catalogue where every
# game belongs to exactly one archetype would be separable by inspection.

GAMES: dict[str, list[tuple[str, str]]] = {
    "competitive_fps": [
        ("Counter-Strike 2", "FPS"),
        ("Valorant", "FPS"),
        ("Rainbow Six Siege", "FPS"),
        ("Overwatch", "FPS"),
        ("Apex Legends", "FPS"),
        ("Battlefield 6", "FPS"),
        ("Call of Duty: Warzone", "FPS"),
        ("The Finals", "FPS"),
        ("Escape from Tarkov", "FPS"),
        ("ARC Raiders", "FPS"),
    ],
    "battle_royale": [
        ("PUBG: Battlegrounds", "Battle Royale"),
        ("Fortnite", "Battle Royale"),
        ("Apex Legends", "FPS"),
        ("Call of Duty: Warzone", "FPS"),
        ("Naraka: Bladepoint", "Battle Royale"),
        ("Fall Guys", "Party"),
    ],
    "moba_strategy": [
        ("League of Legends", "MOBA"),
        ("Dota 2", "MOBA"),
        ("Smite 2", "MOBA"),
        ("Age of Empires IV", "Strategy"),
        ("StarCraft II: Wings of Liberty", "Strategy"),
        ("Teamfight Tactics", "Strategy"),
        ("Sid Meier's Civilization VII", "Strategy"),
        ("Total War: Warhammer III", "Strategy"),
    ],
    "grand_strategy_sim": [
        ("Sid Meier's Civilization VII", "Strategy"),
        ("Crusader Kings III", "Strategy"),
        ("Stellaris", "Strategy"),
        ("Cities: Skylines II", "Simulation"),
        ("Factorio", "Simulation"),
        ("RimWorld", "Simulation"),
        ("Football Manager 25", "Simulation"),
        ("Frostpunk 2", "Strategy"),
    ],
    "cozy_life_sim": [
        ("Stardew Valley", "Simulation"),
        ("Animal Crossing: New Horizons", "Simulation"),
        ("The Sims 4", "Simulation"),
        ("Tiny Glade", "Simulation"),
        ("Coral Island", "Simulation"),
        ("Dave the Diver", "Adventure"),
        ("Palia", "Simulation"),
        ("Graveyard Keeper", "Simulation"),
    ],
    "story_rpg": [
        ("Baldur's Gate III", "RPG"),
        ("The Witcher 3: Wild Hunt", "RPG"),
        ("Cyberpunk 2077", "RPG"),
        ("Elden Ring", "RPG"),
        ("Mass Effect Legendary Edition", "RPG"),
        ("Disco Elysium", "RPG"),
        ("Red Dead Redemption 2", "Adventure"),
        ("Clair Obscur: Expedition 33", "RPG"),
    ],
    "jrpg_anime": [
        ("Persona 5 Royal", "JRPG"),
        ("Final Fantasy XIV Online", "MMO"),
        ("Genshin Impact", "JRPG"),
        ("Honkai: Star Rail", "JRPG"),
        ("Atelier Ryza 3: Alchemist of the End & the Secret Key", "JRPG"),
        ("Metaphor: ReFantazio", "JRPG"),
        ("Dragon Quest XI S", "JRPG"),
        ("Tales of Arise", "JRPG"),
    ],
    "horror_coop": [
        ("Phasmophobia", "Horror"),
        ("Lethal Company", "Horror"),
        ("Dead by Daylight", "Horror"),
        ("R.E.P.O.", "Horror"),
        ("Resident Evil 4", "Horror"),
        ("Content Warning", "Horror"),
        ("The Forest", "Survival"),
    ],
    "survival_craft": [
        ("Minecraft: Java Edition", "Sandbox"),
        ("Valheim", "Survival"),
        ("Rust", "Survival"),
        ("Terraria", "Sandbox"),
        ("Palworld", "Survival"),
        ("Ark: Survival Ascended", "Survival"),
        ("Enshrouded", "Survival"),
        ("Satisfactory", "Simulation"),
    ],
    "mmo_social": [
        ("World of Warcraft", "MMO"),
        ("Final Fantasy XIV Online", "MMO"),
        ("Guild Wars 2", "MMO"),
        ("Old School RuneScape", "MMO"),
        ("Black Desert", "MMO"),
        ("Throne and Liberty", "MMO"),
    ],
    "sports_racing": [
        ("EA Sports FC 26", "Sports"),
        ("NBA 2K26", "Sports"),
        ("Rocket League", "Sports"),
        ("F1 25", "Racing"),
        ("Forza Horizon 5", "Racing"),
        ("Gran Turismo 7", "Racing"),
        ("eFootball 2026", "Sports"),
    ],
    "indie_puzzle": [
        ("Hollow Knight: Silksong", "Metroidvania"),
        ("Hades II", "Roguelike"),
        ("Balatro", "Roguelike"),
        ("Celeste", "Platformer"),
        ("Outer Wilds", "Adventure"),
        ("Baba Is You", "Puzzle"),
        ("Slay the Spire II", "Roguelike"),
        ("Vampire Survivors", "Roguelike"),
    ],
    "retro_nostalgia": [
        ("Doom", "FPS"),
        ("Super Mario 64", "Platformer"),
        ("The Legend of Zelda: Ocarina of Time", "Adventure"),
        ("Chrono Trigger", "JRPG"),
        ("Sonic the Hedgehog 2", "Platformer"),
        ("Street Fighter II", "Fighting"),
        ("Age of Empires II: The Age of Kings", "Strategy"),
    ],
    "fighting_arcade": [
        ("Street Fighter 6", "Fighting"),
        ("Tekken 8", "Fighting"),
        ("Mortal Kombat 1", "Fighting"),
        ("Super Smash Bros. Ultimate", "Fighting"),
        ("Guilty Gear: Strive", "Fighting"),
        ("Street Fighter II", "Fighting"),
    ],
}

# ---------------------------------------------------------------------------
# Keywords: how a gamer plays, rather than what they play.
# ---------------------------------------------------------------------------
# Split into two kinds, because they behave differently and mixing them cost the model
# its second signal.
#
# *Genre* keywords are implied by what you play: a ``waifu collector`` is playing gacha
# JRPGs, an ``emulator user`` is playing retro. These are sampled from the archetype
# mixture, exactly as before.
#
# *Vibe* keywords are not. ``chill``/``tryhard``, ``voice chat``/``no mic``,
# ``long sessions``/``short sessions`` describe temperament, and temperament varies
# *within* a genre — the tryhard and the chill player queue into the same shooter. These
# are sampled from a separate latent vector (see ``VIBE_AXES``) that the archetype only
# partly predicts.
#
# The lists below stay as written: each archetype keeps its curated keywords, and the
# split is applied by ``VIBE_KEYWORDS`` membership. So a keyword can be argued about in
# one place rather than two.

KEYWORDS: dict[str, list[str]] = {
    "competitive_fps": ["competitive", "ranked grinder", "aim training", "tryhard", "clutch", "esports"],
    "battle_royale": ["competitive", "squad player", "clutch", "casual", "loot goblin"],
    "moba_strategy": ["competitive", "ranked grinder", "shotcaller", "theorycrafter", "toxic-free", "esports"],
    "grand_strategy_sim": ["min-maxer", "theorycrafter", "solo player", "long sessions", "modder", "completionist"],
    "cozy_life_sim": ["chill", "casual", "collector", "decorator", "no mic", "short sessions"],
    "story_rpg": ["story-driven", "completionist", "solo player", "lore nerd", "roleplayer", "no spoilers"],
    "jrpg_anime": ["story-driven", "anime fan", "collector", "completionist", "lore nerd", "waifu collector"],
    "horror_coop": ["co-op", "voice chat", "jumpscare enjoyer", "casual", "streamer", "chaotic"],
    "survival_craft": ["builder", "co-op", "grinder", "modder", "long sessions", "explorer"],
    "mmo_social": ["guild leader", "raider", "grinder", "social", "roleplayer", "endgame focused"],
    "sports_racing": ["competitive", "casual", "couch co-op", "sim racer", "ranked grinder"],
    "indie_puzzle": ["completionist", "speedrunner", "solo player", "chill", "achievement hunter"],
    "retro_nostalgia": ["nostalgic", "speedrunner", "collector", "couch co-op", "emulator user"],
    "fighting_arcade": ["competitive", "combo practice", "couch co-op", "tournament goer", "clutch"],
}

# ---------------------------------------------------------------------------
# The vibe axes
# ---------------------------------------------------------------------------
# Three continuous traits, standardised, that describe temperament rather than taste.
# They are the axis a genre-only model cannot see, and the reason two people who play
# nothing in common can still be worth introducing.

VIBE_AXES: tuple[str, str, str] = ("competitiveness", "sociability", "commitment")

#: Where each vibe keyword sits on those three axes. A gamer's chance of listing a
#: keyword rises with the dot product of their vibe vector against these loadings, so
#: someone high on competitiveness tends to say ``tryhard`` and tends not to say
#: ``chill`` — tends to, not always, which is the whole point of sampling.
VIBE_KEYWORDS: dict[str, tuple[float, float, float]] = {
    # competitiveness
    "tryhard": (1.0, 0.0, 0.3),
    "competitive": (0.9, 0.0, 0.2),
    "ranked grinder": (0.8, 0.0, 0.7),
    "esports": (0.9, 0.3, 0.2),
    "tournament goer": (0.9, 0.4, 0.3),
    "aim training": (0.8, -0.2, 0.5),
    "min-maxer": (0.7, -0.2, 0.6),
    "clutch": (0.7, 0.2, 0.0),
    "toxic-free": (-0.4, 0.4, 0.0),
    "casual": (-0.8, 0.0, -0.6),
    "chill": (-0.9, 0.1, -0.3),
    # sociability
    "social": (0.0, 1.0, 0.2),
    "voice chat": (0.0, 0.9, 0.0),
    "squad player": (0.3, 0.8, 0.0),
    "co-op": (-0.2, 0.8, 0.1),
    "couch co-op": (-0.3, 0.7, -0.2),
    "streamer": (0.2, 0.7, 0.3),
    "no mic": (0.0, -0.9, 0.0),
    "solo player": (0.0, -1.0, 0.0),
    # commitment
    "grinder": (0.3, 0.0, 0.9),
    "long sessions": (0.0, 0.0, 0.9),
    "endgame focused": (0.4, 0.2, 0.8),
    "completionist": (0.2, -0.2, 0.8),
    "achievement hunter": (0.3, -0.1, 0.7),
    "short sessions": (0.0, 0.0, -0.9),
}

# ---------------------------------------------------------------------------
# Archetypes
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class Archetype:
    """One latent taste profile.

    ``age_mu`` / ``age_sigma`` give each archetype its own age distribution — retro
    players skew older, battle-royale players younger. The correlation is deliberately
    mild: age should carry a little signal without being a proxy for taste, because a
    recommender that leans on it would then look good for the wrong reason. The means sit
    clear of the 18 floor the product enforces, so clamping does not pile a whole
    archetype onto a single age.

    ``vibe`` is this archetype's prior on the three ``VIBE_AXES``. It is a *prior*, not an
    assignment: a gamer's own vibe is drawn around it with enough spread that the chill
    shooter player and the tryhard cozy-sim player both exist, because they do.

    ``platforms`` is where this taste is played, as weights over ``PLATFORM_IDS``. It is
    generated for realism and filtering only — the backend hard-filters on platform in
    ``FeedFilters.matches()``, so putting it in the taste vector would double-count it.
    """

    key: str
    label: str
    age_mu: float
    age_sigma: float
    vibe: tuple[float, float, float]
    platforms: tuple[float, float, float, float, float]
    games: list[tuple[str, str]] = field(default_factory=list)
    keywords: list[str] = field(default_factory=list)


#: Order matters: it is the column order of ``Archetype.platforms`` and matches
#: ``common/.../enums/Platform.java`` and ``GameBuddy-App/src/profile/platforms.ts``.
PLATFORM_IDS: tuple[str, ...] = ("PC", "PLAYSTATION", "XBOX", "SWITCH", "MOBILE")

ARCHETYPES: list[Archetype] = [
    #             key                   label                            age_mu  sigma   vibe (comp, social, commit)   platforms (PC,  PS,   XB,   SW,   MOB)
    Archetype("competitive_fps",    "Competitive shooter",               22.5,   4.5,   (1.1, 0.3, 0.4),              (0.55, 0.18, 0.18, 0.02, 0.07)),
    Archetype("battle_royale",      "Battle royale",                     21.0,   4.0,   (0.5, 0.7, -0.2),             (0.40, 0.22, 0.18, 0.05, 0.15)),
    Archetype("moba_strategy",      "MOBA / competitive strategy",       23.0,   4.5,   (1.0, 0.2, 0.6),              (0.72, 0.06, 0.05, 0.02, 0.15)),
    Archetype("grand_strategy_sim", "Grand strategy and simulation",     31.0,   8.0,   (0.2, -0.8, 1.1),             (0.85, 0.05, 0.05, 0.03, 0.02)),
    Archetype("cozy_life_sim",      "Cozy life sim",                     26.5,   7.5,   (-1.1, -0.3, -0.6),           (0.35, 0.12, 0.08, 0.35, 0.10)),
    Archetype("story_rpg",          "Story-driven RPG",                  28.0,   7.0,   (-0.4, -0.9, 0.5),            (0.45, 0.28, 0.18, 0.06, 0.03)),
    Archetype("jrpg_anime",         "JRPG and anime",                    24.0,   5.5,   (-0.3, -0.5, 0.6),            (0.35, 0.25, 0.05, 0.15, 0.20)),
    Archetype("horror_coop",        "Co-op horror",                      21.5,   4.5,   (-0.3, 1.0, -0.5),            (0.65, 0.15, 0.12, 0.04, 0.04)),
    Archetype("survival_craft",     "Survival and crafting",             23.0,   6.0,   (0.0, 0.6, 0.9),              (0.60, 0.15, 0.13, 0.07, 0.05)),
    Archetype("mmo_social",         "MMO social",                        27.0,   7.5,   (0.3, 1.0, 1.0),              (0.75, 0.10, 0.06, 0.02, 0.07)),
    Archetype("sports_racing",      "Sports and racing",                 25.5,   7.0,   (0.6, 0.4, -0.3),             (0.25, 0.35, 0.28, 0.05, 0.07)),
    Archetype("indie_puzzle",       "Indie and puzzle",                  27.0,   7.0,   (-0.3, -0.9, -0.2),           (0.50, 0.13, 0.08, 0.24, 0.05)),
    Archetype("retro_nostalgia",    "Retro and nostalgia",               34.0,   8.5,   (-0.4, -0.2, -0.4),           (0.45, 0.12, 0.08, 0.30, 0.05)),
    Archetype("fighting_arcade",    "Fighting games",                    25.0,   6.0,   (0.9, 0.5, 0.1),              (0.35, 0.38, 0.15, 0.10, 0.02)),
]

for _a in ARCHETYPES:
    object.__setattr__(_a, "games", GAMES[_a.key])
    object.__setattr__(_a, "keywords", KEYWORDS[_a.key])

ARCHETYPE_INDEX = {a.key: i for i, a in enumerate(ARCHETYPES)}

# ---------------------------------------------------------------------------
# Flat catalogues
# ---------------------------------------------------------------------------

ALL_GAMES: list[tuple[str, str]] = sorted({g for a in ARCHETYPES for g in a.games})
ALL_KEYWORDS: list[str] = sorted({k for a in ARCHETYPES for k in a.keywords})

#: The keywords driven by taste rather than temperament — everything that is not a vibe
#: keyword. Derived rather than listed, so the two sets cannot drift apart.
GENRE_KEYWORDS: list[str] = [k for k in ALL_KEYWORDS if k not in VIBE_KEYWORDS]

_unknown_vibe_keywords = set(VIBE_KEYWORDS) - set(ALL_KEYWORDS)
if _unknown_vibe_keywords:
    raise ValueError(
        "VIBE_KEYWORDS contains keywords no archetype lists, so they would never be "
        f"seeded into the database: {sorted(_unknown_vibe_keywords)}"
    )

# ---------------------------------------------------------------------------
# Which archetypes get along
# ---------------------------------------------------------------------------
# Only the pairs that are warmer than the floor are listed; everything unlisted sits at
# BACKGROUND_WARMTH, and nothing is at zero. That floor is deliberate and is the thing
# that makes the population organic rather than tidy: a competitive shooter player and a
# football-game player are not each other's best match, but they are not forbidden from
# getting along either, and at a floor of 0.15 a reasonable number of them do.
#
# Symmetric by construction — warmth is a property of the pair.

# Swept, not guessed. The floor sets how far apart the coldest pair of archetypes can be,
# and because a Dirichlet mixture is not one-hot it lifts *every* pair, not just the
# unlisted ones. At 0.15 the whole taste distribution compressed into 0.56–0.87 and warm
# and cold cross-genre pairs sat 0.6 standard deviations apart, which is not enough
# structure to learn. At 0.08 they are 0.9 apart while the coldest pairs still score ~0.5,
# so cross-genre matching stays possible rather than becoming a rounding error.
BACKGROUND_WARMTH = 0.08

WARM_PAIRS: dict[tuple[str, str], float] = {
    # The competitive cluster. These people recognise each other.
    ("competitive_fps", "battle_royale"): 0.75,
    ("competitive_fps", "moba_strategy"): 0.55,
    ("competitive_fps", "fighting_arcade"): 0.45,
    ("competitive_fps", "sports_racing"): 0.40,
    ("competitive_fps", "survival_craft"): 0.30,
    ("battle_royale", "sports_racing"): 0.35,
    ("battle_royale", "horror_coop"): 0.40,
    ("battle_royale", "survival_craft"): 0.35,
    ("moba_strategy", "grand_strategy_sim"): 0.50,
    ("moba_strategy", "mmo_social"): 0.40,
    ("moba_strategy", "fighting_arcade"): 0.35,
    # The systems cluster: people who like a spreadsheet under the game.
    ("grand_strategy_sim", "survival_craft"): 0.40,
    ("grand_strategy_sim", "indie_puzzle"): 0.35,
    ("grand_strategy_sim", "mmo_social"): 0.30,
    ("grand_strategy_sim", "sports_racing"): 0.30,  # Football Manager is both
    # The cosy cluster.
    ("cozy_life_sim", "indie_puzzle"): 0.50,
    ("cozy_life_sim", "jrpg_anime"): 0.45,
    ("cozy_life_sim", "survival_craft"): 0.35,
    ("cozy_life_sim", "retro_nostalgia"): 0.35,
    # The narrative cluster.
    ("story_rpg", "jrpg_anime"): 0.55,
    ("story_rpg", "indie_puzzle"): 0.40,
    ("story_rpg", "mmo_social"): 0.30,
    ("story_rpg", "horror_coop"): 0.30,
    ("jrpg_anime", "fighting_arcade"): 0.40,  # anime fighters are the bridge
    ("jrpg_anime", "mmo_social"): 0.35,
    ("jrpg_anime", "indie_puzzle"): 0.30,
    # Co-op and crafting.
    ("horror_coop", "survival_craft"): 0.45,
    ("horror_coop", "mmo_social"): 0.30,
    ("survival_craft", "mmo_social"): 0.35,
    # Retro sits next to the arcade and the indie shelf.
    ("retro_nostalgia", "fighting_arcade"): 0.55,
    ("retro_nostalgia", "indie_puzzle"): 0.40,
    ("retro_nostalgia", "sports_racing"): 0.25,
    ("retro_nostalgia", "story_rpg"): 0.25,
}


def _build_warmth() -> np.ndarray:
    """Assembles ``WARM_PAIRS`` into a usable affinity kernel.

    Two properties have to hold for the result to be a sensible similarity.

    It must be **symmetric**, which is free — warmth is a property of the pair, and the
    table is read both ways round.

    It must be **positive semi-definite**, which is not free. A hand-written table is just
    a set of opinions and nothing stops those opinions from being geometrically
    impossible: "A is close to B, B is close to C, A is far from C" can be written down
    but cannot be embedded, and a kernel with a negative eigenvalue lets a gamer be less
    similar to themselves than to someone else, which then shows up as a mixture whose
    self-affinity is negative and a cosine outside [-1, 1]. So the matrix is projected
    onto the PSD cone by clipping negative eigenvalues to zero, and the diagonal is
    rescaled back to 1 afterwards so "an archetype with itself" still means full warmth.
    The projection moves the hand-written numbers slightly; that is the cost of them being
    consistent, and the test suite pins how far they are allowed to move.
    """
    n = len(ARCHETYPES)
    warmth = np.full((n, n), BACKGROUND_WARMTH)
    np.fill_diagonal(warmth, 1.0)

    for (left, right), value in WARM_PAIRS.items():
        i, j = ARCHETYPE_INDEX[left], ARCHETYPE_INDEX[right]
        warmth[i, j] = warmth[j, i] = value

    eigenvalues, eigenvectors = np.linalg.eigh(warmth)
    if eigenvalues.min() < 0:
        warmth = (eigenvectors * np.clip(eigenvalues, 0.0, None)) @ eigenvectors.T
        # Rescale to unit diagonal: cos(a, a) must stay 1 or the self-affinity of a pure
        # archetype drifts and the like threshold stops meaning the same thing.
        scale = np.sqrt(np.diag(warmth))
        warmth = warmth / np.outer(scale, scale)
    return warmth


#: ``WARMTH[i, j]`` is how well archetype *i* gets along with archetype *j*.
WARMTH: np.ndarray = _build_warmth()

# ---------------------------------------------------------------------------
# Countries
# ---------------------------------------------------------------------------
# Deliberately drawn independently of taste. Country is a real field the product
# collects, but it carries no information about who someone will get along with, and
# building the population that way lets the evaluation demonstrate it: a model that
# leans on country scores worse, rather than us having to argue about it.

COUNTRIES: list[tuple[str, float]] = [
    ("Turkey", 0.28),
    ("Germany", 0.10),
    ("United States", 0.13),
    ("United Kingdom", 0.06),
    ("France", 0.05),
    ("Netherlands", 0.04),
    ("Poland", 0.05),
    ("Spain", 0.04),
    ("Brazil", 0.06),
    ("Sweden", 0.03),
    ("Italy", 0.04),
    ("Canada", 0.04),
    ("Australia", 0.03),
    ("Japan", 0.03),
    ("South Korea", 0.02),
]
