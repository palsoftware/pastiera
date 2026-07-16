# Changelog

## Pastiera Enhanced 4.0

Pastiera Enhanced 4.0 is a rewrite of the Enhanced fork on top of the newer upstream Pastiera base.

Builds on this branch are posted here first for faster troubleshooting and testing. These changes are intended to be split into pull requests against the main Pastiera branch once they are stable enough for upstream review.

### Added
- Predictive text integration with next-word predictions, bundled common phrase fallback, local learning, and removable predictions.
- Unified Mode, which can show predictions inside the existing variations/status bar instead of stacking a separate prediction row.
- Snippets with searchable shortcut popups, plus improved emoji/symbol shortcode completion.
- Media picker support inside the emoji picker, including GIFs, stickers, and local images.
- Theme controls for key tap color and modifier strip thickness.

### Fixed and Improved
- Fixed media sending crashes and restored GIF/sticker/image sending fallback behavior.
- Improved prediction replacement after selecting suggestions and advanced predictions after accepted words.
- Improved shortcode/snippet popup theming, sizing, and unified-mode positioning.
- Improved SYM popup layout, symbol page padding, page cycling, and close controls.
- Ported Enhanced behavior onto the newer Pastiera base to make future upstream pull requests easier to split and review.

## New Features Pastiera 0.2

### Keyboard Enhancements
- **Swipe Pad Navigation**: The keyboard status bar now doubles as a swipe pad, allowing you to move the cursor by swiping
- **Touch-Enabled Emojis and Symbols**: Emojis and symbols on the SYM keyboard are now also directly touchable for easier input
- **Keyboard Layout Conversion**: Added support for converting between different keyboard layouts (AZERTY, QWERTZ, etc.)

### Auto-Capitalization
- **Smart Sentence Capitalization**: Automatically capitalizes the first letter after sentences ending with periods, exclamation marks, or question marks

### Settings & Customization
- **Customizable Navigation Mode**: Navigation mode and Ctrl+key assignments can now be configured directly from the app settings
- **Quick Settings Access**: Added a quick toggle button (gear icon) to access settings directly from the keyboard
- **Enhanced Dictionary Management**: 
  - Added search functionality in the dictionary corrections interface
  - Custom dictionary entries now appear at the top of the list for easier access
  - Ricette Pastiera: autocorrections that are valid in all the languages. (such as ppp-> %)
  - Added a lot of new unicode chara for sym layer page 2

### User Interface
- **UI Improvements**: Redesigned and improved the app's user interface, various issues solved (white font on light background in android light mode)
- **Multi-Language Support**: Added translations for multiple languages (may require manual review and corrections)

## Bug Fixes

- **Fixed Alt+Space Pop-up Issue**: Resolved a bug that caused an unwanted pop-up to appear when pressing Alt+Space or Alt+Letter+Space
- **Fixed Speech Recognition Focus**: Fixed an issue where Google Voice Typing would incorrectly shift focus to another app when activated


*This changelog covers all changes since the last release (v0.1-alpha).*
