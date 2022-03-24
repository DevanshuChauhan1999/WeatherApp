package com.devanshu.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.devanshu.weatherapp.databinding.ActivityMainBinding
import com.devanshu.weatherapp.models.Sys
import com.devanshu.weatherapp.models.WeatherResponse
import com.devanshu.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding?= null
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)


        if (!isLocationEnable()){
            Toast.makeText(this, "Your location is turned off. Turn it On", Toast.LENGTH_SHORT).show()
            val intent  = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        else{
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                .withListener(object : MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()

                        }
                        if (report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity, "You have denied permission", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permission: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                        token?.continuePermissionRequest()
                    }

                }).onSameThread().check()
        }
        
    }

    //Location is enable in cellphone
    private fun isLocationEnable(): Boolean{
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) 
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // Permission request rational dialog
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this).setMessage("It looks like you have turned off permission required for this feature. You can change them in Application settings.")
            .setPositiveButton("Go To Setting"){
                    _,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("Package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){
                    dialog,_ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest.create()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback, Looper.myLooper()!!
        )
    }

    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude ", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude","$longitude")


            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails( latitude : Double, longitude : Double){
        if (Constants.isNetworkAvailable(this)){
            //Toast.makeText(this, "Internet Available", Toast.LENGTH_SHORT).show()
        // retrofit setup

            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse>  = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        val weatherList: WeatherResponse? = response.body()

                        setupUI(weatherList!!)

                        Log.i("Response Result", "$weatherList")
                        hideProgressDialog()
                    } else{
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400","Bad Connection")
                            }
                            404->{
                                Log.e("Error 404", "Not Found")
                            }else-> {
                                Log.e("Error","Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Errorrrrr", t.message.toString())
                }

            })

        }else{
            Toast.makeText(this, "Internet not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_dialog)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if (mProgressDialog != null)
            mProgressDialog!!.dismiss()
    }


    private fun setupUI(weatherList: WeatherResponse){
        for (i in weatherList.weather.indices){
            Log.i("Weather Name", weatherList.weather.toString())
            binding?.tvMain?.text = weatherList.weather[i].main
            binding?.tvMainDescription?.text = weatherList.weather[i].description
            binding?.tvTemp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())

            binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
            binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)

            binding?.tvHumidity?.text = weatherList.main.humidity.toString() + " per cent"
            binding?.tvMin?.text = weatherList.main.temp_min.toString() + " min"
            binding?.tvMax?.text = weatherList.main.temp_max.toString() + " max"
            binding?.tvSpeed?.text = weatherList.wind.speed.toString()
            binding?.tvName?.text = weatherList.name
            binding?.tvCountry?.text = weatherList.sys.country

            when(weatherList.weather[i].icon){
                "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)

                "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)

                "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)

                "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)

                "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)

                "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)

                "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)

                "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)

                "01n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)

                "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)

                "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)

                "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)

                "11n" -> binding?.ivMain?.setImageResource(R.drawable.rain)

                "13n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)

                "50n" -> binding?.ivMain?.setImageResource(R.drawable.mist)

                "50d" -> binding?.ivMain?.setImageResource(R.drawable.mist)



            }

        }
    }

    private fun getUnit(value: String): String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex* 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}