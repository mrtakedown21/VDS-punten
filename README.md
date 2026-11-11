# VDS punten — Android app (offline)

**Een apart project van SNAP-M** voor fotopunten rond een winkelkeuring.

- Vaste mappen WB1 t/m WB5, GO, GU, Kassa, Servicebalie en Uitzonderlijke locaties
- Eigen mappen toevoegen, hernoemen en verwijderen
- Foto met Samsung-camera vastleggen, datum/tijd automatisch
- Vrijblijvende omschrijving en grote fotogalerij
- Foto's blijven OPEN totdat je op Gedaan drukt; Gedaan blijft bewaard
- Prullenbak, herstellen, definitief verwijderen (bevestiging Yippie / Nee)
- Meerdere foto's selecteren en samen afhandelen
- Dashboard met open/gedaan/procent en één algemene keuringsdatum
- Word `.docx` export met iedere locatie vanaf een nieuwe pagina, foto's/datum/omschrijving
- ZIP-back-up en herstel van alle lokale foto's en instellingen
- Geen internetverkeer of externe diensten binnen de app

## GitHub Actions

1. Maak op GitHub een **nieuwe, lege repository `VDS-punten`**. Deze app is volledig gescheiden van SNAP-M.
2. Zet de **inhoud** van deze ZIP in de root van die repository (niet alleen het ZIP-bestand).
3. Open **Actions → VDS punten Android APK bouwen → Run workflow** (of push naar `main`).
4. Download onder **Artifacts** de ZIP `VDS-punten-Android-APK`.
5. Pak uit op Samsung en installeer `app-debug.apk`.

Voor het bouwen is internet nodig; na de installatie kan de app volledig offline worden gebruikt.

## Belangrijk voor behoud van foto's

Foto's en gegevens staan in de privé-opslag van de Android-app. Verwijderen van de app kan deze gegevens wissen. Gebruik regelmatig **Word & back-up → Back-up maken**. De ZIP kan later op hetzelfde of een nieuw apparaat met VDS punten worden teruggezet.

## Teststatus

De browserinterface kan zonder internet getest worden door `app/src/main/assets/index.html` te openen in een browser. Camera, native foto-opslag, Word en back-up werken uitsluitend in de gebouwde Android APK. De APK moet op een echte Samsung-tablet worden getest.

## Technisch

Native Java / Android WebView, offline HTML/CSS/JS, Android `ACTION_IMAGE_CAPTURE` via FileProvider, WordprocessingML `.docx` met Java ZipOutputStream, `ACTION_CREATE_DOCUMENT` voor export, `ACTION_OPEN_DOCUMENT` voor import. Geen server, geen account, geen internet-permission.
