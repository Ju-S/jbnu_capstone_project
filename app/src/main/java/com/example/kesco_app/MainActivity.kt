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

        // GPS 권한 확인
        if (checkLocationService()) {
            permissionCheck()
        } else {
            Toast.makeText(this, "GPS를 켜주세요", Toast.LENGTH_SHORT).show()
        }

        //region 카카오map 이벤트리스너 등록 및 맵뷰에 더하기
        map = MapView(this)
        map.setMapViewEventListener(this)
        map.setPOIItemEventListener(this)
        binding.mapView.addView(map)   // 카카오 지도 뷰
        //endregion

        view.createMarkerButton.setOnClickListener {
            createMarkerFlg = true
        }

        view.checkCustomerInfoButton.setOnClickListener{
            if(!checkCustomerInfoButtonFlg) {
                view.customerInfoScrollView.visibility = View.VISIBLE
                view.customerInfoList.visibility = View.INVISIBLE
                view.checkCustomerInfoButton.text = "닫기"
                checkCustomerInfoButtonFlg = true
                view.bluetoothConnectButton.visibility = View.VISIBLE
                setCustomerInfo(customerInfoList[selectedPOIItemTag])
            }
            else{
                view.customerInfoScrollView.visibility = View.INVISIBLE
                view.customerInfoList.visibility = View.VISIBLE
                view.checkCustomerInfoButton.text = "고객정보확인"
                checkCustomerInfoButtonFlg = false
                view.bluetoothConnectButton.visibility = View.INVISIBLE
            }
        }

        view.bluetoothConnectButton.setOnClickListener{
            val devices = btClient.getPairedDevices()

            var builder = AlertDialog.Builder(this)
            builder.setTitle("입력할 기기 선택")
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
        binding.insulationResist.setText("절연저항: " + customerInfoData.insulationResist.toString())
        binding.igo.setText("IGO: " + customerInfoData.igo.toString())
        binding.igr.setText("IGR: " + customerInfoData.igr.toString())
        binding.acV.setText("AC V: " + customerInfoData.ac_v.toString())
        binding.freq.setText("주파수: " + customerInfoData.freq.toString())
        binding.leakCurrent.setText("합성누설전류: " + customerInfoData.leakCurrent.toString())
        binding.loadCurrent.setText("부하전류: " + customerInfoData.loadCurrent.toString())
    }

    fun checkBluetoothConnectPermission(){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "블루투스 연결을 확인해주십시오.", Toast.LENGTH_SHORT).show()
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

    // 새로운 마커 생성
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

    // 권한 확인
    private fun permissionCheck() {
        val ACCESS_FINE_LOCATION = 1000
        val preference = getPreferences(MODE_PRIVATE)
        val isFirstCheck = preference.getBoolean("isFirstPermissionCheck", true)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없는 상태
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // 권한 거절
                val builder = AlertDialog.Builder(this)
                builder.setMessage("현재 위치를 확인하시려면 위치 권한을 허용해주세요.")
                builder.setPositiveButton("확인") { dialog, which ->
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION)
                }
                builder.setNegativeButton("취소") { dialog, which ->

                }
                builder.show()
            } else {
                if (isFirstCheck) {
                    // 최초 권한 요청
                    preference.edit().putBoolean("isFirstPermissionCheck", false).apply()
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION)
                } else {
                    val builder = AlertDialog.Builder(this)
                    builder.setMessage("현재 위치를 확인하시려면 설정에서 위치 권한을 허용해주세요.")
                    builder.setPositiveButton("설정으로 이동") { dialog, which ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                    builder.setNegativeButton("취소") { dialog, which ->

                    }
                    builder.show()
                }
            }
        } else {

        }

    }

    // 권한 요청
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ACCESS_FINE_LOCATION = 1000
        if (requestCode == ACCESS_FINE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "위치 권한이 승인되었습니다", Toast.LENGTH_SHORT).show()

            } else {
                Toast.makeText(this, "위치 권한이 거절되었습니다", Toast.LENGTH_SHORT).show()

            }
        }
    }

    // GPS가 켜져있는지 확인
    private fun checkLocationService(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    //region MapView리스너 함수
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
                setTitle("마커의 이름을 입력하세요.")
                setView(builderItem.root)
                setPositiveButton("확인"){ dialogInterface: DialogInterface, i: Int ->
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

    //region POIItem리스너 함수
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
