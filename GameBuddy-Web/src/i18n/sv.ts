import type { Dictionary } from './index';

/**
 * Swedish.
 *
 * Sweden-Swedish rather than Finland-Swedish. The two differ in vocabulary more than most
 * people expect, and standard rikssvenska is understood by both audiences — whereas
 * finlandssvenska reads as regional to a reader in Stockholm.
 *
 * `du`-tilltal throughout, which has been the default register in Swedish for decades and
 * is certainly right for this product.
 */
export const sv: Dictionary = {
  meta: { htmlLang: 'sv', ogLocale: 'sv_SE' },

  nav: {
    features: 'Funktioner',
    how: 'Så funkar det',
    support: 'Support',
    toggleTheme: 'Växla mellan ljust och mörkt',
    language: 'Språk',
  },

  hero: {
    badge: 'Släpps först på Android',
    titleBefore: 'Hitta dem som',
    titleHighlight: 'faktiskt spelar',
    titleAfter: 'det du spelar',
    subtitle:
      'Inte en server full av främlingar. GameBuddy matchar dig utifrån dina spel, din plattform och hur du gillar att spela — och håller sig sedan undan så att du kan spela.',
    comingSoon: 'Kommer snart till',
    getItOn: 'Hämta i',
    releaseNote: 'GameBuddy är i slutfasen före lansering. Android kommer först.',
    deckAlt: 'GameBuddys kortlek som visar en spelares profil och vilka spel hen spelar',
    chatAlt: 'GameBuddys konversationslista, med en ny match som väntar',
  },

  features: {
    title: 'Vad som gör en matchning till en matchning',
    intro:
      'Att matcha på ett gemensamt intresse är enkelt. Att matcha personer som faktiskt kan spela tillsammans är det svåra — och det är vad allt nedan finns till för.',
    matched: {
      title: 'Matchat på vad du spelar',
      body: 'GameBuddy lär sig av spelen och spelstilarna i din profil, inte av en tagglista du scrollar förbi. Personerna du ser äger samma spel och spelar dem på samma sätt.',
    },
    platform: {
      title: 'Plattformsmedvetet',
      body: 'PC, PlayStation, Xbox, Switch eller mobil. Två personer med identiska bibliotek på olika plattformar kan inte spela ihop, så matchningen tar hänsyn till det.',
    },
    chat: {
      title: 'Chatt som förblir privat',
      body: 'Meddelandenas innehåll krypteras i vila — databasen lagrar chiffertext, inte din konversation. Skrivindikator och lässtatus fungerar som du förväntar dig.',
    },
    moderated: {
      title: 'Modererat, och endast för vuxna',
      body: 'Varje uppladdad bild granskas innan någon annan ser den. Anmälningar behandlas inom 24 timmar. Du måste vara 18 år eller äldre för att ha ett konto, och åldern kontrolleras mot ett födelsedatum.',
    },
    badges: {
      title: 'Märken värda att ha',
      body: 'Uppdrag och märken du förtjänar genom att faktiskt använda appen, synliga på din profil. Inga nivåer man betalar sig förbi.',
    },
    free: {
      title: 'Gratis, på riktigt',
      body: 'Matchningen, chatten och lobbyerna är gratis. Gold lägger till filter och obegränsade gillningar — det låser inte det du kom hit för.',
    },
  },

  how: {
    title: 'Så funkar det',
    marketAlt: 'GameBuddys marknad där mynt köper profilramar och banners',
    step1: {
      title: 'Berätta vad du spelar',
      body: 'Välj dina spel, dina plattformar och hur du gillar att spela — tryhard, samlare, lugnt co-op. Sex skärmar, ungefär en minut.',
    },
    step2: {
      title: 'Svep genom kortleken',
      body: 'Riktiga profiler, rangordnade efter hur väl de faktiskt passar det du angav. Hoppa över eller matcha.',
    },
    step3: {
      title: 'Matcha och spela',
      body: 'När ni båda matchar öppnas konversationen. Sedan är det bara att spela.',
    },
  },

  faq: {
    title: 'Frågor',
    q1: { q: 'Är GameBuddy gratis?', a: 'Ja. Matchning, chatt och lobbyer är gratis, med en daglig gräns för gillningar. Gold är valfritt och ger avancerade filter, obegränsade gillningar och några kosmetiska extrafunktioner — det sätter inte själva matchningen bakom en betalvägg.' },
    q2: { q: 'Är det en dejtingapp?', a: 'Nej. GameBuddy matchar människor utifrån vilka spel de spelar och hur de spelar dem, så att du kan hitta ett gäng, en co-op-partner eller en raidgrupp. Den är byggd för att spela tillsammans, inte för att dejta.' },
    q3: { q: 'Vilka plattformar stöds?', a: 'Du berättar för GameBuddy vad du spelar på — PC, PlayStation, Xbox, Switch eller mobil — och matchningen sker därefter, eftersom samma spel på olika plattformar oftast inte går att spela ihop. Appen kommer först till Android, iOS senare.' },
    q4: { q: 'Varför 18-årsgräns?', a: 'GameBuddy sätter främlingar i privat konversation med varandra, och det ska inte drivas för minderåriga vid sidan av vuxna. Det finns ingen yngre nivå och inget övervakat läge. Åldern räknas ut från ett födelsedatum som anges vid registrering, och konton som visar sig tillhöra personer under 18 stängs.' },
    q5: { q: 'Vad gör ni åt otrevliga typer?', a: 'Du kan blockera eller anmäla vem som helst från profilen eller konversationen, och anmälningar granskas inom 24 timmar. Varje uppladdad bild granskas automatiskt innan någon annan kan se den. Kontaktuppgifter rensas bort ur offentlig text, så ingen kan lägga upp ett telefonnummer i en lobby.' },
    q6: { q: 'Kan andra se mina meddelanden?', a: 'Nej. Meddelandenas innehåll krypteras innan det lagras, så databasen innehåller chiffertext och inte din konversation. Moderatorer ser ett meddelande endast när du anmäler det.' },
    q7: { q: 'Hur raderar jag mitt konto?', a: 'Från Inställningar i appen, eller genom att skriva till supporten. Din profil, din bild och dina smakdata försvinner; den andra halvan av dina konversationer blir kvar, eftersom den inte bara är din att radera. Hela förklaringen finns på sidan om kontoradering.' },
  },

  closing: {
    title: 'Sluta spela ensam',
    body: 'GameBuddy är gratis, modererat och 18+.',
  },

  footer: {
    tagline: 'Hitta dem som faktiskt spelar det du spelar',
    builtBy: 'Byggt i Finland av en person.',
    legalNav: 'Juridik och support',
    privacy: 'Integritet',
    terms: 'Villkor',
    childSafety: 'Barnsäkerhet',
    deleteAccount: 'Radera ditt konto',
    support: 'Support',
    help: 'Hjälp',
    legalContact: 'Juridik och integritet',
  },

  legal: {
    englishOnly:
      'Det här dokumentet finns endast på engelska. Det är ett bindande avtal, och en översättning skulle kunna ändra innebörden i en klausul — så i stället för att publicera en ungefärlig version håller vi oss till en auktoritativ text. Om något är oklart, skriv till oss så förklarar vi det på ditt språk.',
  },

  support: {
    title: 'Support',
    intro: 'GameBuddy görs av en person, så svaren kommer från en människa — oftast inom ett par dagar.',
    emailCta: 'Mejla',
    tryFirst: 'Prova det här först',
    writingIn: 'När du skriver',
    writingInBody:
      'Det hjälper enormt om du anger ditt användarnamn, din telefonmodell och Android-version, och vad du förväntade dig skulle hända i stället för vad som hände.',
    reportFaster:
      'Att anmäla någon går snabbare i appen. En anmälan från profilen eller konversationen tar med sig sammanhanget och hamnar direkt i modereringskön; ett mejl om saken gör inte det.',
    legalRoute:
      'Juridiska meddelanden, klagomål och dataskyddsförfrågningar går till adressen nedan i stället, så att de inte hamnar bakom supportfrågor — de har en lagstadgad tidsfrist.',
    a1: { q: 'Jag fick ingen verifieringskod', a: 'Kolla skräpposten först — den ligger nästan alltid där. Koden går ut, så om det har gått en stund, gå tillbaka till inloggningsskärmen och begär en ny. Att begära en ny kod gör den gamla ogiltig, så använd det senaste mejlet.' },
    a2: { q: 'Min kortlek är tom', a: 'Det betyder oftast att filtren är för snäva — ett "online nu"-filter en lugn kväll kan faktiskt matcha ingen. Rensa filtren från kortleken och se om folk kommer tillbaka. Är leken tom utan filter, skriv till oss.' },
    a3: { q: 'Mina gillningar tog slut', a: 'Gratiskonton har en daglig mängd som återställs var 24:e timme. Gold tar bort gränsen. Du kan också tjäna mynt i appen och lägga dem på extrafunktioner utan att betala något.' },
    a4: { q: 'Min bild nekades', a: 'Varje uppladdad bild granskas innan någon annan ser den, och allt sexuellt nekas — GameBuddy är 18+ men inte den sortens app. Om du tycker att en bild nekades felaktigt, skriv till oss så tittar en människa på den.' },
    a5: { q: 'Någon trakasserar mig', a: 'Blockera personen från profilen eller konversationen — det sker omedelbart och personen får inte veta det. Anmäl sedan. Anmälningar granskas inom 24 timmar. Är du i fara, kontakta larmnumret först; vi är inte en larmtjänst.' },
    a6: { q: 'Jag betalade men fick inte det jag köpte', a: 'Köp bekräftas av butiken och det kan ta en stund. Har det gått mer än några minuter, starta om appen — då kontrolleras dina rättigheter på nytt. Saknas det fortfarande? Skriv till oss med ordernumret från ditt Google Play-kvitto.' },
  },

  del: {
    title: 'Radera ditt konto',
    intro: 'Du kan stänga ditt GameBuddy-konto när som helst, utan att fråga någon. Så här gör du, och så här går det med dina uppgifter.',
    inApp: 'I appen',
    step1: 'Öppna GameBuddy och gå till fliken Profil.',
    step2: 'Tryck på kugghjulet och scrolla till Konto.',
    step3: 'Välj Radera mitt konto och bekräfta med ditt lösenord. Lösenordet krävs för att en stulen telefon inte ska kunna förstöra ditt konto.',
    noApp: 'Om du inte har appen kvar',
    noAppBody:
      'Skriv till supporten från e-postadressen som hör till kontot. Att radera appen från telefonen raderar inte kontot, och det spelar roll — ett konto vars app är avinstallerad syns fortfarande för andra.',
    formal:
      'För en formell begäran enligt GDPR — tillgång, rättelse eller radering — skriv till den juridiska adressen. De besvaras inom en månad, som lagen kräver.',
    removedTitle: 'Vad som tas bort omedelbart',
    removedNote: 'Din profil slutar dyka upp i allas kortlekar direkt, och kontot kan inte längre logga in någonstans.',
    r1: 'Ditt användarnamn, din e-postadress och ditt lösenord',
    r2: 'Ditt foto, raderat från lagringen och inte bara avlänkat',
    r3: 'Din ålder, ditt land och ditt kön',
    r4: 'Spelen, plattformarna och spelstilarna i din profil',
    r5: 'Dina märken och allt du köpt eller haft utrustat',
    r6: 'Varje enhet som registrerats för pushnotiser',
    keptTitle: 'Vad som inte gör det, och varför',
    keptIntro:
      'Vi säger det rakt ut i stället för att påstå att allt försvinner — var och en av dessa finns för att skydda någon, och i två fall är den någon inte du.',
    k1: { what: 'Meddelanden du skickat, inuti andras konversationer', why: 'De anonymiseras i stället för att raderas. Den andra personen behåller en logg över en konversation hen deltog i — den är inte bara din att radera — och den går inte längre att koppla till dig.' },
    k2: { what: 'Köphistorik', why: 'Bokförings- och skattelagstiftning kräver att den sparas en viss tid. Den används inte till något annat.' },
    k3: { what: 'Material som rör en anmälan som lett till åtgärd', why: 'Sparas så att någon som stängts av för att ha skadat en annan användare inte kan radera bevisen genom att radera sitt konto.' },
    knowTitle: 'Några saker värda att veta',
    n1: 'Radering går inte att ångra. Ingen betänketid och ingen återställning — vill du tillbaka registrerar du dig på nytt, från noll.',
    n2: 'Mynt och Gold återbetalas inte. Återbetalningar hanteras av Google Play enligt deras policy, inte av oss.',
    n3: 'Under 18? Skriv till den juridiska adressen så stängs kontot och uppgifterna raderas, utan att lösenord behövs.',
    fullDetail: 'Alla detaljer finns i integritetspolicyn.',
  },
};
