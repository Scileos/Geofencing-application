/**TODO
 * upload to github/lab
 */



package moorej22.dissertation_geofencingappliaction;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import me.tittojose.www.timerangepicker_library.TimeRangePickerDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends FragmentActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback, LocationListener, ResultCallback<Status>, TimeRangePickerDialog.OnTimeRangeSelectedListener {

    private GoogleApiClient mGoogleApiClient;
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;
    private Location mLastKnownLocation;
    private boolean mLocationPermissionGranted;
    LocationRequest mLocationRequest = LocationRequest.create();
    protected ArrayList<Geofence> mGeofenceList;
    private PendingIntent mGeofencePendingIntent;
    SharedPreferences prefs = null;
    private String m_Text = null;
    List<Address> addresses = null;
    LatLng latLng;
    Circle circle;

    AlarmManager am;
    PendingIntent pendingAlarmIntent;


    BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.i("Alarm", "Pinging Geofence");
            populateGeoFenceList();
            addGeofences();
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Get shared preferences file
        prefs = getSharedPreferences("myPreferences", MODE_PRIVATE);

        //Initialise Geofence list
        mGeofenceList = new ArrayList<>();

        //On button click, ask for new home location and time range
        final Button changeSettings = (Button) findViewById(R.id.changeSettings);
        changeSettings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setHomeLocation();
                setTimeRange();
            }
        });

        //Check if permissions are granted, if not, ask for them
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_NETWORK_STATE },0);
        } else {

            //Initialise Pending Intent
            mGeofencePendingIntent = null;

            buildGoogleApiClient();
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED
                    && grantResults[2] == PackageManager.PERMISSION_GRANTED) {

                buildGoogleApiClient();
                mGoogleApiClient.connect();
                updateLocationUI();
                getDeviceLocation();

            }
        }

    protected void setHomeLocation() {

        //Build new alert dialog box which asks for a home location, this will be used to define the geofence
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Add Home Address")
                .setMessage("Please insert your home address to use as a base location (Postcode should be sufficient)");

        //Initialise input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Take in user input
                m_Text = input.getText().toString();
                //If user input is blank display error message and break
                if (input.getText().toString().matches("")) {
                    Toast.makeText(MainActivity.this, "Invalid address", Toast.LENGTH_SHORT).show();
                    return;
                }
                //Initialise geo coder which returns with a LatLong value of user input if available
                Geocoder geocoder = new Geocoder(getBaseContext());

                try {
                    //Get LatLong value from location name
                    addresses = geocoder.getFromLocationName(m_Text, 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //If unable to find a location from user input display error message and break
                if (addresses.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Unable to find address", Toast.LENGTH_SHORT).show();
                    return;
                }

                //Get Latitude and longitude to store in Shared Preferences file
                latLng = new LatLng(addresses.get(0).getLatitude(), addresses.get(0).getLongitude());

                //Store LatLng values
                prefs.edit().putLong("fenceLat", Double.doubleToLongBits(latLng.latitude)).commit();
                prefs.edit().putLong("fenceLon", Double.doubleToLongBits(latLng.longitude)).commit();

                mGeofencePendingIntent = null;

                populateGeoFenceList();
                addGeofences();
            }
        });
        builder.show();

    }

    protected void setTimeRange() {
        //Create new dialog from imported library, dialog will take a users startTime and endTime inputs
       final TimeRangePickerDialog timePickerDialog = TimeRangePickerDialog.newInstance(this, true);
        timePickerDialog.show(this.getSupportFragmentManager(),"TIMEPICKER");
    }





    @Override
    public void onResume() {
        super.onResume();

        //If this is the firsrt time the application is run on this device, ask for home location and time range
        if (prefs.getBoolean("firstrun", true)) {
            setHomeLocation();
            setTimeRange();
            prefs.edit().putBoolean("firstrun", false).commit();

        } else {

            am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            Intent alarmIntent = new Intent("alarm");
            pendingAlarmIntent= PendingIntent.getBroadcast(this, 1, alarmIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT);


            registerReceiver(br, new IntentFilter("alarm"));

            //Set alarm
            am.cancel(pendingAlarmIntent);
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingAlarmIntent);
        }
    }

    @Override
    public void onStop() {
        //AUTO GENERATED METHOD
        super.onStop();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }


    @Override
    public void onConnected(Bundle bundle) {

        //Initialise map fragment and set it as a googleMap instance

        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Request location updates, this allows the app to recognise when you move location, does not need permissions check as method will not run without them
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

        populateGeoFenceList();
        addGeofences();

    }

    @Override
    public void onConnectionSuspended(int i) {
        //AutoGenerated method
    }

    @Override
    public void onMapReady(GoogleMap map) {

        //If permissions are not granted then ask for them

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_NETWORK_STATE },0);
        }

            mMap = map;

            updateLocationUI();

            getDeviceLocation();


            //If map already has a marker stored, then add it to the mapInstance
            if (prefs.getLong("fenceLat", Double.doubleToLongBits(0.00)) != 0){
                addMarkerForFence();
            }

    }



    private void updateLocationUI() {

        //If permission granted then allow the user to move map camera to their location
        if (mMap == null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        }

        if (mLocationPermissionGranted) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        }
    }


    private void getDeviceLocation() {

        //Get device location and display it on the map instance, move camera accordingly
        if (mLastKnownLocation == null) {
            mLastKnownLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }
        if (mCameraPosition != null) {
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(mCameraPosition));
        } else if (mLastKnownLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude()), 15));
        }
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //AutoGenerated method
    }

    @Override
    public void onLocationChanged(Location location) {

        //On location change, update map accordingly to show new location
        mLastKnownLocation = location;
        getDeviceLocation();

    }

    public void populateGeoFenceList() {

        //Populate the geofence list with the fences to be added, the entries in this list will be added in addGeofences()

        mGeofenceList.add(new Geofence.Builder()

                // Set the request ID of the geofence. This is a string to identify this geofence.
                .setRequestId("Home Location")

                //Fence region will be taken from shared preferences values assigned by the user
                .setCircularRegion(
                        Double.longBitsToDouble(prefs.getLong("fenceLat", Double.doubleToLongBits(0.00) )),
                        Double.longBitsToDouble(prefs.getLong("fenceLon", Double.doubleToLongBits(0.00))),
                        300
                )
                //Fences will never expire, only be overwritten
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_EXIT | Geofence.GEOFENCE_TRANSITION_DWELL | Geofence.GEOFENCE_TRANSITION_ENTER)
                .setLoiteringDelay(1000) //12000000ms = 20 minutes
                .build());
    }

    public void addGeofences() {
        //Add geofences defined in populateGeofenceList()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //GeofencingApi.addGeofences() creates the fence request and pending intent dynamically, it also returns a callback result which is referenced later
        LocationServices.GeofencingApi.addGeofences(mGoogleApiClient,
                getGeofencingRequest(),
                getGeofencePendingIntent()
        ).setResultCallback(this);
    }

    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }

    //PendingIntent starts the GeofenceTransitionIntentService, this service listens for transitions from the sent intent and completed tasks associated with them
    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling addGeofences()
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    //Creates a visual representation of the Geofence on the map
    public void addMarkerForFence() {

        //If there is already a circle object, then remove it first. Stops multiple markers on map
        if (circle != null) {
            circle.remove();
        }

        //Define circle using the fence values stored in shared preferences as reference
        CircleOptions circleOptions = new CircleOptions()
                .center(new LatLng(Double.longBitsToDouble(prefs.getLong("fenceLat", Double.doubleToLongBits(0.00) )), Double.longBitsToDouble(prefs.getLong("fenceLon", Double.doubleToLongBits(0.00)))))
                .radius(300)
                .fillColor(0x40ff0000)
                .strokeColor(Color.TRANSPARENT)
                .strokeWidth(2);

        //Add circle to the map using defined options
         circle = mMap.addCircle(circleOptions);
    }

    @Override
    public void onResult(@NonNull Status status) {

    // On result of adding geofence update the map UI with circle object representing Geofence
        if (status.isSuccess()) {
            addMarkerForFence();
            Log.i("tag", "success");
        }
    }

    //Used to convert time values stored to that displayed on a clock eg: 6:05am before this would return
    //HOUR = "6", MINUTE = "5"
    //Converted time changes this to
    //HOUR = "06", MINUTE = "05"
    //This is important for checking time values later
    public String convertTime (int input) {
        if (input >= 10) {
            return String.valueOf(input);
        } else {
            return "0" + String.valueOf(input);
        }
    }

    @Override
    public void onTimeRangeSelected(int startHour, int startMin, int endHour, int endMin) {

        //When time range is selected, store the values in shared preferences

        String convertedStartMin = convertTime(startMin);
        String convertedEndMin = convertTime(endMin);
        String convertedStartHour = convertTime(startHour);
        String convertedEndHour = convertTime(endHour);

        prefs.edit().putString("startHour", convertedStartHour).commit();
        prefs.edit().putString("startMin", convertedStartMin).commit();
        prefs.edit().putString("endHour", convertedEndHour).commit();
        prefs.edit().putString("endMin", convertedEndMin).commit();


    }



}
