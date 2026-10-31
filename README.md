# Hoodify — odtwarzacz muzyki i filmów z YouTube bez reklam (prywatny test)

Twórca: Piotr Bogacz

Android 9+ (minSdk 28). Kotlin + Jetpack Compose + Media3 + NewPipeExtractor.

**Co działa:**
- trzy zakładki wyszukiwania: **Utwory** (YouTube Music), **Klipy** (zwykłe filmy: teledyski,
  lekcje, wykłady — od razu z obrazem) i **Playlisty**,
- w odtwarzaczu: prędkość 0,75×–2× oraz cofnij/przewiń o 10 s (przydatne przy lekcjach),
- ♡ ulubione utwory i zapisani wykonawcy w zakładce **Biblioteka** (zapisane tylko na telefonie),
- strona wykonawcy/kanału: menu ⋮ przy utworze → „Wykonawca” albo stuknięcie nazwy w odtwarzaczu,
- ekran nie gaśnie, gdy oglądasz wideo,
- wklejenie linku do filmu lub playlisty w pole wyszukiwania od razu go otwiera,
- playlista: „Odtwórz” / „Losowo” albo kliknięcie dowolnego utworu — cała playlista trafia do kolejki
  (do 500 utworów, dociągane w tle),
- kliknięcie wyniku wyszukiwania gra ten utwór, a potem kolejne wyniki,
- pełnoekranowy odtwarzacz: duża okładka, tło w kolorze okładki, suwak, losowo/powtarzanie, kolejka,
- tryb **Audio / Wideo** (wideo do 720p, z pełnym ekranem w poziomie),
- odtwarzanie w tle z powiadomieniem i obsługą słuchawek,
- przycisk aktualizacji (ikona w prawym górnym rogu).

> Aplikacja łamie regulamin YouTube — tylko do prywatnego użytku, nie publikuj jej w Google Play.
> NewPipeExtractor jest na licencji GPLv3, więc ten projekt też.

---

## Android Auto (ekran w aucie)

Hoodify ma własny ekran w Android Auto: wyszukiwanie (klawiatura na ekranie auta), Biblioteka,
playlisty, strony wykonawców, kolejka oraz **wideo na cały ekran** — przeznaczone do oglądania na postoju.
Używa tego samego nieoficjalnego Android Auto SDK co Fermata (`app/libs/aauto.aar`), więc:

1. W telefonie otwórz **Android Auto** → przewiń na dół i stuknij **10 razy w „Wersja”** →
   potwierdź włączenie trybu programisty.
2. Menu ⋮ w prawym górnym rogu → **Ustawienia programisty** → zaznacz **„Nieznane źródła”**.
3. Rozłącz i podłącz telefon do auta. Hoodify pojawi się w menu aplikacji Android Auto.

Ulubione, wykonawcy i tryb Audio/Wideo są wspólne dla telefonu i ekranu w aucie.
Po dużych aktualizacjach Android Auto ta funkcja może wymagać poprawek (dotyczy też Fermaty).

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
- Gdy utwór jest niedostępny, odtwarzacz raz ponawia próbę, a potem przechodzi do następnego.

## Struktura

| Plik | Rola |
|---|---|
| `App.kt` | inicjalizacja NewPipe |
| `DownloaderImpl.kt` | HTTP (OkHttp) dla extractora |
| `YouTubeRepo.kt` | wyszukiwanie utworów i playlist, linki |
| `StreamResolver.kt` | wybór strumienia audio/wideo w chwili odtwarzania |
| `ChunkedDataSource.kt` | pobieranie strumienia kawałkami (bez dławienia przez YouTube) |
| `YtMediaSourceFactory.kt` | łączenie obrazu i dźwięku w trybie wideo |
| `PlaybackService.kt` | ExoPlayer + MediaSession (odtwarzanie w tle) |
| `UpdateManager.kt` | sprawdzanie wersji, pobieranie i instalacja APK |
| `MainViewModel.kt` | stan aplikacji |
| `Library.kt` | ulubione i zapisani wykonawcy (pamięć telefonu) |
| `car/*.kt`, `ui/CarScreens.kt` | ekran Hoodify w Android Auto |
| `app/libs/aauto.aar` | nieoficjalne Android Auto SDK (z projektu Fermata, GPLv3) |
| `MainActivity.kt`, `ui/*.kt` | interfejs (wyszukiwarka, biblioteka, playlista/wykonawca, odtwarzacz) |
| `.github/workflows/auto-update.yml` | automatyczne budowanie nowych wersji |
