package pl.prywatne.muzyka.car

import android.content.Context
import android.support.car.input.CarRestrictedEditText
import android.view.inputmethod.EditorInfo
import com.google.android.gms.car.input.CarEditable
import com.google.android.gms.car.input.CarEditableListener

/**
 * Niewidoczne pole tekstowe, które łączy się z klawiaturą wyświetlaną przez Android Auto
 * na ekranie samochodu. Wpisywany tekst trafia do [onChange], a „Szukaj” na klawiaturze do [onSubmit].
 */
class CarEditText(context: Context) : CarRestrictedEditText(context, null), CarEditable {
    var onSubmit: (() -> Unit)? = null

    init {
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setSingleLine(true)
    }

    override fun setCarEditableListener(listener: CarEditableListener?) {
        super.setCarEditableListener(
            android.support.car.input.CarEditableListener { selStart, selEnd, candStart, candEnd ->
                listener?.onUpdateSelection(selStart, selEnd, candStart, candEnd)
            }
        )
    }

    override fun onEditorAction(actionCode: Int) {
        when (actionCode) {
            EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_NEXT -> {
                onSubmit?.invoke()
                return
            }
        }
        super.onEditorAction(actionCode)
    }
}
