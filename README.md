# Muzyka — odtwarzacz audio z YouTube bez reklam (prywatny test)

Android 9+ (minSdk 28). Kotlin + Jetpack Compose + Media3 + NewPipeExtractor.

**Co działa:** wyszukiwanie, odtwarzanie samego dźwięku, odtwarzanie w tle z powiadomieniem
i obsługą słuchawek, kolejka (przycisk ➕ przy utworze), przycisk aktualizacji (ikona w prawym górnym rogu).

> Aplikacja łamie regulamin YouTube — tylko do prywatnego użytku, nie publikuj jej w Google Play.
> NewPipeExtractor jest na licencji GPLv3, więc ten projekt też.

---

## 1. Pierwsze uruchomienie (Android Studio)

1. Otwórz folder `AdFreeMusic` w Android Studio (Ladybug lub nowsze).
2. Poczekaj na synchronizację Gradle. Jeśli Studio zaproponuje aktualizację AGP/Kotlina — możesz się zgodzić.
3. Podłącz telefon (tryb debugowania USB) i kliknij ▶ Run.

## 2. Jak działa przycisk aktualizacji

Bibliotek nie da się podmienić w już zainstalowanym APK, więc aktualizacja wygląda tak:

```
YouTube coś zmienia → wychodzi nowe NewPipeExtractor
      → GitHub Actions (codziennie) wykrywa nową wersję, podbija ją, buduje APK, wrzuca do Releases
      → w aplikacji: ikona aktualizacji → „Pobierz i zainstaluj”
```

Okno aktualizacji pokazuje też, którą wersję extractora masz, a która jest najnowsza.

## 3. Konfiguracja automatycznych aktualizacji (jednorazowo, ~10 min)

### a) Repozytorium
Wrzuć projekt do **publicznego** repo na GitHubie (aplikacja pobiera APK bez logowania;
przy prywatnym repo potrzebny byłby token). Następnie w `gradle.properties`:

```
updateRepo=TWOJ_LOGIN/AdFreeMusic
```

### b) Klucz podpisu (ważne!)
Android zainstaluje aktualizację tylko, jeśli każda wersja jest podpisana tym samym kluczem.

```bash
keytool -genkeypair -v -keystore release.jks -alias muzyka \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.jks.b64     # na macOS: base64 -i release.jks
```

**Nie commituj `release.jks`.** W repo: *Settings → Secrets and variables → Actions* dodaj:

| Sekret | Wartość |
|---|---|
| `KEYSTORE_BASE64` | zawartość `release.jks.b64` |
| `KEYSTORE_PASSWORD` | hasło keystore |
| `KEY_ALIAS` | `muzyka` |
| `KEY_PASSWORD` | hasło klucza |

### c) Pierwsza kompilacja
Zakładka *Actions → Auto-update & build → Run workflow* z zaznaczonym „force”.
W *Releases* pojawi się `Muzyka-v1001.apk` — zainstaluj go na telefonie (ta wersja zastępuje
wersję z Android Studio; jeśli instalator protestuje, odinstaluj najpierw starą).

Od tej pory nowe wersje będą się budować same, gdy wyjdzie nowy extractor.

## 4. Gdy coś przestaje grać

- Najpierw: ikona aktualizacji. Jeśli jest nowy extractor, ale nie ma jeszcze APK —
  uruchom workflow ręcznie w Actions.
- Błąd „captcha” = YouTube ograniczył zapytania z Twojego IP; odczekaj albo zmień sieć (Wi-Fi ↔ LTE).
- Bardzo wolne buforowanie: YouTube czasem dławi strumienie. NewPipe obchodzi to własnym
  źródłem danych z zapytaniami `range` — to dobry następny krok rozbudowy (`PlaybackService.kt`).

## Struktura

| Plik | Rola |
|---|---|
| `App.kt` | inicjalizacja NewPipe |
| `DownloaderImpl.kt` | HTTP (OkHttp) dla extractora |
| `YouTubeRepo.kt` | wyszukiwanie + adres strumienia audio |
| `PlaybackService.kt` | ExoPlayer + MediaSession (odtwarzanie w tle) |
| `UpdateManager.kt` | sprawdzanie wersji, pobieranie i instalacja APK |
| `MainViewModel.kt`, `MainActivity.kt` | interfejs |
| `.github/workflows/auto-update.yml` | automatyczne budowanie nowych wersji |
