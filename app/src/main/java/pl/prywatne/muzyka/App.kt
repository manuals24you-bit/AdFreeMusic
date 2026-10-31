package pl.prywatne.muzyka

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderImpl.instance, Localization("pl", "PL"), ContentCountry("PL"))
        PlayerMode.video = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_VIDEO, false)
        LibraryStore.init(this)
    }

    companion object {
        const val PREFS = "settings"
        const val KEY_VIDEO = "video"
    }
}
