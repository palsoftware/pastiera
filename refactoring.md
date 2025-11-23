# Pastiera IME Refactoring Log

Questo documento traccia le fasi del refactoring modulare dell’IME fisica di Pastiera, con l’obiettivo di ridurre il “god class” `PhysicalKeyboardInputMethodService`, migliorare il riuso e facilitare futuri test automatizzati. Ogni fase è stata pensata per essere behaviour-preserving e accompagnata da build/test lato utente.

# Incremental refactor plan (behavior-preserving)

# Establish core data + repositories (no behavior change).

Extract KeyMappingLoader, layout managers, and variation map loading into data/ classes with interfaces.

Keep current JSON/schema untouched; service keeps using same loaders via new wrappers.

# Isolate modifier and nav-mode state.

Create ModifierStateController that owns shift/ctrl/alt states and sync helpers; replace direct properties with delegation while keeping public behavior identical.

Wrap NavModeHandler into NavModeController to manage latch state, notifications, and entry/exit rules.

# Introduce InputContextState snapshot. _(Completato vedi Fase 5)_

Centralize detection of numeric/password/restricted fields and shouldDisableSmartFeatures computation to reduce duplication.

# Extract SYM/variation handling. (completato)

Move SYM page state, variation activation, and auto-close logic into SymLayoutController, exposing intents like onSymKey, onVariationRequested, onSymMappingResolved.

# Split UI responsibilities. _(Fase 6: Status UI completata, resto da fare)_

Implement CandidatesBarController to replace empty CandidatesViewManager, and KeyboardVisibilityController to manage view creation/show/hide; service calls these instead of direct UI mutations.

# Create InputEventRouter.

Add router that decides between nav-mode handling, launcher shortcuts, text input flow, and delegates to controllers; service’s onKeyDown/Up/LongPress/Motion delegate to router.

# Extract text input pipeline.

Move autocorrect undo, double-space period, auto-capitalization triggers, and backspace handling into TextInputController + AutoCorrectionManager, preserving sequence and preference checks.

# Thin the IME service.

Rename to PastieraImeService (or keep name) and limit it to lifecycle wiring, dependency setup, and delegating events to controllers; ensure settings listener and broadcasts are reattached without logic change.

# Cleanup and verification.

Remove dead/unused artifacts (e.g., empty CandidatesViewManager) once replacements are wired.

Run regression checks (same unit/UI tests) after each step to confirm behavior parity.


## Stato attuale (novembre 2025)
- ✅ **Dati e repository**: i loader JSON e i repository (`data/`) sono usati da servizio, schermate e manager (fase 1).
- ✅ **Controller core**: `ModifierStateController`, `NavModeController`, `SymLayoutController`, `TextInputController`, `AutoCorrectionManager` e `InputContextState` sono cablati nel servizio (fasi 2–5).
- ✅ **UI status bar**: `StatusBarController` con `LedStatusView`/`VariationBarView` gestisce modalità FULL e CANDIDATES (fase 6).
- ⚠️ **Variazioni/SYM**: `variationsMap`, `lastInsertedChar`, `availableVariations` e le istanze di `StatusBarController` vivono ancora in `PhysicalKeyboardInputMethodService`.
- ⚠️ **Gestione viste IME**: `onCreateInputView`, `onCreateCandidatesView`, `onEvaluateInputViewShown` e `ensureInputViewCreated` sono logica ad-hoc del servizio; manca un `KeyboardVisibilityController`.
- ⚠️ **Instradamento eventi**: `onKeyDown/Up/LongPress` è una god function >700 righe; non c’è ancora un `InputEventRouter`.
- ⏳ **Snellimento servizio**: la classe principale resta >1.700 righe; rename/estrazione a `PastieraImeService` non ancora eseguiti.

## Fase 1 – Data/Repository Layer _(Completato)_
**Obiettivo:** isolare accesso a JSON/layout/variations dal servizio IME.

- **Nuove classi**:  
  - `data/layout/LayoutMappingRepository`, `JsonLayoutLoader`, `LayoutFileStore`  
  - `data/variation/VariationRepository`  
  - `data/mappings/KeyMappingLoader` (spostato fuori da `inputmethod`)
- **Principali cambiamenti**:  
  - `PhysicalKeyboardInputMethodService` ora carica layout, nav-mode mappings e variazioni tramite i repository (nessuna logica JSON nel servizio).  
  - `KeyboardLayoutSettingsScreen`, `NavModeSettingsScreen`, `SettingsManager` aggiornati a usare i nuovi moduli.  
  - `AltSymManager` e `AutoCorrector` continuano a funzionare ma consumano le nuove API.
- **Testing suggerito**: cambio layout, import/export JSON, long-press/auto-correct con keyboard fisica.

---

## Fase 2 – Modifier & Nav Controllers _(Completato)_
**Obiettivo:** centralizzare stato Shift/Ctrl/Alt e nav-mode per ridurre duplicazione e accoppiamento.

- **`core/ModifierStateController`**  
  - Mantiene gli stati `Shift/Ctrl/Alt` (pressed/latch/one-shot/physically pressed) e sincronizza automaticamente con `AutoCapitalizeHelper`.  
  - Espone snapshot per la Status Bar e funzioni `handleShift/Ctrl/AltKeyDown/Up`.

- **`core/NavModeController`**  
  - Incapsula NavModeHandler, latch state & notifiche.  
  - Decide se una key è nav-mode, gestisce DPAD mapping e conserva la latched-state tra servizi/UI.

- **`PhysicalKeyboardInputMethodService`**  
  - Ridotto ai wiring: delega key handling a `ModifierStateController` e `NavModeController`.  
  - Status bar aggiornata via snapshot, niente più accesso diretto ai campi `ShiftState/CtrlState`.

- **Testing suggerito**: double-tap shift/caps lock, ctrl latch + nav mode (fuori da text field), alt latch/one-shot, status bar LED.

---

## Fase 3 – SYM Layout Controller _(Completato)_
**Obiettivo:** isolare tutta la logica SYM (pagine emoji/simboli, auto-close, restore, UI data).

- **`core/SymLayoutController`**  
  - Gestisce `symPage`, persistenza, restore da `SettingsManager`, auto-close rules e snapshot per UI.  
  - Espone `handleKeyWhenActive` con `SymKeyResult` per distinguere `CONSUME`, `CALL_SUPER`, `NOT_HANDLED`.  
  - Fornisce `emojiMapText()` e `currentSymMappings()` per StatusBar/Candidates.

- **Priorità Alt/Ctrl (correzioni successive)**  
  - **Alt**: chiude sempre SYM all’attivazione per permettere agli Alt mappings di funzionare subito.  
  - **Ctrl**: bypassa la griglia SYM; con latch/pressed, i Ctrl shortcuts (es. DPAD, copy/paste) hanno precedenza, mantenendo SYM aperto finché Ctrl resta attivo.
  - Inserimento SYM via tastiera fisica chiude il layout quando auto-close è attivo; gli inserimenti via touchscreen lo lasciano aperto.

- **UI**: la Status Bar riceve ora `emojiMapText`/`symMappings` direttamente dal controller, mantenendo il layout LED coerente fra input e candidates view.

- **Testing suggerito**:  
  1. Cycle SYM (0→emoji→symbols) e verifica persistenza/restore.  
  2. Inserisci simboli via tastiera con Alt/Ctrl attivi: Alt deve chiudere SYM, Ctrl non deve inserire simboli.  
  3. Inserisci gli stessi simboli via touchscreen: SYM resta aperto.  
  4. Verifica auto-close su Back/Enter/Alt e dopo commit fisico (quando l’opzione è abilitata).

---

## Fase 4 – Text Pipeline Refactor _(Completato)_
**Obiettivo:** spostare auto-correct, double-space, auto-cap e undo fuori dal servizio e in controller dedicati.

- **`core/TextInputController`**  
  - Gestisce double-space→“. ”, auto-cap dopo punteggiatura e Enter; usa `ModifierStateController` per lo Shift one-shot e centralizza il timing (50 ms) nell’`AutoCapitalizeHelper`.
- **`core/AutoCorrectionManager`**  
  - Incapsula undo via Backspace, correzioni su spazio/punteggiatura e accettazione/clear dei reject su altri tasti.
- **`PhysicalKeyboardInputMethodService`**  
  - Deleghe per oltre 200 righe di logica text pipeline; ora richiama solo i metodi dei controller e aggiorna la status bar via callback.
- **Auto-cap delay**  
  - Il servizio non passa più `delayMs` personalizzati: la tempistica è centralizzata nell’helper (default 50 ms) così futuri tweak richiedono una modifica sola.

- **Testing suggerito**:
  1. Digita una parola autocorretta e premi Backspace subito → deve ripristinare la parola originale.
  2. Double-space produce “. ”, abilita Shift one-shot e non introduce ritardi percepibili.
  3. Auto-correction/accept/reset continua a funzionare su spazio/punteggiatura e altri tasti.
  4. Campi con smart features disabilitate ignorano tutte le funzioni sopra.

---

## Fase 5 – Input Context Snapshot _(Completato)_
**Obiettivo:** avere un’unica fonte di verità per lo stato del campo attivo (editable/numeric/password/restricted) e per il flag `shouldDisableSmartFeatures`.

- **`core/InputContextState`**  
  - Nuovo snapshot immutabile creato da `EditorInfo` che espone `isEditable`, `isReallyEditable`, `isNumericField` e il motivo di restrizione (password/URI/email/filter).  
  - Fornisce helper `shouldDisableSmartFeatures` così il servizio non ricalcola più manualmente le bitmask dell’`inputType`.
  - `isNumericField` considera sia `TYPE_CLASS_NUMBER` sia `TYPE_CLASS_PHONE`, quindi anche i campi telefono applicano subito le mappature Alt.
- **`PhysicalKeyboardInputMethodService`**  
  - Sostituiti i campi `isNumericField` e `shouldDisableSmartFeatures` con getter che leggono dallo snapshot.  
  - Rimossi `checkFieldEditability` e la vecchia funzione `shouldDisableSmartFeatures(info)`, ora la logica sta tutta in `InputContextState`.  
  - Introdotta `enforceSmartFeatureDisabledState()` per applicare/hide candidates e variazioni in modo coerente sia in restart sia in start “fresh”.

- **Testing suggerito**:
  1. Campi password/URI/email/filter devono disattivare status LED delle variazioni e non mostrare candidates.  
  2. Campi numerici devono continuare a committare i caratteri Alt direttamente (nessun crash in long-press).  
  3. Passare rapidamente da un campo testo “normale” a uno password non deve lasciare stato sporco nella status bar.

---

## Fase 6 – Status UI Split _(Completato)_
**Obiettivo:** separare la Status Bar in componenti modulari per riuso (anche nella sola visuale candidates) e ridurre il codice monolitico.

- **`inputmethod/ui/LedStatusView`**  
  - Incapsula la creazione/aggiornamento degli indicatori Shift/SYM/Ctrl/Alt.  
  - `StatusBarController` delega ora gli update dei LED allo snapshot, evitando duplicazioni di colore/stato.

- **`inputmethod/ui/VariationBarView`**  
  - Gestisce variazioni, overlay swipe, microfono e pulsante Settings con animazioni dedicate.  
  - Espone callback per `onVariationSelected`/`onCursorMoved` e lancia direttamente `SpeechRecognitionActivity`/`SettingsActivity`.

- **`StatusBarController`**  
  - Supporta il nuovo `Mode` (`FULL` vs `CANDIDATES_ONLY`).  
  - `PhysicalKeyboardInputMethodService` usa `Mode.CANDIDATES_ONLY` per la sola candidates view: vengono mostrati solo i LED e, quando richiesto, il layout SYM (nessuna barra variazioni/microfono).  
  - Il service continua a delegare gli snapshot, ma con una classe >300 righe più corta e priva di logica duplicata per variazioni/LED.

- **Testing suggerito**:
  1. Modalità completa: variazioni + microfono continuano a reagire a swipe/click; aprire i Settings e avviare il microfono.  
  2. Modalità “solo candidates” (disabilitando la tastiera virtuale dall’IME selector): verificare che compaiano solo LED e griglia SYM quando attivata.  
  3. Toggle rapido SYM ↔ variazioni in entrambe le modalità per individuare glitch di animazione.

---

## Fasi successive (pianificate – **da fare**)
7A. **UI surface controllers**
   - Estrarre `CandidatesBarController` (rimpiazza `inputmethod/CandidatesViewManager.kt`) per possedere `variationsMap`, stato SYM e aggiornare le due `StatusBarController`.
   - Introdurre `KeyboardVisibilityController` che gestisce creazione e visibilità delle viste IME (`onCreateInputView`, `onCreateCandidatesView`, `onEvaluateInputViewShown`, `ensureInputViewCreated`, `setCandidatesViewShown`, `requestShowSelf`).
7B. **Input Event Router**
   - Implementare `InputEventRouter` (nuovo pacchetto `inputmethod/events/`) per smistare `onKeyDown/Up/LongPress/Motion` tra nav-mode, shortcut launcher, text pipeline, SYM/Alt e fallback al sistema.
7C. **Service slim down & cleanup**
   - Rinominare o confermare `PastieraImeService`, lasciandolo limitato al lifecycle wiring e ai listener.
   - Rimuovere artefatti morti (`CandidatesViewManager`, duplicazioni) e assicurare che i nuovi controller espongano hook per test/regressioni.

Ogni fase continuerà a essere accompagnata da `assembleDebug` e test manuali suggeriti per garantire parità funzionale.

---

## Checklist di regressione suggerita
- Cambio layout fisico, import/export JSON.
- Shift/Ctrl/Alt double-tap + status bar LED.
- Nav mode (fuori campo testo) con DPAD + ritorno in campo testo.
- SYM: toggle, auto-close, Alt priority, Ctrl shortcuts while SYM is open.
- Long-press Alt/SYM e Alt+Space.
- Inserimento touch vs fisico per emoji/simboli.

Con questa struttura modulare, le prossime fasi potranno concentrarsi su UI e text pipeline senza toccare nuovamente il servizio principale, riducendo il rischio di regressioni.

