# The editor

Each file opens in its own editor tab in the middle of the window. The footer shows the number of lines and
characters.

- **Save** with <kbd>Ctrl</kbd>+<kbd>S</kbd>, **File ▸ Save** or the toolbar button. Save is enabled only
  while an editor with unsaved changes is active; the tab shows the file name in **bold** meanwhile.
- Closing an editor with unsaved changes asks whether to save them. *Yes* saves and closes, *No* discards,
  *Cancel* keeps the editor open.
- Opening a file that is already open activates its existing editor.
- Editors are restored at your next login, with the files they showed; a file that was deleted in the meantime
  is skipped.
- Renaming a file in the [Files](files.md) window renames the open editor's tab as well.

Saved files are written to the server; the **Output** window logs each save.
