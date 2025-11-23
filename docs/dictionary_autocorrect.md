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
- Digitazione normale in italiano: suggerimenti in tempo reale, nessun lag percepito.
- Multi-tap: comportamento invariato, suggerimenti aggiornati sui commit effettivi.
- Nav mode in launcher: routing invariato, buffer resettato all'entrata/uscita.
- Spazio/enter ancora committano testo; con auto-replace attivo sostituzione atomica e undo tramite stack esistente.
- Undo immediato dopo autocorrezione: la parola originale torna correttamente.
