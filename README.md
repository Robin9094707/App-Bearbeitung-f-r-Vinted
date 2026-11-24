# Glanzstück ✨

Android-App, die aus Handyfotos verkaufsfertige Bilder für Vinted & Co. macht – komplett lokal auf dem Handy.

## Funktionen
- **Hintergrund entfernen** mit Google ML Kit (on-device) oder optionalem **HD-Modell (RMBG-1.4)** – Kanten werden per Guided Filter auf volle Auflösung geschärft, Farbsäume vom alten Hintergrund entfernt. Das Motiv selbst wird nicht verändert.
- **Neuer Hintergrund**: Farben, Verläufe, Studiolicht, unscharfes Original, eigenes Foto, transparent (PNG)
- **Schatten**: weich, Boden, schwebend, schräg (Lichteinfall), Spiegelung
- **KI-Radierer** (LaMa, optional) zum Wegradieren von Gegenständen und Fusseln, sonst eingebauter Füll-Algorithmus
- **Pinsel** zum Nachbessern der Freistellung (Entfernen/Wiederherstellen, mit Zoom)
- **Text & Sticker** („NEU“, „Wie neu“, „Größe M“ …), frei verschieb-, dreh- und skalierbar
- **Formate** 4:5, 3:4, 1:1, 9:16, 4:3, Original
- **Licht**: Helligkeit, Kontrast, Sättigung, Wärme
- **Speichern** direkt in die Galerie (Album „Glanzstück“), in der App als Projekt, oder **direkt an Vinted teilen**
- **Stapelverarbeitung** für bis zu 30 Fotos im gleichen Look
- Material 3 Expressive Design, animierter Start, Material You Farben

## Build
GitHub Actions baut bei jedem Push eine signierte APK (Workflow `Android APK`) und veröffentlicht sie als Release.

Lokal: `./gradlew :app:assembleRelease`

## Lizenzen der optionalen Modelle
- RMBG-1.4 von BRIA AI – CC BY-NC 4.0 (nur nicht-kommerzielle Nutzung)
- LaMa (Carve/LaMa-ONNX) – Apache 2.0
