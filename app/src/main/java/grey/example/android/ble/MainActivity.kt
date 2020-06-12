package grey.example.android.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.observe
import grey.example.android.ble.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    val mainVM by lazy{ViewModelProviders.of(this, MainViewModelFactory(App.instance)).get(MainViewModel::class.java)}
    val REQUEST_ENABLE_BT = 1
    val REQUEST_LOCATION_ACCESS = 2
    var isFirstState = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initFragment(DeviceListFragment().newInstance())
        mainVM.toastMessage.observe(this) { value ->
            makeToast(value)
        }
        mainVM.isConnected.observe(this){
            if((it == false) && !isFirstState){
                makeToast(getString(R.string.disconnected))
                mainVM.reset()
                return@observe
            }
            isFirstState = false
        }

        var binding =
            DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.viewModel = mainVM
        binding.lifecycleOwner = this

        fab.setOnClickListener {
            if (checkIsBtEnable()) {
                if (checkPermission()) {
                    mainVM.startScan()
                }
            }
        }
        buttonSend.setOnClickListener{
            mainVM.sendCommand(editText.text.toString())
        }
    }

    fun initFragment(frag: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, frag)
            .commit()
    }

    override fun onStop() {
        mainVM.stopScan()
        mainVM.disconnect()
        isFirstState = true
        super.onStop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == Activity.RESULT_OK && requestCode == REQUEST_ENABLE_BT){
            if(checkPermission()){
                mainVM.startScan()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun checkIsBtEnable(): Boolean{
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return false
        }
        return true
    }

    private fun checkPermission():Boolean{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(enableLocationService()){
                return checkBlePermission()
            }
            return false
        }
        return true
    }

    private fun enableLocationService(): Boolean {
        val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providerEnabled =
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) or lm.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        if (!providerEnabled) {
            val res = this.getResources()
            // notify user
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            dialog.setMessage(res.getString(R.string.EnableLocationService))
            dialog.setPositiveButton(res.getString(android.R.string.ok),
                DialogInterface.OnClickListener { paramDialogInterface, _ ->
                    this.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    paramDialogInterface.cancel()
                })
            dialog.setNegativeButton(
                res.getString(android.R.string.cancel),
                DialogInterface.OnClickListener { paramDialogInterface, _ ->
                    paramDialogInterface.cancel()
                }//onClick
            )
            dialog.show()
        }//if
        return providerEnabled
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkBlePermission():Boolean {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED))
        {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_ACCESS)
            return false
        } else
        return true
    }//checkBlePermission


    fun makeToast(str: String?) {
        val toast = Toast.makeText(this, str, LENGTH_SHORT)
        toast.setGravity(Gravity.TOP, 0, 200)
        toast.show()
    }
}
