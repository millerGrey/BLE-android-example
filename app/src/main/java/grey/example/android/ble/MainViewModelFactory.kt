package grey.example.android.ble

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory(
    private val application: Application
    ) : ViewModelProvider.AndroidViewModelFactory(application){
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        MainViewModel::class.java -> MainViewModel(
            application
        ) as T
        else -> throw RuntimeException("Cannot create an instance of $modelClass")
    }
}
