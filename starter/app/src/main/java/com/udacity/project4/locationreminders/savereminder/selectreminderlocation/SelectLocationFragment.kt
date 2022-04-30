package com.udacity.project4.locationreminders.savereminder.selectreminderlocation


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.util.Log
import android.view.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import java.util.*

class SelectLocationFragment : BaseFragment(){

    //Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding
    private lateinit var map: GoogleMap
    private var poi: MutableLiveData<PointOfInterest?> = MutableLiveData(null)
    private val REQUEST_LOCATION_PERMISSION = 1
    private val REQUEST_BACKGROUND_LOCATION = 2
    private val REQUEST_CHECK_SETTINGS = 3
    private val TAG = "SELECTFRAGMENTMAP"
    private var userLocation:Location?=null
    private lateinit var contxt:Context


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_select_location, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this
        poi.value = null

        poi.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            binding.saveLocation.isEnabled = it != null
        })

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync{
            googleMap ->
            map = googleMap

            val longitude = 65.9667
            val latitude = 40.73061

            val latLng = LatLng(longitude, latitude)
            val zoom = 15f

            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))

            checkDeviceLocationSettings(true)

            val androidOverlay = GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromResource(R.drawable.android))
                .position(latLng, 15f)
            map.addGroundOverlay(androidOverlay)


            setMapStyle(map)
            setMapLongClickListener(map)
            setOnPoiSelected(map)

            Log.i(TAG, "map ready")
        }
        binding.saveLocation.setOnClickListener {
            onLocationSelected()
        }


        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contxt = context
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (isPermissionGranted()) {
            map.isMyLocationEnabled = true
            getUserLocation()
            if (userLocation != null){
                val latLng = LatLng(userLocation!!.latitude, userLocation!!.longitude)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }
        requestLocationPermission()
    }

    @SuppressLint("MissingPermission")
    private fun getUserLocation(){
        val locationManager = (contxt as Activity).getSystemService(Context.LOCATION_SERVICE) as LocationManager
        userLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (userLocation == null){
            val criteria = Criteria()
            criteria.accuracy = Criteria.ACCURACY_COARSE
            userLocation = locationManager.getBestProvider(criteria, true)
                ?.let { locationManager.getLastKnownLocation(it) }
        }
        Log.i(TAG, "this is user location: $userLocation")
    }

    private fun onLocationSelected() {

        if (poi.value == null ){
            Snackbar.make(this.requireView(), "Please select a poi before continuing !",Snackbar.LENGTH_LONG).show()
        }else {
            _viewModel.latitude.value = poi.value?.latLng?.latitude
            _viewModel.longitude.value = poi.value?.latLng?.longitude
            _viewModel.selectedPOI.value = poi.value
            findNavController().popBackStack(R.id.saveReminderFragment, false)
        }



    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }


    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON)
            checkDeviceLocationSettings(false)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Check if location permissions are granted and if so enable the
        // location data layer.
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                enableMyLocation()
            }
            else
            {
                showSnackbar()
            }
        }
        if (requestCode == REQUEST_BACKGROUND_LOCATION)
        {
            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED
            )
            {
                enableMyLocation()
            }
            else
            {
                showSnackbar()
            }
        }
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    private fun showSnackbar() {
        Snackbar.make(
            binding.root,
            R.string.permission_denied_explanation,
            Snackbar.LENGTH_INDEFINITE
        )
            .setAction(R.string.settings) {
                startActivity(Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            .setAction(R.string.ok) {
                requestLocationPermission()
            }
            .show()
    }

    /**
     * requestLocationPermission - request the location fine location
     *
     * Return: Nothing
     */
    private fun requestLocationPermission()
    {
        val hasForegroundPermission = ActivityCompat.checkSelfPermission(
            contxt,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            requestQOrLaterPermission()
        }
        else
        {
            if (hasForegroundPermission)
            {
                checkDeviceLocationSettings(true)
            }
            else
            {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
                )
            }

        }
    }

    /**
     * requestQOrLaterPermission - requests permission for devices running Q or Later
     *
     * Return: Nothing
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestQOrLaterPermission()
    {

        val hasForegroundPermissionAndBackgroundPermission = ActivityCompat.checkSelfPermission(
            contxt,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            contxt,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasForegroundPermissionAndBackgroundPermission)
        {
            checkDeviceLocationSettings(true)
        }
        else
        {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_BACKGROUND_LOCATION
            )
        }
    }

    private fun setMapLongClickListener(map:GoogleMap){


        map.setOnMapLongClickListener {position->

            val snipet = String.format(
                Locale.getDefault(),
                "Lat: %1$.5f, Long: %2$.5f",
                position.latitude, position.longitude
            )

            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(getString(R.string.dropped_pin))
                    .snippet(snipet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
            this.poi.value = PointOfInterest(LatLng(position.latitude, position.longitude), getString(R.string.dropped_pin), "point of interest")
        }


    }


    private fun setOnPoiSelected(map: GoogleMap){
        map.setOnPoiClickListener{poi->
            val poiMarker = map.addMarker(
                MarkerOptions()
                    .position(poi.latLng)
                    .title(poi.name)
            )
            poiMarker?.showInfoWindow()
            this.poi.value = poi
        }
    }




    private fun isPermissionGranted() : Boolean {
        return ContextCompat.checkSelfPermission(
            contxt,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }


    private fun setMapStyle(map: GoogleMap){
        try {
            val success = map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this.requireContext(), R.raw.map_style))
            if (!success) {
                Log.e(ContentValues.TAG, "Style parsing failed.")
            }else{
                Log.i(TAG, "parse style successfully")
            }
        }catch (e: Resources.NotFoundException){
            Log.e(ContentValues.TAG, "Can't find style. Error: ", e)
        }
    }

    fun checkDeviceLocationSettings(
        resolve: Boolean
    ): Task<LocationSettingsResponse>? {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val settingsClient = this.activity?.let { LocationServices.getSettingsClient(it) }
        val locationSettingsResponseTask =
            settingsClient?.checkLocationSettings(builder.build())
        locationSettingsResponseTask?.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && resolve) {
                try {
                    startIntentSenderForResult(
                        exception.resolution.intentSender,
                        REQUEST_TURN_DEVICE_LOCATION_ON,
                        null, 0, 0, 0, null
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d(ContentValues.TAG, "Error getting location settings resolution: " + sendEx.message)
                }
            } else {
                Snackbar.make(
                    binding.root,
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkDeviceLocationSettings(true)
                }.show()
            }
        }?.addOnSuccessListener {
            enableMyLocation()
        }
        return locationSettingsResponseTask
    }
}
private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
