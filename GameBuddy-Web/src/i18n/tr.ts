import type { Dictionary } from './index';

/**
 * Turkish.
 *
 * Informal `sen` throughout, which is the register Turkish gaming audiences use with each
 * other and in games media.
 *
 * Established loanwords are kept where they are what people actually say — *eşleşme* for
 * match, but *co-op*, *raid* and *tryhard* left in English because the Turkish alternatives
 * are either absent or nobody uses them.
 */
export const tr: Dictionary = {
  meta: { htmlLang: 'tr', ogLocale: 'tr_TR' },

  nav: {
    features: 'Özellikler',
    how: 'Nasıl çalışır',
    support: 'Destek',
    toggleTheme: 'Açık ve koyu tema arasında geçiş yap',
    language: 'Dil',
  },

  hero: {
    badge: 'Önce Android’de',
    titleBefore: 'Senin oynadığını',
    titleHighlight: 'gerçekten oynayan',
    titleAfter: 'insanları bul',
    subtitle:
      'Yabancılarla dolu bir sunucu değil. GameBuddy seni oyunlarına, platformuna ve oynama tarzına göre eşleştirir — sonra da çekilir ki oynayabilesin.',
    comingSoon: 'Çok yakında',
    releaseNote: 'GameBuddy çıkış öncesi son aşamada. Önce Android geliyor.',
    deckAlt: 'GameBuddy destesi: bir oyuncunun profili ve oynadığı oyunlar',
    chatAlt: 'Yeni bir eşleşmenin beklediği GameBuddy sohbet listesi',
  },

  features: {
    title: 'Bir eşleşmeyi eşleşme yapan şey',
    intro:
      'Ortak ilgi üzerinden eşleştirmek kolay. Asıl mesele, gerçekten birlikte oynayabilecek insanları bir araya getirmek — aşağıdaki her şey bunun için var.',
    matched: {
      title: 'Ne oynadığına göre eşleşme',
      body: 'GameBuddy profilindeki oyunlardan ve oynama tarzından öğrenir, kaydırıp geçtiğin etiket listesinden değil. Karşına çıkan insanlarda aynı oyunlar var ve onları aynı şekilde oynuyorlar.',
    },
    platform: {
      title: 'Platformu hesaba katar',
      body: 'PC, PlayStation, Xbox, Switch ya da mobil. Aynı kütüphaneye farklı cihazlarda sahip iki kişi birlikte oynayamaz; eşleştirme bunu dikkate alır.',
    },
    chat: {
      title: 'Gizli kalan sohbet',
      body: 'Mesaj içerikleri şifreli saklanır — veritabanında sohbetin değil, şifreli metin durur. Yazıyor göstergesi ve okundu bilgisi beklediğin gibi çalışır.',
    },
    moderated: {
      title: 'Denetimli ve yalnızca yetişkinler için',
      body: 'Yüklenen her fotoğraf, başkası görmeden önce incelenir. Şikayetler 24 saat içinde ele alınır. Hesap açmak için 18 yaşından büyük olmalısın; bu doğum tarihinden kontrol edilir.',
    },
    badges: {
      title: 'Değeri olan rozetler',
      body: 'Uygulamayı gerçekten kullanarak kazandığın görevler ve rozetler, profilinde görünür. Parayla geçilen seviye yok.',
    },
    free: {
      title: 'Gerçekten ücretsiz',
      body: 'Eşleşme, sohbet ve topluluklar ücretsiz. Gold filtreler ve sınırsız beğeni ekler — geldiğin şeyin önüne duvar örmez.',
    },
  },

  how: {
    title: 'Nasıl çalışır',
    marketAlt: 'GameBuddy marketi: coinlerle profil çerçeveleri ve bannerlar alınıyor',
    step1: {
      title: 'Ne oynadığını söyle',
      body: 'Oyunlarını, platformlarını ve oynama tarzını seç — tryhard, her şeyi bitiren, sakin co-op. Altı ekran, yaklaşık bir dakika.',
    },
    step2: {
      title: 'Desteyi kaydır',
      body: 'Gerçek profiller, söylediklerine ne kadar uyduklarına göre sıralanmış. Geç ya da eşleş.',
    },
    step3: {
      title: 'Eşleş ve oyna',
      body: 'İkiniz de eşleşince sohbet açılır. Sonrası: gidip bir şeyler oynayın.',
    },
  },

  faq: {
    title: 'Sorular',
    q1: { q: 'GameBuddy ücretsiz mi?', a: 'Evet. Eşleşme, sohbet ve topluluklar ücretsiz; beğenilerde günlük bir sınır var. Gold isteğe bağlı ve gelişmiş filtreler, sınırsız beğeni ve birkaç görsel ekstra getiriyor — eşleşmenin kendisini ödeme duvarının arkasına koymuyor.' },
    q2: { q: 'Bu bir flört uygulaması mı?', a: 'Hayır. GameBuddy insanları oynadıkları oyunlara ve oynama biçimlerine göre eşleştirir; amaç bir ekip, bir co-op arkadaşı ya da bir raid grubu bulmak. Birlikte oynamak için kuruldu, flört için değil.' },
    q3: { q: 'Hangi platformları destekliyor?', a: 'GameBuddy’ye neyle oynadığını söylüyorsun — PC, PlayStation, Xbox, Switch ya da mobil — eşleşmeler de buna göre yapılıyor, çünkü farklı platformlardaki aynı oyunlar genelde birlikte oynanamaz. Uygulamanın kendisi önce Android’e geliyor, iOS sonra.' },
    q4: { q: 'Neden 18 yaş sınırı var?', a: 'GameBuddy yabancıları birbiriyle özel sohbete sokuyor ve bunu yetişkinlerin yanında reşit olmayanlar için çalıştırmak doğru değil. Daha genç bir kademe ya da denetimli mod yok. Yaş, kayıtta verilen doğum tarihinden hesaplanıyor ve 18 yaşından küçüklere ait olduğu anlaşılan hesaplar kapatılıyor.' },
    q5: { q: 'Rahatsız edenlere karşı ne yapıyorsunuz?', a: 'Herkesi profilinden ya da sohbetten engelleyebilir veya şikayet edebilirsin; şikayetler 24 saat içinde incelenir. Yüklenen her fotoğraf, kimse göremeden otomatik olarak taranır. İletişim bilgileri herkese açık metinlerden temizlenir, yani kimse topluluğa telefon numarası yazamaz.' },
    q6: { q: 'Mesajlarımı başkası görebilir mi?', a: 'Hayır. Mesaj içerikleri kaydedilmeden önce şifrelenir; veritabanında sohbetin değil şifreli metin bulunur. Moderatörler bir mesajı yalnızca sen şikayet edersen görür.' },
    q7: { q: 'Hesabımı nasıl silerim?', a: 'Uygulamadaki Ayarlar’dan ya da destek adresine yazarak. Profilin, fotoğrafın ve zevk verilerin gider; sohbetlerinin diğer yarısı kalır, çünkü onları silmek yalnızca sana bağlı değil. Tam açıklama hesap silme sayfasında.' },
  },

  closing: {
    title: 'Tek başına oynamayı bırak',
    body: 'GameBuddy ücretsiz, denetimli ve 18+.',
  },

  footer: {
    tagline: 'Senin oynadığını gerçekten oynayan insanları bul',
    builtBy: 'Finlandiya’da tek kişi tarafından yapıldı.',
    legalNav: 'Hukuk ve destek',
    privacy: 'Gizlilik',
    terms: 'Kullanım koşulları',
    deleteAccount: 'Hesabını sil',
    support: 'Destek',
    help: 'Yardım',
    legalContact: 'Hukuk ve gizlilik',
  },

  legal: {
    englishOnly:
      'Bu belge yalnızca İngilizce olarak sunuluyor. Bağlayıcı bir sözleşme ve bir çeviri, bir maddenin anlamını değiştirebilir — yaklaşık bir sürüm yayımlamak yerine tek bir geçerli metin tutuyoruz. Bir yer belirsizse bize yaz, kendi dilinde açıklayalım.',
  },

  support: {
    title: 'Destek',
    intro: 'GameBuddy’yi tek kişi yapıyor, dolayısıyla cevaplar bir insandan geliyor — genelde birkaç gün içinde.',
    emailCta: 'E-posta gönder',
    tryFirst: 'Önce bunları dene',
    writingIn: 'Yazarken',
    writingInBody:
      'Kullanıcı adını, telefon modelini ve Android sürümünü, ayrıca ne olmasını beklediğini ve bunun yerine ne olduğunu yazman çok yardımcı oluyor.',
    reportFaster:
      'Birini şikayet etmek uygulamada daha hızlı. Profilden ya da sohbetten yapılan şikayet bağlamı da yanında taşır ve doğrudan moderasyon sırasına düşer; e-posta bunu yapmaz.',
    legalRoute:
      'Hukuki bildirimler, şikayetler ve veri koruma talepleri bunun yerine aşağıdaki adrese gider; böylece destek sorularının arkasında beklemezler — onların yasal bir süresi var.',
    a1: { q: 'Doğrulama kodum gelmedi', a: 'Önce spam klasörüne bak — neredeyse her zaman oradadır. Kodun süresi doluyor, dolayısıyla üzerinden zaman geçtiyse giriş ekranına dön ve yenisini iste. Yeni kod istemek eskisini geçersiz kılar, o yüzden en son gelen e-postayı kullan.' },
    a2: { q: 'Destem boş', a: 'Bu genelde filtrelerin çok dar olduğu anlamına gelir — sakin bir akşamda “şu an çevrimiçi” filtresi gerçekten kimseyle eşleşmeyebilir. Desteden filtreleri temizle ve insanlar geri geliyor mu bak. Filtre yokken de deste boşsa bize yaz.' },
    a3: { q: 'Beğenilerim bitti', a: 'Ücretsiz hesaplarda 24 saatte bir sıfırlanan günlük bir hak var. Gold sınırı kaldırıyor. Ayrıca uygulamada coin kazanıp hiç para ödemeden ekstralara harcayabilirsin.' },
    a4: { q: 'Fotoğrafım reddedildi', a: 'Yüklenen her fotoğraf başkası görmeden önce inceleniyor ve cinsel içerikli olan her şey reddediliyor — GameBuddy 18+ ama o tür bir uygulama değil. Bir fotoğrafın haksız yere reddedildiğini düşünüyorsan bize yaz, bir insan bakacak.' },
    a5: { q: 'Biri beni taciz ediyor', a: 'Profilinden ya da sohbetten engelle — anında olur ve karşı tarafa bildirilmez. Sonra şikayet et. Şikayetler 24 saat içinde inceleniyor. Tehlikedeysen önce acil servisleri ara; biz bir acil durum servisi değiliz.' },
    a6: { q: 'Ödeme yaptım ama aldığım şey gelmedi', a: 'Satın almaları mağaza onaylıyor ve bu biraz sürebiliyor. Birkaç dakikadan fazla olduysa uygulamayı yeniden başlat — haklarını yeniden kontrol eder. Hâlâ yoksa Google Play makbuzundaki sipariş numarasıyla bize yaz.' },
  },

  del: {
    title: 'Hesabını sil',
    intro: 'GameBuddy hesabını istediğin zaman, kimseye sormadan kapatabilirsin. Nasıl yapılacağı ve verilerine tam olarak ne olacağı aşağıda.',
    inApp: 'Uygulamada',
    step1: 'GameBuddy’yi aç ve Profil sekmesine git.',
    step2: 'Ayarlar dişlisine dokun, sonra Hesap bölümüne in.',
    step3: 'Hesabımı sil’i seç ve şifrenle onayla. Şifre isteniyor ki çalınan bir telefon hesabını yok edemesin.',
    noApp: 'Uygulama artık sende yoksa',
    noAppBody:
      'Hesabın e-posta adresinden destek adresine yaz. Uygulamayı telefondan silmek hesabı silmez — ve bu önemli, çünkü uygulaması kaldırılmış bir hesap başkalarına hâlâ görünür.',
    formal:
      'GDPR kapsamında resmi bir talep için — erişim, düzeltme veya silme — hukuk adresine yaz. Bunlar yasanın gerektirdiği gibi bir ay içinde yanıtlanır.',
    removedTitle: 'Hemen silinenler',
    removedNote: 'Profilin herkesin destesinden anında çıkar ve hesap artık hiçbir yerde giriş yapamaz.',
    r1: 'Kullanıcı adın, e-posta adresin ve şifren',
    r2: 'Fotoğrafın — bağlantısı kesilmekle kalmaz, depolamadan silinir',
    r3: 'Yaşın, ülken ve cinsiyetin',
    r4: 'Profilindeki oyunlar, platformlar ve oynama tarzları',
    r5: 'Rozetlerin ve satın aldığın ya da kuşandığın her şey',
    r6: 'Bildirimler için kayıtlı her cihaz',
    keptTitle: 'Silinmeyenler ve nedeni',
    keptIntro:
      'Her şeyin yok olduğunu iddia etmek yerine açık konuşalım — bunların her biri birini koruyor ve iki durumda o kişi sen değilsin.',
    k1: { what: 'Başkalarının sohbetlerinde senin gönderdiğin mesajlar', why: 'Silinmez, anonimleştirilir. Karşı taraf katıldığı bir sohbetin kaydını korur — bu kayıt yalnızca sana ait değil — ve artık seninle ilişkilendirilemez.' },
    k2: { what: 'Satın alma kayıtları', why: 'Muhasebe ve vergi mevzuatı belirli bir süre saklanmasını zorunlu kılıyor. Başka hiçbir şey için kullanılmıyorlar.' },
    k3: { what: 'İşlem yapılmış bir şikayete ilişkin materyal', why: 'Başka bir kullanıcıya zarar verdiği için uzaklaştırılan birinin, hesabını silerek kanıtları yok edememesi için saklanır.' },
    knowTitle: 'Bilmekte fayda olan birkaç şey',
    n1: 'Silme geri alınamaz. Bekleme süresi ya da kurtarma yok — geri dönmek istersen sıfırdan yeniden kayıt olursun.',
    n2: 'Coin ve Gold iade edilmez. İadeleri kendi politikası uyarınca Google Play yürütür, biz değil.',
    n3: '18 yaşından küçük müsün? Hukuk adresine yaz; hesap kapatılır ve verileri silinir, şifreye gerek kalmadan.',
    fullDetail: 'Tüm ayrıntı gizlilik politikasında.',
  },
};
