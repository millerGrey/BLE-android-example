package grey.example.android.ble

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import grey.example.android.ble.databinding.ItemDeviceBinding

class DeviceListAdapter(private val viewModel: MainViewModel): RecyclerView.Adapter<DeviceListAdapter.ViewHolder>()
{
    private var set: Set<BluetoothDevice>? = emptySet()
    private lateinit var  binding: ItemDeviceBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        binding = DataBindingUtil.bind<ItemDeviceBinding>(view)!!
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(set?.toList()?.get(position),viewModel)
    }

    override fun getItemCount(): Int {
        return set?.size?:0
    }

    fun refreshList(lst: Set<BluetoothDevice>?){
        set = lst
        notifyDataSetChanged()
    }

    class ViewHolder(private var binding: ItemDeviceBinding): RecyclerView.ViewHolder(binding.root){
        fun bind(device: BluetoothDevice?, vm: MainViewModel){
            binding.device = device
            binding.listener = object : Clicker {
                override fun onClick() {
                    vm.onItemClick(layoutPosition)
                }
            }
            binding.executePendingBindings()
        }
    }
}
interface Clicker{
    fun onClick()
}

