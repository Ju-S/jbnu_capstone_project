package com.example.kesco_app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.kesco_app.databinding.ActivityMainBinding
import com.example.kesco_app.databinding.AlertdialogEdittextBinding
import kotlinx.android.synthetic.main.activity_main.view.*
import net.daum.mf.map.api.*
import net.daum.mf.map.api.MapPoint.GeoCoordinate
import java.io.InputStream
import java.io.OutputStream


data class customerInfoData(var insulationResist: Float = 0f,
                            var igo: Float = 0f, var igr: Float = 0f,
                            var ac_v: Float = 0f, var freq: Float = 0f,
                            var leakCurrent: Float = 0f, var loadCurrent: Float = 0f)

class MainActivity : AppCompatActivity(), MapView.MapViewEventListener, MapView.POIItemEventListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private lateinit var listView: ListView
    private lateinit var customerInfoList: ArrayList<customerInfoData>
    private lateinit var customerNameList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private var checkCustomerInfoButtonFlg = false
    private var createMarkerFlg: Boolean = false
    private var selectedPOIItemTag = 0
    private var totalPOIItemCnt = 0

    private var btClient: BluetoothClient = BluetoothClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        listView = view.customerInfoList
        listView.onItemClickListener = SimpleListListener()
        customerNameList = arrayListOf()
        customerInfoList = arrayListOf()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, customerNameList)
        listView.adapter = adapter

        checkBluetoothConnectPermission()
        btClient.setOnSocketListener(mOnSocketListener)

        // GPS ?????? ??????
        if (checkLocationService()) {
            permissionCheck()
        } else {
            Toast.makeText(this, "GPS??? ????????????", Toast.LENGTH_SHORT).show()
        }

        //region ?????????map ?????????????????? ?????? ??? ????????? ?????????
        map = MapView(this)
        map.setMapViewEventListener(this)
        map.setPOIItemEventListener(this)
        binding.mapView.addView(map)   // ????????? ?????? ???
        //endregion

        view.createMarkerButton.setOnClickListener {
            createMarkerFlg = true
        }

        view.checkCustomerInfoButton.setOnClickListener{
            if(!checkCustomerInfoButtonFlg) {
                view.customerInfoScrollView.visibility = View.VISIBLE
                view.customerInfoList.visibility = View.INVISIBLE
                view.checkCustomerInfoButton.text = "??????"
                checkCustomerInfoButtonFlg = true
                view.bluetoothConnectButton.visibility = View.VISIBLE
                setCustomerInfo(customerInfoList[selectedPOIItemTag])
            }
            else{
                view.customerInfoScrollView.visibility = View.INVISIBLE
                view.customerInfoList.visibility = View.VISIBLE
                view.checkCustomerInfoButton.text = "??????????????????"
                checkCustomerInfoButtonFlg = false
                view.bluetoothConnectButton.visibility = View.INVISIBLE
            }
        }

        view.bluetoothConnectButton.setOnClickListener{
            val devices = btClient.getPairedDevices()

            var builder = AlertDialog.Builder(this)
            builder.setTitle("????????? ?????? ??????")
            var deviceArray: Array<String> = arrayOf()
            for(bluetoothDevice in devices){
                deviceArray = deviceArray.plus(bluetoothDevice.name)
            }

            builder.setItems(deviceArray, DialogInterface.OnClickListener{ _, which ->
                btClient.connectToServer(devices[which])
            })

            val alertDialog = builder.create()
            alertDialog.show()
        }
    }

    private val mOnSocketListener: SocketListener = object : SocketListener {
        override fun onConnect() {
        }

        override fun onDisconnect() {
        }

        override fun onError(e: Exception?) {
        }

        override fun onReceive(msg: String?) {
            msg?.let {
                Log.i("btdata", msg)
                val splitedMsg = msg.split(',')
                Log.i("btdata", splitedMsg[7])
                customerInfoList[selectedPOIItemTag] = customerInfoData(
                    splitedMsg[1].toFloat(), splitedMsg[2].toFloat(), splitedMsg[3].toFloat(), splitedMsg[4].toFloat(),
                    splitedMsg[5].toFloat(), splitedMsg[6].toFloat(), splitedMsg[7].toFloat())
                setCustomerInfo(customerInfoList[selectedPOIItemTag])
            }
        }

        override fun onSend(msg: String?) {
        }

        override fun onLogPrint(msg: String?) {
        }
    }

    fun setCustomerInfo(customerInfoData: customerInfoData){
        binding.insulationResist.setText("????????????: " + customerInfoData.insulationResist.toString())
        binding.igo.setText("IGO: " + customerInfoData.igo.toString())
        binding.igr.setText("IGR: " + customerInfoData.igr.toString())
        binding.acV.setText("AC V: " + customerInfoData.ac_v.toString())
        binding.freq.setText("?????????: " + customerInfoData.freq.toString())
        binding.leakCurrent.setText("??????????????????: " + customerInfoData.leakCurrent.toString())
        binding.loadCurrent.setText("????????????: " + customerInfoData.loadCurrent.toString())
    }

    fun checkBluetoothConnectPermission(){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "???????????? ????????? ?????????????????????.", Toast.LENGTH_SHORT).show()
            return
        }
    }

    inner class SimpleListListener: AdapterView.OnItemClickListener{
        override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            onPOIItemSelected(map, map.findPOIItemByTag(position))
            map.selectPOIItem(map.findPOIItemByTag(position), true)
            selectedPOIItemTag = position
            Log.i("selectedTag", selectedPOIItemTag.toString())
        }
    }

    // ????????? ?????? ??????
    private fun createNewMarker(map: MapView, markerName: String, markerPoint: MapPoint) {
        val marker = MapPOIItem()
        marker.itemName = markerName
        marker.tag = totalPOIItemCnt++
        marker.mapPoint = markerPoint
        marker.markerType = MapPOIItem.MarkerType.BluePin
        marker.selectedMarkerType = MapPOIItem.MarkerType.RedPin

        customerInfoList.add(customerInfoData())

        map.addPOIItem(marker)
    }

    // ?????? ??????
    private fun permissionCheck() {
        val ACCESS_FINE_LOCATION = 1000
        val preference = getPreferences(MODE_PRIVATE)
        val isFirstCheck = preference.getBoolean("isFirstPermissionCheck", true)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // ????????? ?????? ??????
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // ?????? ??????
                val builder = AlertDialog.Builder(this)
                builder.setMessage("?????? ????????? ?????????????????? ?????? ????????? ??????????????????.")
                builder.setPositiveButton("??????") { dialog, which ->
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION)
                }
                builder.setNegativeButton("??????") { dialog, which ->

                }
                builder.show()
            } else {
                if (isFirstCheck) {
                    // ?????? ?????? ??????
                    preference.edit().putBoolean("isFirstPermissionCheck", false).apply()
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION)
                } else {
                    val builder = AlertDialog.Builder(this)
                    builder.setMessage("?????? ????????? ?????????????????? ???????????? ?????? ????????? ??????????????????.")
                    builder.setPositiveButton("???????????? ??????") { dialog, which ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                    builder.setNegativeButton("??????") { dialog, which ->

                    }
                    builder.show()
                }
            }
        } else {

        }

    }

    // ?????? ??????
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ACCESS_FINE_LOCATION = 1000
        if (requestCode == ACCESS_FINE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "?????? ????????? ?????????????????????", Toast.LENGTH_SHORT).show()

            } else {
                Toast.makeText(this, "?????? ????????? ?????????????????????", Toast.LENGTH_SHORT).show()

            }
        }
    }

    // GPS??? ??????????????? ??????
    private fun checkLocationService(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    //region MapView????????? ??????
    override fun onMapViewInitialized(p0: MapView) {

    }

    override fun onMapViewCenterPointMoved(p0: MapView?, p1: MapPoint?) {

    }

    override fun onMapViewZoomLevelChanged(p0: MapView?, p1: Int) {

    }

    override fun onMapViewSingleTapped(p0: MapView, p1: MapPoint) {
        val mapPointGeo: GeoCoordinate = p1.mapPointGeoCoord
        val expectedMarkerPoint = MapPoint.mapPointWithGeoCoord(mapPointGeo.latitude, mapPointGeo.longitude)

        if(createMarkerFlg) {
            val builder = AlertDialog.Builder(this)
            val builderItem = AlertdialogEdittextBinding.inflate(layoutInflater)
            val editText = builderItem.editText

            with(builder){
                setTitle("????????? ????????? ???????????????.")
                setView(builderItem.root)
                setPositiveButton("??????"){ dialogInterface: DialogInterface, i: Int ->
                    if(editText.text != null) {
                        createNewMarker(map, editText.text.toString(), expectedMarkerPoint)
                        customerNameList.add(editText.text.toString())
                        adapter.notifyDataSetChanged()
                        Log.i("customerInfo", customerNameList.toString())
                    }
                }
                show()
            }
            createMarkerFlg = false
        }
        Log.i("mapPointGeo", mapPointGeo.latitude.toString() + " " + mapPointGeo.longitude.toString())
    }

    override fun onMapViewDoubleTapped(p0: MapView?, p1: MapPoint?) {

    }

    override fun onMapViewLongPressed(p0: MapView?, p1: MapPoint?) {

    }

    override fun onMapViewDragStarted(p0: MapView?, p1: MapPoint?) {

    }

    override fun onMapViewDragEnded(p0: MapView?, p1: MapPoint?) {

    }

    override fun onMapViewMoveFinished(p0: MapView?, p1: MapPoint?) {

    }
    //endregion

    //region POIItem????????? ??????
    override fun onPOIItemSelected(p0: MapView, p1: MapPOIItem) {
        val cameraPoint = CameraPosition(p1.mapPoint, 2f)
        p0.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPoint))
        selectedPOIItemTag = p1.tag
        Log.i("selectedTag", selectedPOIItemTag.toString())
    }

    override fun onCalloutBalloonOfPOIItemTouched(p0: MapView?, p1: MapPOIItem?) {

    }

    override fun onCalloutBalloonOfPOIItemTouched(
        p0: MapView,
        p1: MapPOIItem,
        p2: MapPOIItem.CalloutBalloonButtonType
    ) {

    }

    override fun onDraggablePOIItemMoved(p0: MapView?, p1: MapPOIItem?, p2: MapPoint?) {

    }
    //endregion
}
