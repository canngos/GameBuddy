"""The game and keyword catalogue, and the latent taste archetypes behind it.

The archetypes are the point of this file. A synthetic population is only useful for
training if there is something real to learn from it, and that means the games a gamer
lists must be *evidence of* a hidden disposition rather than the disposition itself. Here
each archetype is a distribution over games and keywords; a gamer draws a mixture over
archetypes, and their profile is sampled from that mixture. The recommender never sees
the mixture — recovering it from the sampled profile is the entire job.

Game titles are real so the seeded database looks like something a person would recognise.
"""

from __future__ import annotations

from dataclasses import dataclass, field

# ---------------------------------------------------------------------------
# Games, grouped by the archetype that most strongly implies them.
# ---------------------------------------------------------------------------
# A game may appear under more than one archetype, and should: overlap between
# neighbouring tastes is what makes the problem non-trivial. A catalogue where every
# game belongs to exactly one archetype would be separable by inspection.

GAMES: dict[str, list[tuple[str, str]]] = {
    "competitive_fps": [
        ("Counter-Strike 2", "FPS"),
        ("VALORANT", "FPS"),
        ("Rainbow Six Siege", "FPS"),
        ("Overwatch 2", "FPS"),
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
        ("StarCraft II", "Strategy"),
        ("Teamfight Tactics", "Strategy"),
        ("Civilization VII", "Strategy"),
        ("Total War: Warhammer III", "Strategy"),
    ],
    "grand_strategy_sim": [
        ("Civilization VII", "Strategy"),
        ("Crusader Kings III", "Strategy"),
        ("Stellaris", "Strategy"),
        ("Cities: Skylines II", "Simulation"),
        ("Factorio", "Simulation"),
        ("RimWorld", "Simulation"),
        ("Football Manager 2025", "Simulation"),
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
        ("Baldur's Gate 3", "RPG"),
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
        ("Final Fantasy XIV", "MMO"),
        ("Genshin Impact", "JRPG"),
        ("Honkai: Star Rail", "JRPG"),
        ("Atelier Ryza 3", "JRPG"),
        ("Metaphor: ReFantazio", "JRPG"),
        ("Dragon Quest XI S", "JRPG"),
        ("Tales of Arise", "JRPG"),
    ],
    "horror_coop": [
        ("Phasmophobia", "Horror"),
        ("Lethal Company", "Horror"),
        ("Dead by Daylight", "Horror"),
        ("REPO", "Horror"),
        ("Resident Evil 4 Remake", "Horror"),
        ("Content Warning", "Horror"),
        ("The Forest", "Survival"),
    ],
    "survival_craft": [
        ("Minecraft", "Sandbox"),
        ("Valheim", "Survival"),
        ("Rust", "Survival"),
        ("Terraria", "Sandbox"),
        ("Palworld", "Survival"),
        ("ARK: Survival Ascended", "Survival"),
        ("Enshrouded", "Survival"),
        ("Satisfactory", "Simulation"),
    ],
    "mmo_social": [
        ("World of Warcraft", "MMO"),
        ("Final Fantasy XIV", "MMO"),
        ("Guild Wars 2", "MMO"),
        ("Old School RuneScape", "MMO"),
        ("Black Desert Online", "MMO"),
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
        ("Slay the Spire 2", "Roguelike"),
        ("Vampire Survivors", "Roguelike"),
    ],
    "retro_nostalgia": [
        ("Doom (1993)", "FPS"),
        ("Super Mario 64", "Platformer"),
        ("The Legend of Zelda: Ocarina of Time", "Adventure"),
        ("Chrono Trigger", "JRPG"),
        ("Sonic the Hedgehog 2", "Platformer"),
        ("Street Fighter II", "Fighting"),
        ("Age of Empires II", "Strategy"),
    ],
    "fighting_arcade": [
        ("Street Fighter 6", "Fighting"),
        ("Tekken 8", "Fighting"),
        ("Mortal Kombat 1", "Fighting"),
        ("Super Smash Bros. Ultimate", "Fighting"),
        ("Guilty Gear Strive", "Fighting"),
        ("Street Fighter II", "Fighting"),
    ],
}

# ---------------------------------------------------------------------------
# Keywords: how a gamer plays, rather than what they play.
# ---------------------------------------------------------------------------

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
# Archetypes
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class Archetype:
    """One latent taste profile.

    ``age_mu`` / ``age_sigma`` give each archetype its own age distribution — retro
    players skew older, battle-royale players younger. The correlation is deliberately
    mild: age should carry a little signal without being a proxy for taste, because a
    recommender that leans on it would then look good for the wrong reason.
    """

    key: str
    label: str
    age_mu: float
    age_sigma: float
    games: list[tuple[str, str]] = field(default_factory=list)
    keywords: list[str] = field(default_factory=list)


ARCHETYPES: list[Archetype] = [
    Archetype("competitive_fps", "Competitive shooter", 21.0, 4.5),
    Archetype("battle_royale", "Battle royale", 18.5, 4.0),
    Archetype("moba_strategy", "MOBA / competitive strategy", 22.0, 4.5),
    Archetype("grand_strategy_sim", "Grand strategy and simulation", 31.0, 8.0),
    Archetype("cozy_life_sim", "Cozy life sim", 26.0, 7.5),
    Archetype("story_rpg", "Story-driven RPG", 28.0, 7.0),
    Archetype("jrpg_anime", "JRPG and anime", 23.0, 5.5),
    Archetype("horror_coop", "Co-op horror", 20.0, 4.5),
    Archetype("survival_craft", "Survival and crafting", 22.0, 6.0),
    Archetype("mmo_social", "MMO social", 27.0, 7.5),
    Archetype("sports_racing", "Sports and racing", 25.0, 7.0),
    Archetype("indie_puzzle", "Indie and puzzle", 27.0, 7.0),
    Archetype("retro_nostalgia", "Retro and nostalgia", 34.0, 8.5),
    Archetype("fighting_arcade", "Fighting games", 24.0, 6.0),
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
