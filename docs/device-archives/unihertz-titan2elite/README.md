# Unihertz Titan 2 Elite

Dieses Verzeichnis sammelt Referenzmaterial zum Unihertz Titan 2 Elite. Solange noch kein Testgerät verfügbar ist, sind Fotos und daraus abgeleitete Maße ausdrücklich nur Näherungen und keine Messungen am Gerät. Ein Debug-Export eines final ausgelieferten Geräts liegt für die Geräteerkennung vor.

## Display

Laut Hersteller verwendet das Gerät ein 4,03-Zoll-AMOLED mit 1080 × 1200 Pixeln und 401 ppi. Die Referenzfotos zeigen stark gerundete untere Displayecken. Der sichtbare Bogen entspricht in der normalisierten 1080 × 1200-Schablone näherungsweise einem Radius von 96 px. Die [SVG-Schablone](titan2elite-display-overlay.svg) markiert diesen Bogen und die im Emulator bei 400 dpi ermittelten 16-px-Führungslinien für die untere Pastiera-Zeile.

Ein final ausgeliefertes Gerät (Titan 2 Elite_V02.00.04) meldet über `WindowInsets.getRoundedCorner` an allen vier Ecken einen Radius von 100 px. Ist die Option aktiv, rundet Pastiera die unteren Ecken der gesamten IME-Chrome per Outline-Clip mit dem gemeldeten Radius ab, sodass der Hintergrund der Statusleiste dem Displaybogen folgt. Die Zeileninhalte bleiben bewusst über die volle Displaybreite und werden nicht eingerückt. Fehlt der gemeldete Radius, wird ein am Gerät abgeleiteter 50-dp-Fallback verwendet.

## Emulator-Abnahme

Der API-36-Test-AVD verwendet 1080 × 1200 Pixel. Der Stock-Emulator kann die zusätzliche IME-Caption-Bar nicht wie die Unihertz-Systemoption ausblenden. Für vergleichbare Screenshots wird die Rohhöhe deshalb um die Android-Dimension `navigation_bar_frame_height` von 48 dp erhöht und anschließend bei y = 1200 beschnitten. Das entspricht 108 px bei 360 dpi, 120 px bei 400 dpi und 132 px bei 440 dpi. So endet die effektive 1080 × 1200-Aufnahme dort, wo auf dem Gerät bei aktivierter Option „Hide IME Caption Bar“ der Pastiera-Inhalt endet.

Die Abnahmematrix kombiniert 360, 400 und 440 dpi jeweils mit den Schrift-Skalierungen 85 %, 100 % und 115 %.

## Reproduzierbare Theme-Geometriematrix

Die integrierten Hardware-Themes lassen sich gegen den kompensierten 1080-x-1200-Viewport wiederholen mit:

```bash
scripts/capture-titan2elite-theme-geometry.sh emulator-5560
```

Das Skript wählt jedes Theme über die normale Einstellungsoberfläche aus, fokussiert das Pastiera-Testfeld mit
Maestro, nimmt den 1080-x-1320-Stock-AVD auf und schneidet die berechnete 48-dp-/120-px-Caption-Bar-Kompensation
ab. Die Bilder prüfen damit insbesondere unterschiedliche Höhen, Rundungen, Paddings und Gaps, ohne einen
Debug-only-Pfad zum Schreiben von Einstellungen einzuführen.

## Modifier-LEDs

Bei aktivierter Rundungsoption folgen die LED-Segmente konzentrischen Bögen entlang der unteren Displayecken. Die beiden Zeilen behalten ihre Farben und Zustände; ihre Segmentbreiten werden entlang der Kontur verteilt. Die LED-Fläche reserviert mindestens die Höhe des größeren Eckenradius, damit die äußeren Segmente sichtbar nach oben laufen können. Ohne Rundungsoption bleibt die bisherige flache Darstellung erhalten.

Die LED-Geometrie verwendet normalisierte X-/Y-Koordinaten sowie Breite und Höhe pro Segment. Das bisherige
einzeilige Layout bleibt das Defaultprofil. Das Titan-2-Elite-Profil bildet dagegen zwei physische Tastenzeilen ab:
Alt links und Sym rechts oben, Shift gekoppelt an beiden äußeren unteren Positionen sowie Ctrl unten rechts über
der weiter innen liegenden Fn-Taste. Die T2E-Balken verwenden die breiten, auf dem Foto sichtbaren Sechstelzonen
statt der schmaleren Tastenkappenbreiten, damit sie an den gerundeten Displayecken lesbar bleiben. Mehrere Segmente
dürfen denselben logischen Zustand darstellen; dadurch kann ein späteres Geräteprofil Positionen oder
Tastenbreiten ändern, ohne die Zustands- oder Renderlogik anzupassen.

## Geräteerkennung

Das finale Titan 2 Elite meldet wie ein Titan 2 unter anderem `model=Titan 2`, `device=Titan_2`, `product=Titan_2_EEA` und einen Titan-2-Fingerprint. Verlässliche Unterscheidungsmerkmale sind `board=G72BoardV1` und ein `build_display`, das mit `Titan 2 Elite` beginnt. Ein roher Build-Fingerprint allein darf daher weder die Geräteerkennung noch einen geräteübergreifenden Settings-Import steuern.

## Referenzen

- [Pastiera-Statusleiste auf einem Titan 2 Elite](2026-07-20-pastiera-statusbar-reference.png), zugesandtes Foto vom 20. Juli 2026, 1176 × 1416 Pixel, SHA-256 `4278b3599317acc733f532eb2f572602de49410f1eb6a759280e1ce764c7ea21`
- [Herstellerseite mit technischen Daten](https://www.unihertz.com/en-de/products/titan-2-elite)
