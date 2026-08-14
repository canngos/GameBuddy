import type { Dictionary } from './index';

/**
 * Spanish.
 *
 * Peninsular Spanish with `tú`, and neutral enough to read naturally in Latin America. The
 * one place the two genuinely diverge is the second person plural, which this copy avoids
 * entirely — there is no `vosotros` here, so no sentence marks the text as Spain-only.
 *
 * `móvil` rather than `celular` is the one Spain-leaning choice; it is understood everywhere,
 * whereas `celular` reads as clearly Latin American to a Spanish reader.
 */
export const es: Dictionary = {
  meta: { htmlLang: 'es', ogLocale: 'es_ES' },

  nav: {
    features: 'Funciones',
    how: 'Cómo funciona',
    support: 'Ayuda',
    toggleTheme: 'Cambiar entre claro y oscuro',
    language: 'Idioma',
  },

  hero: {
    badge: 'Primero en Android',
    titleBefore: 'Encuentra a quienes',
    titleHighlight: 'sí juegan',
    titleAfter: 'a lo que tú juegas',
    subtitle:
      'No es un servidor lleno de desconocidos. GameBuddy te empareja por tus juegos, tu plataforma y tu forma de jugar — y luego se aparta para que juegues.',
    comingSoon: 'Muy pronto en',
    releaseNote: 'GameBuddy está en la recta final antes del lanzamiento. Android va primero.',
    deckAlt: 'El mazo de GameBuddy, con el perfil de un jugador y los juegos a los que juega',
    chatAlt: 'Una conversación de GameBuddy entre dos jugadores emparejados',
  },

  features: {
    title: 'Qué hace que un match sea un match',
    intro:
      'Emparejar por un interés común es fácil. Emparejar a personas que de verdad pueden jugar juntas es lo difícil — y para eso está todo lo que viene.',
    matched: {
      title: 'Emparejado por lo que juegas',
      body: 'GameBuddy aprende de los juegos y estilos de juego de tu perfil, no de una lista de etiquetas que pasas de largo. Las personas que te muestra tienen los mismos juegos y los juegan igual.',
    },
    platform: {
      title: 'La plataforma importa',
      body: 'PC, PlayStation, Xbox, Switch o móvil. Dos personas con la misma biblioteca en máquinas distintas no pueden jugar juntas, y el emparejamiento lo tiene en cuenta.',
    },
    chat: {
      title: 'Un chat que sigue siendo privado',
      body: 'El contenido de los mensajes se cifra en reposo: la base de datos guarda texto cifrado, no tu conversación. El indicador de escritura y el estado de leído funcionan como esperas.',
    },
    moderated: {
      title: 'Moderado, y solo para adultos',
      body: 'Cada foto que se sube se revisa antes de que la vea nadie más. Las denuncias se atienden en 24 horas. Hay que tener 18 años o más, y se comprueba con una fecha de nacimiento.',
    },
    badges: {
      title: 'Insignias que valen algo',
      body: 'Misiones e insignias que ganas usando la app de verdad, visibles en tu perfil. Sin niveles que se compran.',
    },
    free: {
      title: 'Gratis, de verdad',
      body: 'El emparejamiento, el chat y las comunidades son gratis. Gold añade filtros y likes ilimitados — no bloquea aquello por lo que viniste.',
    },
  },

  how: {
    title: 'Cómo funciona',
    marketAlt: 'La tienda de GameBuddy, donde las monedas compran marcos de perfil y banners',
    step1: {
      title: 'Di a qué juegas',
      body: 'Elige tus juegos, tus plataformas y tu forma de jugar — tryhard, completista, co-op tranquilo. Seis pantallas, alrededor de un minuto.',
    },
    step2: {
      title: 'Pasa el mazo',
      body: 'Perfiles reales, ordenados según lo bien que encajan con lo que has dicho. Pasa o haz match.',
    },
    step3: {
      title: 'Haz match y juega',
      body: 'Cuando los dos hacéis match, se abre la conversación. Y después, a jugar.',
    },
  },

  faq: {
    title: 'Preguntas',
    q1: { q: '¿GameBuddy es gratis?', a: 'Sí. El emparejamiento, el chat y las comunidades son gratis, con un límite diario de likes. Gold es opcional y añade filtros avanzados, likes ilimitados y algunos extras cosméticos — no pone el emparejamiento en sí detrás de un muro de pago.' },
    q2: { q: '¿Es una app de citas?', a: 'No. GameBuddy empareja a personas por los juegos a los que juegan y cómo los juegan, para que encuentres un grupo, un compañero de co-op o gente para una raid. Está hecha para jugar juntos, no para ligar.' },
    q3: { q: '¿Qué plataformas admite?', a: 'Le dices a GameBuddy en qué juegas — PC, PlayStation, Xbox, Switch o móvil — y el emparejamiento va en consecuencia, porque los mismos juegos en plataformas distintas normalmente no se pueden jugar juntos. La app sale primero en Android; iOS después.' },
    q4: { q: '¿Por qué +18?', a: 'GameBuddy pone a desconocidos a hablar en privado entre ellos, y eso no es algo que deba funcionar para menores junto a adultos. No hay nivel más joven ni modo supervisado. La edad se calcula a partir de la fecha de nacimiento indicada al registrarse, y las cuentas de menores de 18 se cierran.' },
    q5: { q: '¿Qué hacéis con los indeseables?', a: 'Puedes bloquear o denunciar a cualquiera desde su perfil o desde la conversación, y las denuncias se revisan en 24 horas. Cada foto subida se revisa automáticamente antes de que nadie pueda verla. Los datos de contacto se eliminan del texto público, así que nadie puede publicar un teléfono en una comunidad.' },
    q6: { q: '¿Puede alguien ver mis mensajes?', a: 'No. El contenido de los mensajes se cifra antes de guardarse, así que la base de datos contiene texto cifrado y no tu conversación. Un moderador solo ve un mensaje si tú lo denuncias.' },
    q7: { q: '¿Cómo borro mi cuenta?', a: 'Desde Ajustes en la app, o escribiendo a soporte. Tu perfil, tu foto y tus preferencias desaparecen; la otra mitad de tus conversaciones permanece, porque no es solo tuya para borrarla. La explicación completa está en la página de borrado de cuenta.' },
  },

  closing: {
    title: 'Deja de jugar solo',
    body: 'GameBuddy es gratis, moderado y para mayores de 18.',
  },

  footer: {
    tagline: 'Encuentra a quienes sí juegan a lo que tú juegas',
    builtBy: 'Hecho en Finlandia por una sola persona.',
    legalNav: 'Legal y ayuda',
    privacy: 'Privacidad',
    terms: 'Términos',
    deleteAccount: 'Borrar tu cuenta',
    support: 'Ayuda',
    help: 'Ayuda',
    legalContact: 'Legal y privacidad',
  },

  legal: {
    englishOnly:
      'Este documento solo está disponible en inglés. Es un acuerdo vinculante, y una traducción podría cambiar el sentido de una cláusula — así que en lugar de publicar una versión aproximada mantenemos un único texto auténtico. Si algo no queda claro, escríbenos y te lo explicamos en tu idioma.',
  },

  support: {
    title: 'Ayuda',
    intro: 'GameBuddy lo hace una sola persona, así que las respuestas vienen de un humano — normalmente en un par de días.',
    emailCta: 'Escribe a',
    tryFirst: 'Prueba esto primero',
    writingIn: 'Cuando escribas',
    writingInBody:
      'Ayuda muchísimo que incluyas tu nombre de usuario, el modelo de tu teléfono y la versión de Android, y qué esperabas que pasara en lugar de lo que pasó.',
    reportFaster:
      'Denunciar a alguien es más rápido desde la app. Una denuncia desde el perfil o la conversación lleva el contexto consigo y llega directa a la cola de moderación; un correo no.',
    legalRoute:
      'Los avisos legales, las reclamaciones y las solicitudes de protección de datos van a la dirección de abajo, para que no queden detrás de preguntas de soporte — tienen un plazo legal.',
    a1: { q: 'No me llegó el código de verificación', a: 'Mira primero en spam: casi siempre está ahí. El código caduca, así que si ha pasado un rato, vuelve a la pantalla de inicio de sesión y pide uno nuevo. Pedir uno nuevo anula el anterior, así que usa el correo más reciente.' },
    a2: { q: 'Mi mazo está vacío', a: 'Normalmente significa que los filtros son muy estrechos: un filtro de «en línea ahora» una noche tranquila puede no encajar con nadie de verdad. Quita los filtros del mazo y mira si vuelve la gente. Si el mazo está vacío sin filtros, escríbenos.' },
    a3: { q: 'Me he quedado sin likes', a: 'Las cuentas gratuitas tienen una cantidad diaria que se reinicia cada 24 horas. Gold quita el límite. También puedes ganar monedas en la app y gastarlas en extras sin pagar nada.' },
    a4: { q: 'Han rechazado mi foto', a: 'Cada foto se revisa antes de que la vea nadie más, y se rechaza cualquier contenido sexual — GameBuddy es +18, pero no es esa clase de app. Si crees que una foto se rechazó por error, escríbenos y la mirará una persona.' },
    a5: { q: 'Alguien me está acosando', a: 'Bloquéalo desde su perfil o desde la conversación: es inmediato y no se le avisa. Después denúncialo. Las denuncias se revisan en 24 horas. Si estás en peligro, contacta antes con emergencias; nosotros no somos un servicio de emergencia.' },
    a6: { q: 'He pagado y no he recibido lo que compré', a: 'Las compras las confirma la tienda y pueden tardar un momento. Si han pasado más de unos minutos, reinicia la app: eso vuelve a comprobar tus permisos. ¿Sigue sin aparecer? Escríbenos con el número de pedido del recibo de Google Play.' },
  },

  del: {
    title: 'Borrar tu cuenta',
    intro: 'Puedes cerrar tu cuenta de GameBuddy cuando quieras, sin pedir permiso a nadie. Aquí tienes cómo, y qué pasa exactamente con tus datos.',
    inApp: 'Desde la app',
    step1: 'Abre GameBuddy y ve a la pestaña Perfil.',
    step2: 'Toca la rueda de ajustes y baja hasta Cuenta.',
    step3: 'Elige Borrar mi cuenta y confirma con tu contraseña. Se pide la contraseña para que un teléfono robado no pueda destruir tu cuenta.',
    noApp: 'Si ya no tienes la app',
    noAppBody:
      'Escribe a soporte desde el correo de la cuenta. Borrar la app del teléfono no borra la cuenta, y eso importa: una cuenta cuya app se ha desinstalado sigue siendo visible para los demás.',
    formal:
      'Para una solicitud formal bajo el RGPD — acceso, rectificación o supresión — escribe a la dirección legal. Se responden en un mes, como exige la ley.',
    removedTitle: 'Qué se elimina de inmediato',
    removedNote: 'Tu perfil deja de aparecer en los mazos al instante, y la cuenta ya no puede iniciar sesión en ningún sitio.',
    r1: 'Tu nombre de usuario, tu correo y tu contraseña',
    r2: 'Tu foto, borrada del almacenamiento y no solo desvinculada',
    r3: 'Tu edad, tu país y tu género',
    r4: 'Los juegos, plataformas y estilos de juego de tu perfil',
    r5: 'Tus insignias y todo lo que hubieras comprado o equipado',
    r6: 'Cada dispositivo registrado para notificaciones',
    keptTitle: 'Qué no, y por qué',
    keptIntro:
      'Mejor decirlo claro que afirmar que todo desaparece: cada uno de estos puntos protege a alguien, y en dos casos ese alguien no eres tú.',
    k1: { what: 'Los mensajes que enviaste, dentro de conversaciones de otras personas', why: 'Se anonimizan en lugar de borrarse. La otra persona conserva el registro de una conversación en la que participó — que no es solo tuya para borrarla — y deja de poder atribuirse a ti.' },
    k2: { what: 'Los registros de compra', why: 'La legislación contable y fiscal obliga a conservarlos durante un tiempo. No se usan para nada más.' },
    k3: { what: 'El material relativo a una denuncia con la que se actuó', why: 'Se conserva para que quien fue expulsado por dañar a otro usuario no pueda borrar las pruebas borrando su cuenta.' },
    knowTitle: 'Algunas cosas que conviene saber',
    n1: 'El borrado no se puede deshacer. No hay periodo de gracia ni recuperación: si quieres volver, te registras de nuevo desde cero.',
    n2: 'Las monedas y Gold no se reembolsan. Los reembolsos los gestiona Google Play según su política, no nosotros.',
    n3: '¿Menor de 18? Escribe a la dirección legal y la cuenta se cierra y sus datos se borran, sin necesidad de contraseña.',
    fullDetail: 'Todo el detalle está en la política de privacidad.',
  },
};
