[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C31OHWF2)
# Pastiera

Input method per tastiere fisiche (es. Unihertz Titan 2) pensato per rendere la digitazione piu veloce, con tante scorciatoie e personalizzazioni su file JSON.

## Panoramica veloce
- Barra di stato compatta con LED per Shift/SYM/Ctrl/Alt, barra varianti/suggerimenti e gesture swipe-pad per muovere il cursore.
- Layout multipli (QWERTY/AZERTY/QWERTZ, Greek, Cyrillic, Arabic, translit, ecc.) configurabili; import/export JSON dall'app. Frontend per la personalizzazione delle mappe diposibile su https://pastierakeyedit.vercel.app/
- Due pagine SYM lanciabili da touch o tasti fisici (emoji + simboli) riordinabili/disattivabili, con editor integrato e tanti caratteri Unicode nuovi.
- Backup/restore completo (impostazioni, layout, variazioni, dizionari), UI tradotta in piu lingue e controllo aggiornamenti GitHub.

## Digitazione e modificatori
- Pressione lunga: Alt+key di default o maiuscole; tempo configurabile.
- Shift/Ctrl/Alt in modalita one-shot o latch (doppio tap), doppio Shift per Caps Lock; opzione per cancellare Alt allo spazio.
- Multi-tap per tasti con varianti definite dal layout.
- Scorciatoie standard: Ctrl+C/X/V, Ctrl+A, Ctrl+Backspace, Ctrl+E/D/S/F o I/J/K/L per frecce, Ctrl+W/R per selezione, Ctrl+T per Tab, Ctrl+Y/H per Page Up/Down, Ctrl+Q per Esc (Tutte personalizzabili nella schermata Customize Nav)

## Navigazione e gesture
- Nav Mode: doppio Ctrl fuori dai campi di testo per usare ESDF/IJKL/T come frecce/Tab; rimappabile per ogni tasto alfabetico dal menu "Nav Mode".
- Barra varianti come swipe pad: trascina per muovere il cursore con soglia regolabile; hint automatico se non ci sono varianti.
- Helper di selezione testo (Ctrl+W/R) e annulla per gli auto-replace.
- Launcher shortcuts: nel launcher premi una lettera per aprire/assegnare un'app.
- Power shortcuts: premi SYM (timeout 5s) e poi una lettera per usare le stesse scorciatoie ovunque, anche fuori dal launcher.
- Gear sulla barra della tastiera per aprire le impostazioni senza lasciare il campo di testo.

## Layout tastiera
- Layout inclusi: qwerty, azerty, qwertz, greek, arabic, russian/armenian phonetic, translit, piu mappe Alt dedicate al Titan 2.
- Conversione layout: selezione del layout dalla lista abilitata (configurabile).
- Supporto multi-tap e mapping per caratteri complessi nei layout.
- Import/export da file JSON direttamente dall'app, anteprima grafica e gestione lista (abilita/disabilita, elimina).
- Le mappe sono salvate in `files/keyboard_layouts` e modificabili anche a mano.

## Simboli, emoji e variazioni
- Due pagine SYM (emoji + simboli) touch: puoi riordinarle/attivarle, chiusura automatica dopo input, keycaps personalizzabili.
- Editor SYM in-app con griglia emoji, picker Unicode e seconda pagina piena di nuovi caratteri.
- Barra variazioni sopra la tastiera: mostra accenti/varianti della lettera appena digitata o set statici (utility/email) quando serve.
- Editor variazioni dedicato per sostituire/aggiungere varianti via JSON o picker Unicode; barra statica opzionale.
- Modalita emoji/simboli accessibile anche toccando direttamente la barra.

## Suggerimenti e autocorrezione
- Auto-replace opzionale su spazio/enter; auto-cap smart dopo ., !, ?.
- Dizionario utente con frequenza/priorita e ricerca; voci personali sempre in cima.
- Editor autocorrezioni per lingua, ricerca rapida, e set globale "Ricette Pastiera" valido per tutte le lingue.
- Modalita debug/esperimenti disattivabile; funzioni smart spente automaticamente in password/email dove non servono.

## Comfort e input extra
- Doppio spazio -> punto+spazio+maiuscola; swipe a sinistra sulla tastiera per cancellare parola (Titan 2).
- Tasto SYM per doppia pagina emoji/simboli; auto close SYM configurabile.
- Scorciatoia Alt+Ctrl (opzionale) per avviare il microfono Google Voice Typing; microfono sempre disponibile sulla barra varianti.
- Stato compatto della barra per occupare poco spazio verticale. Con tastiera on screen off dal selettore ime occupa ancora meno spazio grazie alla modalità Pastierina.
- UI tradotta (it/en/de/es/fr/pl/ru/hy) e tutorial iniziale.

## Backup, update e dati
- Backup/restore da UI in formato ZIP: include preferenze, layout personalizzati, variazioni, sym/ctrl map, dizionari utente.
- Ripristino unisce le variazioni salvate con quelle di default per non perdere chiavi nuove.
- Controllo aggiornamenti GitHub integrato all'apertura delle impostazioni (con possibilita di ignorare una release).
- File personalizzabili in `files/`: `variations.json`, `ctrl_key_mappings.json`, `sym_key_mappings*.json`, `keyboard_layouts/*.json`, dizionari utente.

## Installazione
1. Compila l'APK o installa una build esistente.
2. Impostazioni Android -> Sistema -> Lingue e immissione -> Tastiera virtuale -> Gestisci tastiere.
3. Abilita "Pastiera Physical Keyboard" e selezionala dal selettore input quando digiti.

## Configurazione rapida
- Tempi pressione: Impostazioni -> Keyboard timing (long press, multi-tap).
- Testo: auto-maiuscole (inizio frase e post-punteggiatura), doppio spazio, cancella Alt su spazio, swipe-to-delete, mostra tastiera, scorciatoia voce Alt+Ctrl.
- Autocorrezione: toggle suggerimenti/accenti/auto-replace, lingue attive, dizionario utente, Ricette Pastiera, debug.
- Personalizzazione: layout tastiera (scelta, import/export, ciclo), SYM/emoji (mapping, ordine pagine, chiusura automatica), variazioni, Nav Mode mapping, launcher/power shortcuts, sensibilita swipe cursore.
- Avanzate: backup/restore, test IME, info build.

## Requisiti
- Android 10 (API 29) o superiore.
- Dispositivo con tastiera fisica (profilato su Unihertz Titan 2, adattabile via JSON).

## Build
Progetto Android in Kotlin/Jetpack Compose. Apri in Android Studio e builda normalmente.
