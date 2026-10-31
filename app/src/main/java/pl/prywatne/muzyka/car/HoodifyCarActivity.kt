package pl.prywatne.muzyka.car

import android.app.Application
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.apps.auto.sdk.CarActivity
import pl.prywatne.muzyka.MainViewModel
import pl.prywatne.muzyka.ui.CarApp
import pl.prywatne.muzyka.ui.CarInput

/**
 * Ekran Hoodify wyświetlany przez Android Auto na wyświetlaczu w aucie
 * (ta sama technika co w Fermacie: nieoficjalne Android Auto SDK, wymaga „Nieznanych źródeł”).
 *
 * CarActivity nie jest zwykłą Activity, więc sami dostarczamy to, czego potrzebuje Compose:
 * cykl życia, magazyn ViewModeli, zapis stanu i obsługę przycisku „wstecz”.
 */
class HoodifyCarActivity : CarActivity(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val backDispatcher = OnBackPressedDispatcher { superBack() }
    private var editText: CarEditText? = null
    private var watcher: TextWatcher? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = backDispatcher

    /** Klawiatura Android Auto na ekranie auta. */
    private val carInput = object : CarInput {
        override fun start(initial: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
            val et = editText ?: CarEditText(this@HoodifyCarActivity).also { editText = it }
            watcher?.let { et.removeTextChangedListener(it) }
            et.setText(initial)
            et.setSelection(et.text.length)
            val w = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
            }
            et.addTextChangedListener(w)
            watcher = w
            et.onSubmit = {
                stop()
                onSubmit()
            }
            runCatching { a().startInput(et) }
        }

        override fun stop() {
            runCatching { if (a().isInputActive) a().stopInput() }
        }
    }

    private fun superBack() {
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setIgnoreConfigChanges(-1) // obrót / tryb nocny obsługuje Compose
        runCatching {
            carUiController.statusBarController.hideAppHeader()
            carUiController.menuController.hideMenuButton()
        }

        savedStateController.performAttach()
        savedStateController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val app = applicationContext as Application
        val vm = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MainViewModel::class.java]

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@HoodifyCarActivity)
            setViewTreeViewModelStoreOwner(this@HoodifyCarActivity)
            setViewTreeSavedStateRegistryOwner(this@HoodifyCarActivity)
            setViewTreeOnBackPressedDispatcherOwner(this@HoodifyCarActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@HoodifyCarActivity))
            setContent { CarApp(vm, carInput) }
        }
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        carInput.stop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savedStateController.performSave(outState)
    }

    override fun onBackPressed() {
        backDispatcher.onBackPressed()
    }
}
