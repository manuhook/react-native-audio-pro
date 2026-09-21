# Plages audio natives

`AudioProTrack.startMs` et `endMs` sont deux bornes optionnelles, entières, en
millisecondes absolues dans la source. `startMs` vaut implicitement zéro ; `endMs`
doit être strictement supérieur au début. La validation refuse une plage invalide
avant d'appeler le natif, pour éviter de jouer accidentellement le média entier.

```ts
AudioPro.play({ ...track, startMs: 30000, endMs: 45000 });
AudioPro.setNextTrack({ ...nextTrack, startMs: 12000, endMs: 20000 });
```

Les valeurs `position`, `startTimeMs` et `seekTo()` gardent leur repère absolu.
`duration` reste la durée totale de la source. Tous les seeks de l'API sont bornés
à la plage. À la fin d'un extrait isolé, `TRACK_ENDED.position` contient sa borne
effective ; `TRACK_ENDED.duration` reste la durée de la source. Le lecteur arrêté
se replace au début de la plage.

Sur Android, un message temporel Media3 s'exécute dans le service natif et arrête
ou enchaîne la lecture sans attendre JS. La piste pré-enfilée porte également ses
bornes ; `TRACK_TRANSITIONED` transporte la piste suivante et son début absolu.
La timeline source est conservée pour protéger les usages de durée et de
position existants. Le message passe par le looper du lecteur : ce mécanisme ne
promet pas une découpe à l'échantillon près. À la fin naturelle d'un média entier,
la transition vers un extrait vise son début, avec correction native de position
si l'enchaînement automatique est survenu avant le message.

Sur iOS, `AVPlayerItem.forwardPlaybackEndTime` impose la fin native. Le seek
initial se termine avant le démarrage audible. `setNextTrack()` reste ignoré sur
cette plateforme : l'application reçoit `TRACK_ENDED` puis démarre le suivant.
Cette version n'ajoute ni `AVQueuePlayer` ni pré-enfilage iOS.

## Vérification sur appareil avant publication

- Lire 30–45 s d'une source plus longue ; vérifier la première et dernière phrase,
  les positions absolues, la durée source et un seul événement de fin.
- Reprendre à 35 s ; chercher avant 30 s puis après 45 s depuis l'app et l'écran
  verrouillé ; tester pause/reprise et changement de vitesse pendant le chargement.
- Android : enchaîner média entier → extrait → extrait → média entier ; modifier
  ou effacer le suivant, interrompre rapidement par `play()`/`stop()`/`clear()`.
- Refaire avec HLS, écran verrouillé et application en arrière-plan ; mesurer le
  dépassement éventuel de la borne Android sous charge.
- iOS : vérifier l'arrêt natif en arrière-plan ; le relais JS vers le suivant
  demeure un comportement distinct du pré-enfilage Android.

## Intégration du fork

Le travail est basé sur `v10.3.0-dv.3` (`5a5b209`). Reporter les sources et `lib/`
reconstruit sur `dv/10.1.3`, vérifier le diff et les scénarios ci-dessus, incrémenter
la version du fork, committer les artefacts générés, taguer puis mettre à jour la
référence git dans l'application. Aucun commit, tag ou push n'est nécessaire pour
la revue locale. Un rebuild natif de l'application est indispensable.
