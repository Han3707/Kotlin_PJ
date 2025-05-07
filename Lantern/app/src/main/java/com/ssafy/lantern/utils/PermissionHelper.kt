package com.ssafy.lantern.utils
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// private val activity를 받아와야 어떤 화면에서 권한 요청 화면을 띄울지 알 수 있음
class PermissionHelper(private val activity: Activity) {
    // Bluetooth가 현재 활성화 되어있는지
    // Context 상수를 모아놓은 클래스라고 생각하면 됨
    fun isBluetoothEnabeld(): Boolean{
        // Context는 Application Context와 Activity Context가 두 가지 존재
        // Manager를 호출하는데 any type을 호출하기 때문에 형변환 해줘야됨
        val bluetoothAdapter = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        // null == true -> false
        return bluetoothAdapter?.isEnabled == true;
    }

    // bluetooth permission을 가지고 있는지
    fun hasPermission(): Boolean{

        // 권한 이름을 배열에 저장한다.
        // String 배열
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // 배열에 원소 하나 하나에 대해서 내가 정한 조건을 확인한다.
        // it가 원소
        return permissions.all{
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // bluetooth 권한 요청
    // 일단은 모든 요청을 하는 씩으로 개발하고 나중에 뭔가 터지면 그 때 바꾸자
    fun requestPermissions(requestCode: Int){
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }
}