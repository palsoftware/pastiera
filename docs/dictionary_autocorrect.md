# Sistema di autocorrezione basato su dizionario (IME fisica)

## Architettura
- **SuggestionController** (core/suggestions): orchestration layer, unico punto di contatto con il router. Aggiorna il buffer della parola corrente, calcola i suggerimenti, applica l'auto-replace opzionale e pubblica callback `suggestionsListener`.
- **CurrentWordTracker**: mantiene `currentWord` in sync con i commit testo del router. Reset su spazi/enter/punteggiatura, cambio campo, movimenti cursore, entrata/uscita nav mode.
- **DictionaryRepository**: carica il dizionario locale leggero da asset (`common/dictionaries/it_base.json`), indicizza per prefisso (cache a 3 caratteri) e un indice normalizzato per match accenti. Incorpora le parole del dizionario utente tramite `UserDictionaryStore`.
- **SuggestionEngine**: ricerca candidati per prefisso e calcola Levenshtein con bound 2 (più stripping accenti). Gestisce errori comuni (swap/missing/doppie) tramite distanza edit + normalizzazione.
- **AutoReplaceController**: opzionale, sostituisce la parola su spazio/enter/punteggiatura se `autoReplaceOnSpaceEnter` è attivo e il suggerimento top ha distanza ≤ soglia.
- **UserDictionaryStore**: persiste parole aggiunte dall'utente (JSON in SharedPreferences), conserva frequenza e recency. Le parole utente hanno priorità nel ranking.

## Flusso dati
1. Il router committa caratteri → `SuggestionController.onCharacterCommitted()` aggiorna `CurrentWordTracker` e richiama `suggestionsListener`.
2. Spazio/enter/punteggiatura → `onBoundaryKey()` valuta auto-replace e resetta il tracker.
3. Movimento cursore, cambio campo, nav mode → `onContextReset()/onCursorMoved()/onNavModeToggle()` azzerano il buffer per evitare correzioni errate.
4. La UI si aggancia a `suggestionsListener` (o a `currentSuggestions()`) per mostrare la riga di suggerimenti senza logica hardcoded.

## Performance
- Cache per prefisso (3 caratteri) per ridurre il set di candidati.
- Indice normalizzato per match accentati senza calcolare la normalizzazione ad ogni lookup.
- Levenshtein bounded (max 2) con early exit → O(k * d) con k lunghezza parola e d distanza massima.
- User dict in memoria (mappa) con persist a batch per evitare I/O in hot path.

## Test manuali suggeriti

### Abilitazione rapide
1. Apri **Impostazioni → Auto-correzione** dalla schermata dell'app (categoria già esistente).
2. Attiva i toggle desiderati:
   - **Suggerimenti** / **Accenti** per vedere le proposte in tempo reale.
   - **Auto-replace su spazio/enter** per applicare la correzione automatica.
3. (Opzionale) Aggiungi una parola utente dal pannello "Dizionario utente" per verificare la precedenza rispetto al dizionario base.

### Checklist di test manuali
- **Digitazione base ITA (campo testo qualunque)**
  - Digita parole corrette e osserva i suggerimenti; nessun lag percepito.
  - Premi spazio/enter: il testo viene committato normalmente; con auto-replace attivo la parola viene sostituita dal top suggestion solo se non nel dizionario.
- **Errori comuni e accenti**
  - Digita "perche" → verifica che appaia "perché" fra i suggerimenti; con auto-replace attivo deve correggere su spazio.
  - Prova doppie/mancanti ("cosi"/"comincia" → "comincia"); controlla che la distanza ≤2 venga corretta.
- **Multi-tap**
  - Usa sequenze multi-tap per caratteri speciali: il ritmo/temporizzazioni restano invariati, i suggerimenti arrivano solo quando il commit avviene.
- **Nav mode / routing launcher**
  - Entra ed esci dalla nav mode: il buffer currentWord si resetta e il routing dei tasti resta invariato.
- **Movimento cursore e cambio campo**
  - Sposta il cursore manualmente o cambia campo di input: il buffer si svuota e non avviene auto-replace.
- **Dizionario utente**
  - Aggiungi una parola personalizzata → verifica che appaia come primo suggerimento e prevalga sulle proposte base.
- **Undo**
  - Dopo un auto-replace, usa l'undo già presente: la parola originale deve tornare integralmente.
- **Stress/performance**
  - Digitazione veloce (10–15 parole) senza pause: nessun lag, suggerimenti aggiornati ad ogni lettera.
