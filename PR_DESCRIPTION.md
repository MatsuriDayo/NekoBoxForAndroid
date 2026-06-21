# PR description for quick import feature

This pull request adds a quick import dialog and activity to allow pasting a proxy configuration (VLESS/VMess/etc.) and importing it as a profile, with an option to auto-connect after import.

Changes:
- Add QuickImportDialogFragment (UI for paste/import)
- Add QuickImportActivity (parsing, create profile, select and auto-connect)
- Add strings_import.xml for dialog texts
- Add menu entry "Quick import" in add_profile_menu.xml
- Register QuickImportActivity in AndroidManifest.xml
- Fix resources: removed duplicate closing </resources> in strings.xml

Please test locally before merging.
