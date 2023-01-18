package com.example.airquality

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality.databinding.ActivityMainBinding
import com.example.airquality.retrofit.AirQualityResponse
import com.example.airquality.retrofit.AirQualityService
import com.example.airquality.retrofit.RetrofitConnection
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {

    var latitude: Double = 0.0
    var longitude: Double = 0.0

    lateinit var binding :ActivityMainBinding

    private val PERMISSION_REQUEST_CODE=100
    var REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION)

    lateinit var getGpsPermissionLauncher : ActivityResultLauncher<Intent>

    lateinit var locationProvider: LocationProvider

    val startMapActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), object : ActivityResultCallback<ActivityResult>{
        override fun onActivityResult(result: ActivityResult?) {
            if(result?.resultCode ?: 0 == Activity.RESULT_OK){
                latitude = result?.data?.getDoubleExtra("latitude",0.0) ?: 0.0
                longitude = result?.data?.getDoubleExtra("longitude",0.0) ?: 0.0
                updateUI()
            }
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()
        updateUI()
        setRefreshButton()

        setFab()
        setFab()
    }

    private fun setFab(){
        binding.fab.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra("currentLat", latitude)
            intent.putExtra("currentLng", longitude)
            startMapActivityResult.launch(intent)
        }
    }

    private fun setRefreshButton(){
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    private fun updateUI(){
        locationProvider = LocationProvider(this@MainActivity)

        // 위도와 경도 정보를 가져옵니다.
        if(latitude == 0.0 || longitude == 0.0){
            latitude = locationProvider.getLocationLatitude()
            longitude = locationProvider.getLocationLongitude()
        }

        if(latitude != 0.0 || longitude != 0.0){
            // 1. 현재 위치를 가져오고 UI 업데이트
            val address = getCurrentAddress(latitude, longitude)
            address?.let {
                binding.tvLocationTitle.text = "${it.thoroughfare}"
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }
            // 2. 현재 미세먼지 농도 가져오고 UI 업데이트
            getAirQualityData(latitude, longitude)
        } else{
            Toast.makeText(this@MainActivity, "위도, 경도 정보를 가져올 수 없었습니다. 새로고침을 눌러주세요",
            Toast.LENGTH_LONG).show()
        }

    }

    private fun checkAllPermissions(){
        // 1. 위치 서비스(GPS)가 켜져 있는지 확인
        if(!isLocationServiceAvailable()){
            showDialogForLocationServiceSetting()
        }else{ // 2. 런타임 앱 권한이 모두 허용되어 있는지 확인
            isRunTimePermissionsGranted()
        }
    }

    private fun isLocationServiceAvailable() : Boolean{
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    fun isRunTimePermissionsGranted(){
        // 위치 퍼미션을 가지고 있는지 체크
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        if(hasFineLocationPermission != PackageManager.PERMISSION_GRANTED ||
                hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.size == REQUIRED_PERMISSIONS.size){

            // 요청 코드가 PERMISSION_REQUEST_CODE이고, 요청한 퍼미션 개수만큼 수신되었다면
            var checkResult = true

            // 모든 퍼미션을 허용했으면 체크
            for(result in grantResults){
                if(result != PackageManager.PERMISSION_GRANTED){
                    checkResult = false
                    break
                }
            }

            if(checkResult){
                // 위젯값을 가져올 수 있음
                updateUI()
            } else {
                // 퍼미션이 거부되었다면 앱 종료
                Toast.makeText(this@MainActivity, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showDialogForLocationServiceSetting(){
        getGpsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()){
            result -> // 결과 값을 받았을 때 로직
            if(result.resultCode == Activity.RESULT_OK){
                // 사용자가 GPS를 활성화시켰는지 확인
                if(isLocationServiceAvailable()){
                    isRunTimePermissionsGranted()
                } else{
                    Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
        val builder : AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage("위치 서비스가 꺼져 있습니다. 설정해야 앱을 사용할 수 있습니다.")
        builder.setCancelable(true)
        builder.setPositiveButton("설정", DialogInterface.OnClickListener{
            dialog, id -> val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            getGpsPermissionLauncher.launch(callGPSSettingIntent)
        })
        builder.setNegativeButton("취소", DialogInterface.OnClickListener { dialog, id ->
            dialog.cancel()
            Toast.makeText(this@MainActivity, "기기에서 위치서비스(GPS) 설정 후 사용해주세요.", Toast.LENGTH_LONG).show()
            finish()
        })
        builder.create().show()
    }

    fun getCurrentAddress(latitude: Double, longitude: Double) : Address? {
        val geocoder = Geocoder(this, Locale.getDefault())
        // Address 객체는 주소와 관련된 여러 정보를 가지고 있습니다.
        // android.location.Address 패키지 참고
        val addresses: List<Address>?

        addresses = try{
            geocoder.getFromLocation(latitude, longitude, 7)
        } catch (ioException: IOException){
            Toast.makeText(this, "지오코더 서비스 사용불가합니다.", Toast.LENGTH_LONG).show()
            return null
        } catch (illegalArgumentException : java.lang.IllegalArgumentException){
            Toast.makeText(this, "잘못된 위도, 경도 입니다.", Toast.LENGTH_LONG).show()
            return null
        }

        if(addresses == null || addresses.size == 0){
            Toast.makeText(this, "주소가 발견되지 않았습니다.", Toast.LENGTH_LONG).show()
            return null
        }
        val address: Address = addresses[0]
        return address
    }

    fun getAirQualityData(latitude: Double, longitude: Double){
        val retrofitAPI = RetrofitConnection.getInstance().create(AirQualityService::class.java)
        retrofitAPI.getAirQualityData(
            latitude.toString(),
            longitude.toString(),
            "d364ac80-77ae-4f47-bf69-050991244b46"
        ).enqueue(object : Callback<AirQualityResponse> {
            override fun onResponse(
                call: Call<AirQualityResponse>,
                response: Response<AirQualityResponse>
            ) {
                if(response.isSuccessful){
                    Toast.makeText(this@MainActivity, "최신 정보 업데이트 완료!", Toast.LENGTH_LONG).show()
                    response.body()?.let { updateAirUI(it) }
                } else{
                    Toast.makeText(this@MainActivity, "업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(this@MainActivity, "업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateAirUI(airQualityData: AirQualityResponse){
        val pollutionData = airQualityData.data.current.pollution

        // 수치 지정
        binding.tvCount.text = pollutionData.aqius.toString()

        // 측정된 날짜 지정
        val dateTime = ZonedDateTime.parse(pollutionData.ts).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()
        val dateFormatter : DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        binding.tvCheckTime.text = dateTime.format(dateFormatter).toString()

        when(pollutionData.aqius){
            in 0..50->{
                binding.tvTitle.text = "좋음"
                binding.imgBg.setImageResource(R.drawable.bg_good)
            }
            in 51..150->{
                binding.tvTitle.text = "보통"
                binding.imgBg.setImageResource(R.drawable.bg_soso)
            }
            in 151..200->{
                binding.tvTitle.text = "나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_bad)
            }
            else ->{
                binding.tvTitle.text = "매우 나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_worst)
            }

        }
    }
}