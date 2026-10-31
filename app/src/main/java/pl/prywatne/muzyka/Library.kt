package pl.prywatne.muzyka

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Biblioteka zapisana tylko na telefonie (SharedPreferences, format JSON):
 * ulubione utwory i zapisani wykonawcy. Nic nie jest wysyłane do internetu.
 */
class Library(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun loadFavorites(): List<Track> = readArray(KEY_FAVORITES).mapNotNull { o ->
        val url = o.optString("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Track(
            title = o.optString("title"),
            url = url,
            uploader = o.optString("uploader"),
            durationSec = o.optLong("duration"),
            thumbnail = o.optString("thumbnail").takeIf { it.isNotEmpty() },
            uploaderUrl = o.optString("uploaderUrl").takeIf { it.isNotEmpty() },
        )
    }

    fun saveFavorites(tracks: List<Track>) = writeArray(KEY_FAVORITES, tracks.map { t ->
        JSONObject()
            .put("title", t.title)
            .put("url", t.url)
            .put("uploader", t.uploader)
            .put("duration", t.durationSec)
            .put("thumbnail", t.thumbnail.orEmpty())
            .put("uploaderUrl", t.uploaderUrl.orEmpty())
    })

    fun loadArtists(): List<ArtistRef> = readArray(KEY_ARTISTS).mapNotNull { o ->
        val url = o.optString("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        ArtistRef(url, o.optString("name"), o.optString("avatar").takeIf { it.isNotEmpty() })
    }

    fun saveArtists(artists: List<ArtistRef>) = writeArray(KEY_ARTISTS, artists.map { a ->
        JSONObject().put("url", a.url).put("name", a.name).put("avatar", a.avatar.orEmpty())
    })

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_ARTISTS = "artists"
    }
}

/** Jedna, wspólna kopia biblioteki dla całej aplikacji (telefon + Android Auto). */
object LibraryStore {
    private var library: Library? = null

    var favorites: List<Track> by mutableStateOf(emptyList())
        private set
    var favoriteUrls: Set<String> by mutableStateOf(emptySet())
        private set
    var artists: List<ArtistRef> by mutableStateOf(emptyList())
        private set

    fun init(context: Context) {
        if (library != null) return
        val lib = Library(context.applicationContext)
        library = lib
        favorites = lib.loadFavorites()
        favoriteUrls = favorites.map { it.url }.toSet()
        artists = lib.loadArtists()
    }

    fun updateFavorites(list: List<Track>) {
        favorites = list
        favoriteUrls = list.map { it.url }.toSet()
        library?.saveFavorites(list)
    }

    fun updateArtists(list: List<ArtistRef>) {
        artists = list
        library?.saveArtists(list)
    }
}
