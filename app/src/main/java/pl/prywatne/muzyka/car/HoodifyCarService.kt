package pl.prywatne.muzyka.car

import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

/** Punkt wejścia dla Android Auto: pokazuje [HoodifyCarActivity] na ekranie samochodu. */
class HoodifyCarService : CarActivityService() {
    override fun getCarActivity(): Class<out CarActivity> = HoodifyCarActivity::class.java
}
