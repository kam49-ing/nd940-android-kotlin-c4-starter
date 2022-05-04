package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.finishAffinity
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.RemindersActivity
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.BuildConfig
import org.koin.android.ext.android.inject
import java.lang.Thread.sleep
import java.util.*

const val ACTION_GEOFENCE_INTENT="LOCATION_REMINDER"
class SaveReminderFragment : BaseFragment() {
    //Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding
    private val runningQOrLater = android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.Q
    private lateinit var geofencingClient: GeofencingClient
    private var title:String? = null
    private var description:String? = null
    private var latitude:Double? = null
    private var longitude:Double? = null
    private var radius = 5000f
    private var location: String? = null
    private lateinit var id:String
    private lateinit var contxt:Context
    private val geofencePendingIntent:PendingIntent by lazy {
        val intent = Intent(this.contxt as Activity, GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_INTENT
        PendingIntent.getBroadcast(this.contxt, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)

        setDisplayHomeAsUpEnabled(true)

        binding.viewModel = _viewModel
        geofencingClient = LocationServices.getGeofencingClient(this.contxt as Activity)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
            //            Navigate to another fragment to get the user location
            _viewModel.navigationCommand.value =
                NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        binding.saveReminder.setOnClickListener {
            title = _viewModel.selectedPOI.value?.name
            description = _viewModel.reminderDescription.value
            location = _viewModel.reminderSelectedLocationStr.value
            latitude = _viewModel.selectedPOI.value?.latLng?.latitude
            longitude = _viewModel.selectedPOI.value?.latLng?.longitude
            id = UUID.randomUUID().toString()

            if (
                title == null ||
                description == null ||
                latitude == null ||
                longitude == null
            )
            {
                Snackbar.make(
                    binding.root,
                    getString(R.string.save_reminder_error_explanation),
                    Snackbar.LENGTH_LONG
                )
                    .show()
            }
            else
            {
                checkPermissionsAndStartGeofencing()
            }
        }
    }

    /**
     * Starts the permission check and Geofence process only if the Geofence associated with the
     * current hint isn't yet active.
     */
    private fun checkPermissionsAndStartGeofencing() {
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartGeofence()
        } else {
            requestForegroundAndBackgroundLocationPermissions()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON) {
            checkDeviceLocationSettingsAndStartGeofence(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contxt = context
    }

    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(contxt,
                            Manifest.permission.ACCESS_FINE_LOCATION))
        val backgroundPermissionApproved =
            if (runningQOrLater) {
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            this.contxt, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved
    }

    /**
     * foregroundPermissionApproved - Checks if foreground permission is approved
     *
     * Return - true if permission is approved, false otherwise
     */
    private fun foregroundPermissionApproved():Boolean
    {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat
            .checkSelfPermission(
                contxt,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
    }

    @TargetApi(29 )
    private fun requestForegroundAndBackgroundLocationPermissions() {
        if (foregroundAndBackgroundLocationPermissionApproved()){
            return
        }

        var permissionsArray = arrayOf<String>()
        val resultCode = when {
            runningQOrLater -> {
                if (foregroundPermissionApproved())
                {
                    permissionsArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    REQUEST_BACKGROUND_ONLY_PERMISSIONS_REQUEST_CODE
                }
                else
                {
                    permissionsArray += Manifest.permission.ACCESS_FINE_LOCATION
                    permissionsArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
                }
            }
            else -> REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
        }
        Log.d(TAG, "Request foreground only location permission")
        requestPermissions(permissionsArray, resultCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionResult")

        if (
            grantResults.isEmpty() ||
            (requestCode == REQUEST_BACKGROUND_ONLY_PERMISSIONS_REQUEST_CODE &&
                    grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED) ||
            (requestCode == REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE &&
                    grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED) ||
            (requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE &&
                    (grantResults[BACKGROUND_LOCATION_PERMISSION_INDEX] ==
                    PackageManager.PERMISSION_DENIED) || grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED))
        {
            if (
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                || !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            )
            {
                Snackbar.make(
                    binding.root,
                    R.string.permission_denied_and_dont_ask_again_explanation,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction(R.string.ok){
                        finishAffinity(contxt as Activity)
                    }
                    .show()
            }
            else
            {
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
                    .setAction(R.string.ok){
                        requestForegroundAndBackgroundLocationPermissions()
                    }
                    .show()
            }
        }
        else
        {
            checkDeviceLocationSettingsAndStartGeofence(true)
        }
    }

    private fun checkDeviceLocationSettingsAndStartGeofence(resolve:Boolean = true) {
        val locationSettingsResponseTask =
            checkDeviceLocationSettings(resolve)
        locationSettingsResponseTask?.addOnCompleteListener {
            if ( it.isSuccessful ) {
                addGeofenceForClue()
                val reminderDataItem =
                    ReminderDataItem(title, description, location, latitude, longitude, id = id)
                _viewModel.validateAndSaveReminder(reminderDataItem)
            }
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
                    Log.d(TAG, "Error getting location settings resolution: " + sendEx.message)
                }
            } else {
                Snackbar.make(
                    binding.root,
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkDeviceLocationSettings(true)
                }.show()
            }
        }
        return locationSettingsResponseTask
    }

    @SuppressLint("MissingPermission")
    private fun addGeofenceForClue() {
        val geofence = latitude?.let {
            longitude?.let { it1 ->
                Geofence.Builder()
                    .setCircularRegion(it, it1, radius)
                    .setRequestId(id)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
            }
        }
        val geofenceRequest = GeofencingRequest.Builder()
            .addGeofence(geofence)
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .build()

        geofencingClient.removeGeofences(geofencePendingIntent)?.run {
            addOnCompleteListener {
                geofencingClient.addGeofences(geofenceRequest, geofencePendingIntent)?.run {
                    addOnSuccessListener {
                        Toast.makeText(
                            contxt,
                            contxt.getString(R.string.geofence_added),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    addOnFailureListener {
                        Toast.makeText(contxt, "An exception occurred: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

}


private const val LOCATION_PERMISSION_INDEX = 0
private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1
private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
private const val REQUEST_BACKGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 35