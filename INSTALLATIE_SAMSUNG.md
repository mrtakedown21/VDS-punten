# VDS punten op je Samsung — alleen je tablet nodig

Dit is een **nieuw, los programma**, niet dezelfde GitHub-repository als SNAP-M.

## De eerste APK bouwen via GitHub Actions

1. Download `VDS_punten_Android_Project.zip` op je Samsung via Drive of rechtstreeks.
2. Maak op GitHub een **nieuwe private repository** `VDS-punten` met README.
3. Upload het ZIP-bestand in de hoofdmap via **Add file → Upload files → Commit changes**.
4. Open **Code → Codespaces**, open een bestaande of maak een nieuwe Codespace.
5. Open Terminal en typ de volgende commando's afzonderlijk:

```bash
git pull origin main
unzip -o VDS_punten_Android_Project.zip
git add .
git commit -m "VDS punten Android-app toevoegen"
git push origin main
```

6. Open **Actions** in jouw repository en klik de nieuwe groene uitvoering `VDS punten Android APK bouwen` aan.
7. Scroll naar **Artifacts**, download `VDS-punten-Android-APK`.
8. Open Mijn bestanden → Downloads → ZIP → Extraheren → `app-debug.apk` → Installeren.

### Een latere update

Upload het nieuwe update-ZIP in dezelfde repository. Codespaces:

```bash
git pull origin main
unzip -o VDS_punten_Update.zip
git add .
git commit -m "VDS punten update"
git push origin main
```

Daarna bouwt GitHub weer een APK. Let op dat de app-identiteit en ondertekening gelijk blijven om bestaande gegevens te behouden. Bij een conflict: niet zomaar de oude app verwijderen; maak eerst een back-up.

## Dagelijks gebruik

- Linkerkolom: WB1–WB5, GO, GU, Kassa, Servicebalie, Uitzonderlijke locaties.
- `+` bij LOCATIES voor een eigen map, daarna kun je die hernoemen of verwijderen.
- Foto maken: camera opent, foto wordt lokaal opgeslagen met datum en tijd.
- Gedaan: foto verhuist naar de Gedaan-lijst, blijft daar staan.
- Prullenbak: kan terugzetten of permanent leegmaken; permanent is onomkeerbaar.
- Instellingen: kies één keuringsdatum voor de hele winkel.
- Word & back-up: exporteer een `.docx` met locatiekoppen en foto's, en maak regelmatig een ZIP-back-up.

**Deze app werkt volledig offline na installatie.** Alleen het GitHub-bouwen vraagt internet.
