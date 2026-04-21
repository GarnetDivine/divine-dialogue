# Bundled Fonts

The Serif choice uses the platform's built-in serif font family and needs no file.
The other three choices load from files in this folder:

| Filename                      | Used For     | Source                             | License              |
|-------------------------------|--------------|------------------------------------|----------------------|
| `inter.ttf`                   | Sans Serif   | https://rsms.me/inter/             | SIL Open Font License |
| `runescape_uf.ttf`            | Pixel        | Medieval/OSRS-style pixel font     | Check per-font        |
| `OpenDyslexic-Regular.otf`    | Dyslexic     | https://opendyslexic.org/          | SIL Open Font License |

If any of these files are missing at runtime, the plugin falls back to the
matching platform default font family and logs a warning. You can develop and
test without dropping them in, but submission to the Plugin Hub expects the
bundled files to be present.

**Filename case matters** — the resource paths in `FontChoice.java` are
case-sensitive. If your file is named differently, either rename it to match
or update `FontChoice.java` to point at the actual filename.
