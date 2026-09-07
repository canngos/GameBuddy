import type { Dictionary } from './index';

/**
 * Finnish.
 *
 * Translated from the English rather than rendered word for word. A few choices worth
 * recording, because the next person to edit these will wonder:
 *
 * - Second person singular (`sinä`-muoto) throughout. Finnish marketing that uses the polite
 *   plural to one reader sounds like a bank, and this is a product for gamers.
 * - "Deck" is `korttipino` in the body text but the app's own screens say "DISCOVER" — the
 *   app is not localised yet, so the site does not promise Finnish UI.
 * - `pelikaveri` is left as ordinary vocabulary rather than treated as a brand word;
 *   GameBuddy itself stays untranslated, as product names should.
 */
export const fi: Dictionary = {
  meta: { htmlLang: 'fi', ogLocale: 'fi_FI' },

  nav: {
    features: 'Ominaisuudet',
    how: 'Näin se toimii',
    support: 'Tuki',
    toggleTheme: 'Vaihda vaalean ja tumman välillä',
    language: 'Kieli',
  },

  hero: {
    badge: 'Julkaistaan ensin Androidille',
    titleBefore: 'Löydä ne, jotka',
    titleHighlight: 'oikeasti pelaavat',
    titleAfter: 'samaa kuin sinä',
    subtitle:
      'Ei satunnaisia tuntemattomia täynnä olevaa palvelinta. GameBuddy yhdistää sinut pelien, alustan ja pelityylin perusteella — ja jää sitten pois tieltä, jotta pääset pelaamaan.',
    comingSoon: 'Tulossa pian',
    releaseNote: 'GameBuddy on viimeistelyvaiheessa. Android tulee ensin.',
    deckAlt: 'GameBuddyn korttipino, jossa näkyy pelaajan profiili ja hänen pelinsä',
    chatAlt: 'GameBuddyn keskustelulista, jossa uusi matchi odottaa',
  },

  features: {
    title: 'Mikä tekee osumasta osuman',
    intro:
      'Yhteisen kiinnostuksen perusteella yhdistäminen on helppoa. Vaikeaa on löytää ihmiset, jotka oikeasti pystyvät pelaamaan yhdessä — siitä kaikessa alla on kyse.',
    matched: {
      title: 'Osumat pelien perusteella',
      body: 'GameBuddy oppii profiilisi peleistä ja pelityyleistä, ei tagilistasta jonka selaat ohi. Näytetyillä ihmisillä on samat pelit ja sama tapa pelata niitä.',
    },
    platform: {
      title: 'Alusta huomioidaan',
      body: 'PC, PlayStation, Xbox, Switch tai mobiili. Kaksi ihmistä joilla on samat pelit eri alustoilla eivät voi pelata yhdessä, joten osumissa otetaan tämä huomioon.',
    },
    chat: {
      title: 'Keskustelut pysyvät yksityisinä',
      body: 'Viestien sisältö salataan levylle — tietokannassa on salakirjoitusta, ei keskusteluasi. Kirjoitusilmaisin ja luettu-tila toimivat kuten odotat.',
    },
    moderated: {
      title: 'Valvottu, ja vain aikuisille',
      body: 'Jokainen ladattu kuva tarkistetaan ennen kuin kukaan muu näkee sen. Ilmoitukset käsitellään 24 tunnin kuluessa. Tilin haltijan on oltava vähintään 18-vuotias, ja ikä tarkistetaan syntymäajasta.',
    },
    badges: {
      title: 'Merkit, joilla on merkitystä',
      body: 'Tehtäviä ja merkkejä, jotka ansaitset käyttämällä sovellusta — näkyvät profiilissasi. Ei maksamalla ohitettavia tasoja.',
    },
    free: {
      title: 'Ilmainen, oikeasti',
      body: 'Osumat, keskustelut ja yhteisöt ovat ilmaisia. Gold tuo suodattimet ja rajattomat tykkäykset — se ei lukitse sitä, minkä takia tulit.',
    },
  },

  how: {
    title: 'Näin se toimii',
    marketAlt: 'GameBuddyn kauppa, jossa kolikoilla ostetaan profiilikehyksiä ja bannereita',
    step1: {
      title: 'Kerro mitä pelaat',
      body: 'Valitse pelisi, alustasi ja pelityylisi — tryhard, kaiken kerääjä, rento co-op. Kuusi näkymää, noin minuutti.',
    },
    step2: {
      title: 'Selaa korttipinoa',
      body: 'Oikeita profiileja, järjestettynä sen mukaan miten hyvin ne vastaavat kertomaasi. Ohita tai tykkää.',
    },
    step3: {
      title: 'Osuma ja peliin',
      body: 'Kun tykkäätte molemmat, keskustelu aukeaa. Sitten vain pelaamaan.',
    },
  },

  faq: {
    title: 'Kysymyksiä',
    q1: { q: 'Onko GameBuddy ilmainen?', a: 'On. Osumat, keskustelut ja yhteisöt ovat ilmaisia, ja tykkäyksillä on päivittäinen raja. Gold on vapaaehtoinen ja tuo lisäsuodattimet, rajattomat tykkäykset ja muutaman ulkoasulisän — se ei laita itse osumia maksumuurin taakse.' },
    q2: { q: 'Onko tämä deittisovellus?', a: 'Ei. GameBuddy yhdistää ihmisiä pelien ja pelitavan perusteella, jotta löydät porukan, co-op-kaverin tai raid-ryhmän. Se on rakennettu yhdessä pelaamiseen, ei deittailuun.' },
    q3: { q: 'Mitä alustoja tuetaan?', a: 'Kerrot GameBuddylle millä pelaat — PC, PlayStation, Xbox, Switch tai mobiili — ja osumat tehdään sen mukaan, koska samat pelit eri alustoilla eivät yleensä tarkoita yhdessä pelaamista. Sovellus itse tulee ensin Androidille, iOS myöhemmin.' },
    q4: { q: 'Miksi ikäraja on 18?', a: 'GameBuddy laittaa tuntemattomat yksityiseen keskusteluun keskenään, eikä sitä ole syytä pyörittää alaikäisille aikuisten rinnalla. Nuorempaa tasoa tai valvottua tilaa ei ole. Ikä lasketaan rekisteröinnissä annetusta syntymäajasta, ja alle 18-vuotiaiden tilit suljetaan.' },
    q5: { q: 'Entä häiriköt?', a: 'Voit estää tai ilmoittaa kenet tahansa profiilista tai keskustelusta, ja ilmoitukset käsitellään 24 tunnin kuluessa. Jokainen ladattu kuva tarkistetaan automaattisesti ennen kuin kukaan muu näkee sen. Yhteystiedot poistetaan julkisesta tekstistä, joten puhelinnumeroa ei voi julkaista yhteisöön.' },
    q6: { q: 'Näkeekö joku muu viestini?', a: 'Ei. Viestien sisältö salataan ennen tallennusta, joten tietokannassa on salakirjoitusta eikä keskusteluasi. Valvoja näkee viestin vain jos ilmoitat siitä.' },
    q7: { q: 'Miten poistan tilini?', a: 'Sovelluksen asetuksista tai kirjoittamalla tukeen. Profiilisi, kuvasi ja makutietosi poistetaan; keskustelujen toinen puoli jää, koska se ei ole yksin sinun poistettavissasi. Koko selitys on tilinpoistosivulla.' },
  },

  closing: {
    title: 'Lopeta yksin pelaaminen',
    body: 'GameBuddy on ilmainen, valvottu ja 18+.',
  },

  footer: {
    tagline: 'Löydä ne, jotka oikeasti pelaavat samaa kuin sinä',
    builtBy: 'Tehty Suomessa, yhden ihmisen voimin.',
    legalNav: 'Ehdot ja tuki',
    privacy: 'Tietosuoja',
    terms: 'Käyttöehdot',
    childSafety: 'Lasten turvallisuus',
    deleteAccount: 'Poista tilisi',
    support: 'Tuki',
    help: 'Apua',
    legalContact: 'Juridiikka ja tietosuoja',
  },

  legal: {
    englishOnly:
      'Tämä asiakirja on saatavilla vain englanniksi. Kyseessä on sitova sopimus, ja käännös voisi muuttaa jonkin kohdan merkitystä — joten likimääräisen version julkaisemisen sijaan pidämme yhden virallisen tekstin. Jos jokin jää epäselväksi, kirjoita meille niin selitämme sen omalla kielelläsi.',
  },

  support: {
    title: 'Tuki',
    intro: 'GameBuddyn tekee yksi ihminen, joten vastauksen kirjoittaa ihminen — yleensä parin päivän sisällä.',
    emailCta: 'Lähetä sähköpostia',
    tryFirst: 'Kokeile ensin näitä',
    writingIn: 'Kun kirjoitat',
    writingInBody:
      'Auttaa valtavasti, jos kerrot käyttäjänimesi, puhelinmallisi ja Android-versiosi sekä mitä odotit tapahtuvan sen sijaan mitä tapahtui.',
    reportFaster:
      'Ilmoituksen tekeminen on nopeampaa sovelluksessa. Profiilista tai keskustelusta tehty ilmoitus kuljettaa asiayhteyden mukanaan ja menee suoraan valvontajonoon; sähköposti ei.',
    legalRoute:
      'Juridiset ilmoitukset, valitukset ja tietosuojapyynnöt menevät alla olevaan osoitteeseen, jotta ne eivät jää tukikysymysten taakse — niillä on lakisääteinen määräaika.',
    a1: { q: 'En saanut vahvistuskoodia', a: 'Tarkista ensin roskapostikansio — se on lähes aina siellä. Koodi vanhenee, joten jos aikaa on kulunut, palaa kirjautumisnäkymään ja pyydä uusi. Uuden koodin pyytäminen mitätöi vanhan, joten käytä viimeisintä viestiä.' },
    a2: { q: 'Korttipinoni on tyhjä', a: 'Yleensä suodattimet ovat liian tiukat — "paikalla nyt" hiljaisena iltana voi aidosti osua tyhjään. Tyhjennä suodattimet ja katso palaavatko ihmiset. Jos pino on tyhjä ilman suodattimia, kirjoita meille.' },
    a3: { q: 'Tykkäykseni loppuivat', a: 'Ilmaisilla tileillä on päivittäinen määrä, joka nollautuu 24 tunnin välein. Gold poistaa rajan. Voit myös ansaita kolikoita sovelluksessa ja käyttää ne lisiin maksamatta mitään.' },
    a4: { q: 'Kuvani hylättiin', a: 'Jokainen ladattu kuva tarkistetaan ennen kuin kukaan muu näkee sen, ja seksuaalinen sisältö hylätään — GameBuddy on 18+ mutta ei sitä lajia. Jos kuva mielestäsi hylättiin väärin, kirjoita meille niin ihminen katsoo sen.' },
    a5: { q: 'Joku häiriköi minua', a: 'Estä hänet profiilista tai keskustelusta — se tapahtuu heti eikä hänelle kerrota. Tee sitten ilmoitus. Ilmoitukset käsitellään 24 tunnin kuluessa. Jos olet vaarassa, ota ensin yhteyttä hätänumeroon; me emme ole hätäpalvelu.' },
    a6: { q: 'Maksoin enkä saanut ostostani', a: 'Kauppa vahvistaa ostot, ja siinä voi kestää hetki. Jos on kulunut yli muutama minuutti, käynnistä sovellus uudelleen — se tarkistaa oikeutesi uudestaan. Puuttuuko yhä? Kirjoita meille ja liitä mukaan tilausnumero Google Play -kuitista.' },
  },

  del: {
    title: 'Poista tilisi',
    intro: 'Voit sulkea GameBuddy-tilisi milloin tahansa kysymättä keneltäkään. Näin se tehdään, ja näin tiedoillesi käy.',
    inApp: 'Sovelluksessa',
    step1: 'Avaa GameBuddy ja mene Profiili-välilehdelle.',
    step2: 'Napauta asetusrattaita ja vieritä kohtaan Tili.',
    step3: 'Valitse Poista tilini ja vahvista salasanallasi. Salasanaa kysytään, jotta varastettu puhelin ei voi tuhota tiliäsi.',
    noApp: 'Jos sovellusta ei enää ole',
    noAppBody:
      'Kirjoita tukeen tilin sähköpostiosoitteesta. Sovelluksen poistaminen puhelimesta ei poista tiliä, ja tällä on väliä — poistetunkin sovelluksen tili näkyy yhä muille.',
    formal:
      'Virallinen GDPR-pyyntö — tietojen saanti, oikaisu tai poisto — lähetetään juridiseen osoitteeseen. Ne vastataan kuukauden kuluessa, kuten laki edellyttää.',
    removedTitle: 'Mikä poistetaan heti',
    removedNote: 'Profiilisi katoaa kaikkien korttipinoista välittömästi, eikä tilillä voi enää kirjautua mihinkään.',
    r1: 'Käyttäjänimesi, sähköpostiosoitteesi ja salasanasi',
    r2: 'Valokuvasi, poistettuna tallennustilasta eikä vain irrotettuna',
    r3: 'Ikäsi, maasi ja sukupuolesi',
    r4: 'Profiilisi pelit, alustat ja pelityylit',
    r5: 'Merkkisi ja kaikki ostamasi tai käytössäsi ollut',
    r6: 'Jokainen push-ilmoituksiin rekisteröity laite',
    keptTitle: 'Mikä ei, ja miksi',
    keptIntro:
      'Sanotaan tämä suoraan sen sijaan että väitettäisiin kaiken katoavan — jokainen näistä suojaa jotakuta, ja kahdessa tapauksessa se joku et ole sinä.',
    k1: { what: 'Lähettämäsi viestit toisten keskusteluissa', why: 'Ne anonymisoidaan, ei poisteta. Toiselle jää tallenne keskustelusta johon hän osallistui — se ei ole yksin sinun poistettavissasi — eikä sitä voi enää yhdistää sinuun.' },
    k2: { what: 'Ostotiedot', why: 'Kirjanpito- ja verolainsäädäntö vaatii säilyttämään ne määräajan. Niitä ei käytetä mihinkään muuhun.' },
    k3: { what: 'Aineisto käsitellystä ilmoituksesta', why: 'Säilytetään, jotta toista käyttäjää vahingoittanut ei voi poistaa todisteita poistamalla tilinsä.' },
    knowTitle: 'Muutama asia hyvä tietää',
    n1: 'Poistoa ei voi perua. Ei harkinta-aikaa eikä palautusta — jos haluat takaisin, rekisteröidyt uudestaan tyhjästä.',
    n2: 'Kolikoita ja Goldia ei hyvitetä. Hyvitykset hoitaa Google Play oman käytäntönsä mukaan, emme me.',
    n3: 'Alle 18-vuotias? Kirjoita juridiseen osoitteeseen, niin tili suljetaan ja tiedot poistetaan ilman salasanaa.',
    fullDetail: 'Koko selitys on tietosuojaselosteessa.',
  },
};
