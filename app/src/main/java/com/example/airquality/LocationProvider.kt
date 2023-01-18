package com.example.airquality

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat

class LocationProvider(val context: Context) {
    private var location : Location? = null
    private var locationManager: LocationManager? = null

    init{
        getLocation()
    }

    private fun getLocation(): Location? {
        try{
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            var gpsLocation: Location? = null
            var networkLocation: Location? = null

            // GPS Provider와 Network Provider가 활성화되어 있는지 확인
            val isGPSEnabled : Boolean = locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled : Boolean = locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if(!isGPSEnabled && !isNetworkEnabled){
                // GPS, Network Provider 둘 다 사용 불가능한 상황이면 null 반환
                return null
            }else{
                // coarseLocation보다 더 정밀한 위치 정보 가져오기
                val hasFineLocationPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                // 도시 블록 단위 정도의 정밀도로 위치 정보 가져오기
                val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)

                // 위 두 개 권한이 없다면 null을 리턴한다.
                if(hasFineLocationPermission!= PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) return null
                // 네트워크를 통한 위치 파악이 가능한 경우에 위치를 가져옵니다.
                if(isNetworkEnabled){
                    networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }

                if(isGPSEnabled){
                    gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }

                if(gpsLocation != null && networkLocation != null){
                    // 두 개의 위치가 있다면 정확도가 높은것으로 선택된다.
                    if(gpsLocation.accuracy > networkLocation.accuracy){
                        location = gpsLocation
                        return gpsLocation
                    } else{
                        location = networkLocation
                        return networkLocation
                    }
                } else{

                    if(gpsLocation != null){
                        location = gpsLocation
                    }
                    if(networkLocation != null){
                        location = networkLocation
                    }
                }

            }
        }catch (e: Exception){
            e.printStackTrace() // 에러 출력
        }
        return location
    }

    // 위도 정보를 가져오는 함수
    fun getLocationLatitude(): Double{
        return location?.latitude ?: 0.0 // null이면 0.0반환
    }

    fun getLocationLongitude(): Double{
        return location?.longitude ?: 0.0
    }

}