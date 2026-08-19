import type { Dictionary } from './index';

/**
 * French.
 *
 * `tu` rather than `vous`. Same reasoning as the German file: `vous` is the safe commercial
 * default and would make a product for gamers sound like an insurance company. French gaming
 * media and communities use `tu` throughout.
 *
 * Anglicisms kept where they are what people actually say — *match*, *co-op*, *raid* — rather
 * than replaced with the Académie's preferred forms, which nobody in this audience uses.
 */
export const fr: Dictionary = {
  meta: { htmlLang: 'fr', ogLocale: 'fr_FR' },

  nav: {
    features: 'Fonctionnalités',
    how: 'Comment ça marche',
    support: 'Aide',
    toggleTheme: 'Basculer entre clair et sombre',
    language: 'Langue',
  },

  hero: {
    badge: "D'abord sur Android",
    titleBefore: 'Trouve ceux qui',
    titleHighlight: 'jouent vraiment',
    titleAfter: 'à ce que tu joues',
    subtitle:
      "Pas un serveur rempli d'inconnus. GameBuddy te met en relation selon tes jeux, ta plateforme et ta façon de jouer — puis s'efface pour que tu puisses jouer.",
    comingSoon: 'Bientôt sur',
    releaseNote: 'GameBuddy est en phase finale avant la sortie. Android arrive en premier.',
    deckAlt: "Le deck GameBuddy, montrant le profil d'un joueur et les jeux auxquels il joue",
    chatAlt: 'La liste des conversations GameBuddy, avec un nouveau match en attente',
  },

  features: {
    title: "Ce qui fait un vrai match",
    intro:
      "Associer des gens sur un intérêt commun, c'est facile. Associer des gens qui peuvent réellement jouer ensemble, c'est le vrai sujet — et c'est à ça que sert tout ce qui suit.",
    matched: {
      title: 'Un match basé sur ce que tu joues',
      body: "GameBuddy apprend des jeux et des styles de jeu de ton profil, pas d'une liste de tags que tu fais défiler. Les gens qu'il te montre ont les mêmes jeux et y jouent de la même manière.",
    },
    platform: {
      title: 'La plateforme compte',
      body: "PC, PlayStation, Xbox, Switch ou mobile. Deux personnes avec la même bibliothèque sur des machines différentes ne peuvent pas jouer ensemble : le match en tient compte.",
    },
    chat: {
      title: 'Un chat qui reste privé',
      body: "Le contenu des messages est chiffré au repos — la base de données stocke du texte chiffré, pas ta conversation. L'indicateur de saisie et les accusés de lecture fonctionnent comme prévu.",
    },
    moderated: {
      title: 'Modéré, et réservé aux adultes',
      body: "Chaque photo envoyée est contrôlée avant que quiconque la voie. Les signalements sont traités sous 24 heures. Il faut avoir 18 ans ou plus, vérifié à partir d'une date de naissance.",
    },
    badges: {
      title: 'Des badges qui valent quelque chose',
      body: "Des missions et des badges gagnés en utilisant vraiment l'appli, affichés sur ton profil. Aucun palier qui s'achète.",
    },
    free: {
      title: 'Gratuit, pour de vrai',
      body: "Le matching, le chat et les communautés sont gratuits. Gold ajoute des filtres et des likes illimités — il ne verrouille pas ce pour quoi tu es venu.",
    },
  },

  how: {
    title: 'Comment ça marche',
    marketAlt: 'La boutique GameBuddy, où les pièces achètent cadres de profil et bannières',
    step1: {
      title: 'Dis à quoi tu joues',
      body: 'Choisis tes jeux, tes plateformes et ta façon de jouer — tryhard, complétionniste, co-op tranquille. Six écrans, environ une minute.',
    },
    step2: {
      title: 'Parcours le deck',
      body: "De vrais profils, classés selon leur correspondance réelle avec ce que tu as indiqué. Passe ou matche.",
    },
    step3: {
      title: 'Matche et joue',
      body: "Quand vous matchez tous les deux, la conversation s'ouvre. Ensuite, allez jouer.",
    },
  },

  faq: {
    title: 'Questions',
    q1: { q: 'GameBuddy est-il gratuit ?', a: "Oui. Le matching, le chat et les communautés sont gratuits, avec une limite quotidienne de likes. Gold est facultatif et ajoute des filtres avancés, des likes illimités et quelques extras cosmétiques — il ne met pas le matching lui-même derrière un paywall." },
    q2: { q: "Est-ce une appli de rencontre ?", a: "Non. GameBuddy associe les gens sur les jeux auxquels ils jouent et leur façon d'y jouer, pour trouver une équipe, un partenaire de co-op ou un groupe de raid. C'est construit pour jouer ensemble, pas pour draguer." },
    q3: { q: 'Quelles plateformes sont prises en charge ?', a: "Tu indiques à GameBuddy sur quoi tu joues — PC, PlayStation, Xbox, Switch ou mobile — et le matching suit, parce que les mêmes jeux sur des plateformes différentes ne se jouent généralement pas ensemble. L'appli sort d'abord sur Android, iOS ensuite." },
    q4: { q: 'Pourquoi 18 ans et plus ?', a: "GameBuddy met des inconnus en conversation privée, et ce n'est pas quelque chose à faire tourner pour des mineurs à côté d'adultes. Il n'y a pas de palier plus jeune ni de mode supervisé. L'âge est calculé à partir de la date de naissance donnée à l'inscription, et les comptes de moins de 18 ans sont fermés." },
    q5: { q: 'Que faites-vous contre le harcèlement ?', a: "Tu peux bloquer ou signaler n'importe qui depuis son profil ou la conversation, et les signalements sont examinés sous 24 heures. Chaque photo envoyée est contrôlée automatiquement avant d'être visible. Les coordonnées sont retirées des textes publics, donc personne ne peut publier un numéro dans une communauté." },
    q6: { q: "D'autres peuvent-ils lire mes messages ?", a: "Non. Le contenu des messages est chiffré avant d'être stocké : la base contient du texte chiffré, pas ta conversation. Un modérateur ne voit un message que si tu le signales." },
    q7: { q: 'Comment supprimer mon compte ?', a: "Depuis les Réglages dans l'appli, ou en écrivant au support. Ton profil, ta photo et tes préférences partent ; l'autre moitié de tes conversations reste, parce qu'elle ne t'appartient pas seule. L'explication complète est sur la page de suppression de compte." },
  },

  closing: {
    title: 'Arrête de jouer seul',
    body: 'GameBuddy est gratuit, modéré et réservé aux 18 ans et plus.',
  },

  footer: {
    tagline: 'Trouve ceux qui jouent vraiment à ce que tu joues',
    builtBy: 'Conçu en Finlande par une seule personne.',
    legalNav: 'Mentions légales et aide',
    privacy: 'Confidentialité',
    terms: "Conditions d'utilisation",
    deleteAccount: 'Supprimer ton compte',
    support: 'Aide',
    help: 'Aide',
    legalContact: 'Juridique et confidentialité',
  },

  legal: {
    englishOnly:
      "Ce document n'est disponible qu'en anglais. C'est un accord contraignant, et une traduction pourrait changer le sens d'une clause — plutôt que de publier une version approximative, nous conservons un seul texte faisant foi. Si quelque chose n'est pas clair, écris-nous et nous te l'expliquerons dans ta langue.",
  },

  support: {
    title: 'Aide',
    intro: "GameBuddy est fait par une seule personne : les réponses viennent d'un humain, en général sous deux ou trois jours.",
    emailCta: 'Écrire à',
    tryFirst: "Essaie d'abord ceci",
    writingIn: 'Quand tu écris',
    writingInBody:
      "Ça aide énormément d'indiquer ton nom d'utilisateur, ton modèle de téléphone et ta version d'Android, et ce que tu attendais au lieu de ce qui s'est passé.",
    reportFaster:
      "Signaler quelqu'un est plus rapide dans l'appli. Un signalement depuis le profil ou la conversation emporte le contexte avec lui et arrive directement dans la file de modération ; un e-mail, non.",
    legalRoute:
      "Les notifications juridiques, les réclamations et les demandes relatives aux données vont plutôt à l'adresse ci-dessous, pour ne pas rester derrière des questions de support — elles ont un délai légal.",
    a1: { q: "Je n'ai pas reçu mon code de vérification", a: "Regarde d'abord dans les spams — il y est presque toujours. Le code expire : si du temps a passé, retourne à l'écran de connexion et demandes-en un nouveau. Une nouvelle demande annule l'ancien code, utilise donc le dernier e-mail." },
    a2: { q: 'Mon deck est vide', a: "En général les filtres sont trop stricts — un filtre « en ligne maintenant » un soir calme peut vraiment ne correspondre à personne. Enlève les filtres et regarde si les profils reviennent. Si le deck est vide sans aucun filtre, écris-nous." },
    a3: { q: "Je n'ai plus de likes", a: "Les comptes gratuits ont un quota quotidien qui se réinitialise toutes les 24 heures. Gold supprime la limite. Tu peux aussi gagner des pièces dans l'appli et les dépenser en extras sans rien payer." },
    a4: { q: 'Ma photo a été refusée', a: "Chaque photo envoyée est contrôlée avant d'être visible, et tout contenu sexuel est refusé — GameBuddy est 18+, mais ce n'est pas ce genre d'appli. Si tu penses qu'une photo a été refusée à tort, écris-nous et un humain la regardera." },
    a5: { q: "Quelqu'un me harcèle", a: "Bloque-le depuis son profil ou la conversation — c'est immédiat et il n'en est pas informé. Signale-le ensuite. Les signalements sont examinés sous 24 heures. Si tu es en danger, contacte d'abord les secours ; nous ne sommes pas un service d'urgence." },
    a6: { q: "J'ai payé et je n'ai rien reçu", a: "Les achats sont confirmés par le store et ça peut prendre un instant. Si plus de quelques minutes se sont écoulées, redémarre l'appli — elle revérifie tes droits. Toujours rien ? Écris-nous avec le numéro de commande de ton reçu Google Play." },
  },

  del: {
    title: 'Supprimer ton compte',
    intro: "Tu peux fermer ton compte GameBuddy à tout moment, sans demander à personne. Voici comment, et ce qui arrive exactement à tes données.",
    inApp: "Dans l'appli",
    step1: "Ouvre GameBuddy et va dans l'onglet Profil.",
    step2: "Touche la roue dentée, puis descends jusqu'à Compte.",
    step3: "Choisis Supprimer mon compte et confirme avec ton mot de passe. Le mot de passe est exigé pour qu'un téléphone volé ne puisse pas détruire ton compte.",
    noApp: "Si tu n'as plus l'appli",
    noAppBody:
      "Écris au support depuis l'adresse e-mail du compte. Supprimer l'appli de ton téléphone ne supprime pas ton compte — et ça compte, car un compte dont l'appli est désinstallée reste visible des autres.",
    formal:
      "Pour une demande formelle au titre du RGPD — accès, rectification ou effacement — écris à l'adresse juridique. Elles reçoivent une réponse sous un mois, comme la loi l'exige.",
    removedTitle: 'Ce qui est supprimé immédiatement',
    removedNote: "Ton profil disparaît aussitôt des decks de tout le monde, et le compte ne peut plus se connecter nulle part.",
    r1: "Ton nom d'utilisateur, ton adresse e-mail et ton mot de passe",
    r2: 'Ta photo, effacée du stockage et pas seulement dissociée',
    r3: 'Ton âge, ton pays et ton genre',
    r4: 'Les jeux, plateformes et styles de jeu de ton profil',
    r5: 'Tes badges et tout ce que tu avais acheté ou équipé',
    r6: 'Chaque appareil enregistré pour les notifications',
    keptTitle: "Ce qui ne l'est pas, et pourquoi",
    keptIntro:
      "Autant être direct plutôt que prétendre que tout disparaît — chacun de ces points protège quelqu'un, et dans deux cas ce quelqu'un n'est pas toi.",
    k1: { what: "Les messages que tu as envoyés, dans les conversations d'autres personnes", why: "Ils sont anonymisés plutôt qu'effacés. L'autre garde la trace d'une conversation à laquelle elle a participé — elle ne t'appartient pas seule — et elle ne t'est plus attribuable." },
    k2: { what: "Les enregistrements d'achat", why: "La législation comptable et fiscale impose de les conserver un certain temps. Ils ne servent à rien d'autre." },
    k3: { what: "Les éléments liés à un signalement traité", why: "Conservés pour que quelqu'un exclu après avoir nui à un autre utilisateur ne puisse pas effacer les preuves en supprimant son compte." },
    knowTitle: 'Quelques points à savoir',
    n1: "La suppression est irréversible. Aucun délai de grâce, aucune récupération — pour revenir, tu te réinscris depuis zéro.",
    n2: "Les pièces et Gold ne sont pas remboursés. Les remboursements sont gérés par Google Play selon sa politique, pas par nous.",
    n3: "Moins de 18 ans ? Écris à l'adresse juridique : le compte est fermé et ses données supprimées, sans mot de passe.",
    fullDetail: 'Tout le détail est dans la politique de confidentialité.',
  },
};
