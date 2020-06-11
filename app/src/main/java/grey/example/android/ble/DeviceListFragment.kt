package grey.example.android.ble

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import grey.example.android.ble.databinding.FragmentDeviceListBinding


class DeviceListFragment: Fragment(){

    private val mainVM  by lazy{ViewModelProviders.of(requireActivity()).get(MainViewModel::class.java)}
    lateinit var binding: FragmentDeviceListBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val adapter = DeviceListAdapter(mainVM)
        val layoutManager = LinearLayoutManager(requireActivity())

        binding = FragmentDeviceListBinding.inflate(inflater, container, false)
        binding.apply{
            viewModel = mainVM
            deviceRecyclerView.adapter = adapter
            deviceRecyclerView.layoutManager = layoutManager
            lifecycleOwner = requireActivity()
        }
        return binding.root
    }

    fun newInstance():DeviceListFragment = DeviceListFragment()

}