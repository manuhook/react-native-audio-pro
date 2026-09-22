# Fusion main / dv — 22 septembre 2026

La branche unique est `main`. La fusion conserve l'historique de `main`
(`2a23864`) et de `dv/10.1.3` (`a5899f1`), ainsi que les tags existants.
La version du fork devient `11.0.0-dv.1`, sans publication npm ni nouveau tag.

## Audit des changements de main

- `4f88971` : améliore la completion des seeks iOS. Conservé avec le bornage
  des extraits et les protections de génération de `PlaybackSeekGate` de dv.
- `036660e` : reconstruit le MediaBrowser après déconnexion. Combiné avec
  le nettoyage dv ; un mutex attend la connexion en cours au lieu de laisser
  une commande continuer avant sa fin.
- `789e7e1` : adapte la promotion du service Android au playback actif et
  traite les refus de démarrage en foreground. Conservé avec le nettoyage
  de session, d'ambiance et de plages de dv.
- `28f4f1c` : conserve les changements significatifs de position/durée lors
  du filtrage des états Android répétés.
- `dc2b523` : ignore les métadonnées de pistes obsolètes. Adaptation nécessaire :
  `TRACK_TRANSITIONED` doit autoriser l'adoption de la piste suivante de dv.
  Un test échouait avant cette adaptation et passe après.
- `4aa4e09` : minimum iOS 16. Compatible avec le checkout Divine Volonté
  consulté, dont la cible iOS est 16.4. Les consommateurs iOS < 16 ne sont
  plus pris en charge par cette version.
- Les versions beta, Yarn 4, Node et hooks sont intégrés. Le packaging Git
  de dv est conservé : `lib/` reconstruit et versionné, sans lifecycle
  `prepare`, sans workspaces ni packageManager dans le package distribué.
  `check` appelle `build`, et `example` utilise npm dans son propre dossier.
- Les avis d'archivage sont attribués au projet amont, pas au fork.
- Les mocks Jest vers un ancien dossier `.conductor/hong-kong` inexistant
  sont retirés ; ils empêchaient tous les tests de démarrer.

## Validation

- `yarn check` : lint, TypeScript, Bob, 5 suites / 68 tests réussis.
- Android / Java 17 : `:react-native-audio-pro:compileDebugKotlin` et
  `:react-native-audio-pro:testDebugUnitTest` réussis.
- Swift : les 4 scénarios autonomes `PlaybackSeekGateTests` réussissent ;
  `swiftc -frontend -parse ios/AudioPro.swift` réussit.
- Pas de compilation complète iOS ni de validation audio sur appareil.
  Les tests ne garantissent donc pas tous les comportements système
  (écran verrouillé, interruption, reprise et destruction de service).

L'application Divine Volonté référence toujours `v10.3.0-dv.4` : cette fusion
ne met pas automatiquement à jour sa dépendance.
