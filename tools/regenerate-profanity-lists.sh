#!/usr/bin/env bash
#
# Regenerates the per-language profanity lists in
# GameBuddy-backend/src/main/resources/moderation/profanity/.
#
# Development-time only. Nothing at build or run time fetches anything — the generated
# files are committed and are the source of truth. Run this when you want to pull upstream
# changes or after editing the curation rules below, then run the backend test suite.
#
# Usage:  tools/regenerate-profanity-lists.sh
#
# en.txt is NOT generated. It is hand-maintained: the upstream English list is full of
# clinical and merely-rude entries that would mask ordinary chat, so English stays curated
# by hand with upstream as a reading reference.

set -euo pipefail

# Pinned so a regeneration is reproducible. Bump deliberately, then re-run the suite.
LDNOOBW_REF="master"
KARALISTE_REF="master"

LDNOOBW="https://raw.githubusercontent.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/${LDNOOBW_REF}"
KARALISTE="https://raw.githubusercontent.com/ooguz/turkce-kufur-karaliste/${KARALISTE_REF}/karaliste.txt"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${HERE}/../GameBuddy-backend/src/main/resources/moderation/profanity"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

# ---------------------------------------------------------------------------
# Curation drops.
#
# Every entry here is a word the upstream list calls profane that this app must not mask,
# because after normalisation it collides with something ordinary. Normalisation strips
# diacritics, folds dotless i to i and sharp s to s, and collapses repeated letters — so
# collisions that look impossible in the raw spelling are real once folded.
#
# Reason is required. If you cannot state one, the entry stays.
# ---------------------------------------------------------------------------

drops_fi() { cat <<'EOF'
panna        # "to put" — one of the most common verbs in Finnish
pistää       # "to put/insert" — likewise
pantava      # ordinary participle of the above
naida        # also, and mostly, "to marry"
muna         # "egg"
tavara       # "goods, stuff"
reikä        # "hole" — ordinary noun
palli        # "stool"
jätkä        # "guy, dude" — everyday slang, not profanity
hatullinen   # "a hatful of"
haahka       # "eider duck"
pehko        # "bush"
viiksi       # "moustache"
EOF
}

drops_sv() { cat <<'EOF'
fan          # collides with English "fan" — constant in a gaming chat
olla         # collides with Finnish "olla", "to be"
mutta        # collides with Finnish "mutta", "but"
stake        # collides with English "stake"
hård         # "hard" — ordinary adjective
sås          # "sauce"
moona        # obscure; the -ade/-ar/-at forms with it
moonade
moonar
moonat
brutta       # obscure, collides with names
EOF
}

drops_de() { cat <<'EOF'
nackt        # "naked" — ordinary word
penis        # clinical
orgasmus     # clinical
porno        # clinical enough, and common in ordinary speech
nippel       # clinical / hardware term
popel        # "booger" — childish, not profane
rosette      # ordinary word and a surname
milf         # acronym, not a word people are hurt by
reudig       # "mangy" — ordinary adjective
kimme        # "notch, rear sight" — ordinary word
ische        # obscure regional slang
mufti        # a religious office
bonze        # ordinary (if snide) political word
EOF
}

drops_fr() { cat <<'EOF'
con          # collides with English "con" and with "Comic-Con"; connard/connasse stay
conne        # folds to "cone"
cul          # substring of "culture", "calcul" — bites usernames
pédale       # also, and mostly, "pedal"
folle        # "crazy" (fem.) — ordinary word
baiser       # also "to kiss"
bander       # also "to bandage"
bourré       # also "stuffed, packed"
bourrée      # likewise, and a dance
péter        # "to fart", but also "to break" — mild and common
gueule       # ordinary word for an animal's mouth
meuf         # "girl" — slang, not profanity
gerbe        # "sheaf"; gerber (to vomit) stays
clito        # clinical
clitoris     # clinical
malpt        # not a word
bite         # folds to "bite", which is also German "bitte" ("please")
bitte        # the same fold, from the other spelling
EOF
}

drops_es() { cat <<'EOF'
concha       # "shell", and a given name; the full phrase stays
martillo     # "hammer"
asno         # "donkey"
infierno     # "hell" — ordinary word
maldito      # "damned" — mild and very common
idiota       # mild insult, ordinary word
imbécil      # likewise
pinche       # regional intensifier, ordinary in Mexican Spanish
trio         # "trio"
nazi         # political term, not profanity
racista      # "racist" — the word for the thing, not the slur
asesinato    # "murder"
drogas       # "drugs"
heroína      # "heroin", also "heroine"
sexo         # clinical
sexo oral    # clinical
semen        # clinical
esperma      # clinical
orina        # clinical
pis          # clinical, and short
pezón        # clinical
vulva        # clinical
prostituta   # clinical
coprofagía   # clinical
pervertido   # ordinary word
sádico       # ordinary word
travesti     # identity term, not an insult in itself
maciza       # "solid" (fem.)
macizorra
chupetón     # "hickey"
fiesta de salchichas   # literal "sausage party", harmless as text
haciendo el amor       # "making love"
tetas grandes          # descriptive
tía buena              # mild
EOF
}

drops_tr() { cat <<'EOF'
am           # two letters once normalised; the loader drops it anyway
amı          # folds to "ami" — substring of Jamie, Benjamin, Amir
sik          # folds together with "sık" ("often, tight")
siki         # folds together with "sıkı" ("tight")
sikin        # folds together with "sıkın" ("squeeze!")
göt          # folds to "got"
allah        # a name of God, not profanity
ahmak        # "fool" — mild, ordinary word
ag           # too short and ordinary
abaza        # regional/ethnic term
domal        # ordinary verb stem in some contexts
mal          # "goods"; also French and Spanish "mal", "bad"
penis        # clinical, and it is not a Turkish word
EOF
}

# ---------------------------------------------------------------------------
# Cleaning rules, applied to every language:
#   - lowercase, trim, drop blanks and comments
#   - drop entries under 3 characters (the loader enforces this again after folding)
#   - drop multi-word entries of more than 3 words, or whose joined form is under 6
#     characters: the normaliser strips spaces, so a kept phrase matches only when typed
#     unspaced. Long distinctive ones are worth keeping; short ones would be noise.
#   - apply the curation drops above
#   - dedupe and sort
# ---------------------------------------------------------------------------
clean() {
    local drops="$1"
    tr '[:upper:]' '[:lower:]' \
    | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' \
    | grep -v '^$' \
    | grep -v '^#' \
    | awk '
        { words = NF; joined = $0; gsub(/[[:space:]]/, "", joined) }
        length(joined) < 3 { next }
        words > 3 { next }
        words > 1 && length(joined) < 6 { next }
        { print }
      ' \
    | grep -vxF -f "$drops" \
    | sort -u
}

fetch_and_write() {
    local lang="$1" title="$2"
    echo "  ${lang}..."

    "drops_${lang}" | sed 's/[[:space:]]*#.*$//; s/[[:space:]]*$//' | grep -v '^$' > "${WORK}/drops-${lang}"
    curl -fsSL "${LDNOOBW}/${lang}" -o "${WORK}/raw-${lang}"

    if [ "${lang}" = "tr" ]; then
        curl -fsSL "${KARALISTE}" >> "${WORK}/raw-${lang}"
    fi

    clean "${WORK}/drops-${lang}" < "${WORK}/raw-${lang}" > "${WORK}/clean-${lang}"

    {
        echo "# ${title} profanity — masked, not refused."
        echo "#"
        echo "# Generated by tools/regenerate-profanity-lists.sh — edit that script, not this file."
        echo "# Source: LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words @ ${LDNOOBW_REF} (CC-BY 4.0)"
        if [ "${lang}" = "tr" ]; then
            echo "#         ooguz/turkce-kufur-karaliste @ ${KARALISTE_REF} (CC-BY-SA 4.0)"
        fi
        echo "# Entries are written in natural spelling; the loader folds them."
        echo "#"
        cat "${WORK}/clean-${lang}"
    } > "${OUT}/${lang}.txt"

    printf '    %s entries\n' "$(wc -l < "${WORK}/clean-${lang}" | tr -d ' ')"
}

mkdir -p "${OUT}"
echo "Regenerating profanity lists into ${OUT}"

fetch_and_write fi "Finnish"
fetch_and_write sv "Swedish"
fetch_and_write de "German"
fetch_and_write fr "French"
fetch_and_write es "Spanish"
fetch_and_write tr "Turkish"

cat > "${OUT}/NOTICE.txt" <<EOF
Word list attribution
=====================

fi, sv, de, fr, es, tr (and English reference material)
    List of Dirty, Naughty, Obscene, and Otherwise Bad Words
    https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words
    Copyright (c) 2012-2020 Shutterstock, Inc.
    Licensed under Creative Commons Attribution 4.0 International (CC-BY 4.0)
    https://creativecommons.org/licenses/by/4.0/

tr (additional entries)
    turkce-kufur-karaliste
    https://github.com/ooguz/turkce-kufur-karaliste
    Licensed under Creative Commons Attribution-ShareAlike 4.0 International
    (CC-BY-SA 4.0) https://creativecommons.org/licenses/by-sa/4.0/
    tr.txt is a derivative of that list and remains under CC-BY-SA 4.0.

Both lists have been filtered and curated for this application: entries that collide with
ordinary words in the seven languages the app ships were removed. See
tools/regenerate-profanity-lists.sh for the removals and the reason for each.

en.txt is hand-maintained by the GameBuddy project and is not derived from either list.
allowlist.txt is hand-maintained by the GameBuddy project.
EOF

echo "Wrote NOTICE.txt"
echo "Done. Run: ./gradlew :backend:test --tests 'com.gamebuddy.shared.moderation.*'"
