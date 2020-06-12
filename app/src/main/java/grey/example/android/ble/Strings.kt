package grey.example.android.ble

import androidx.annotation.StringRes

object Strings {
    fun get(@StringRes id: Int): String{
        return App.instance.getString(id)
    }

}

