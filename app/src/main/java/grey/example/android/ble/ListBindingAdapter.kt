package grey.example.android.ble

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton


object ListBindingAdapter {
    @BindingAdapter("app:items")
    @JvmStatic fun setItems(listView: RecyclerView, items: Set<BluetoothDevice>?) {
        with(listView.adapter as DeviceListAdapter) {
            Log.d("RV","Refresh")
            refreshList(items)
        }
    }
}

object IconBindingAdapter {

    @BindingAdapter("app:icon")
    @JvmStatic fun setItems(fab: FloatingActionButton, state: Boolean) {
        when(state){
            true->{
                fab.setImageResource(R.drawable.ic_clear_black_24dp)
            }
            false-> {
                fab.setImageResource(R.drawable.ic_search_black_24dp)
            }
        }
    }
}



