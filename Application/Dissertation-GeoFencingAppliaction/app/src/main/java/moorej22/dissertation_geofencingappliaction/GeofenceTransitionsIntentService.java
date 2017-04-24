package moorej22.dissertation_geofencingappliaction;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;


//Acts as a service, and so runs in the background
public class GeofenceTransitionsIntentService extends IntentService {

    protected static final String TAG = "GeofenceTransitions";
    //Initialise taskCompleted
    Boolean taskCompleted = false;
    Long lastUpdateTime;
    //Initialise SharedPreferences
    SharedPreferences prefs = null;
    AlarmManager am;
    PendingIntent contentIntent;



    public GeofenceTransitionsIntentService() {
        super(TAG);
    }


    @Override
    public void onCreate() {
        super.onCreate();
        //Get our Shared Preferences file
        prefs = getSharedPreferences("myPreferences", MODE_PRIVATE);

        //Initialise an instance of alarm manager
        am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        //Initialise new pendingIntent handled by the alarmManager
        Intent notificationIntent = new Intent(this, ShowNotification.class);
        contentIntent = PendingIntent.getBroadcast(this, 0, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
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
    public void onHandleIntent(Intent intent) {


        Log.i(TAG, "intent made");

        //What to do when a Geofence transition occurs

        //Set relevant time values
        Calendar c = Calendar.getInstance();
        String startHour = prefs.getString("startHour", "00");
        String startMin = prefs.getString("startMin", "00");
        String endHour = prefs.getString("endHour", "00");
        String endMin = prefs.getString("endMin", "00");
        String currentHour = convertTime(c.get(Calendar.HOUR_OF_DAY));
        String currentMin = convertTime(c.get(Calendar.MINUTE));


        //First check if transition has an error
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            String errorMessage = "Error";
            Log.e(TAG, errorMessage);
            return;
        }

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Long timeCheck = new Date().getTime();

            //Check to see if it has been an appropriate amount of time (12h) since last taskComplete
            if (timeCheck - prefs.getLong("lastUpdateTime", 0) >= (24 * 60 * 60 * 1000) / 2)  {
                taskCompleted = false;
            } else {
                taskCompleted = true;
                Log.i(TAG, "Not been 12 hours yet");
            }

                //If haven't completed task of leaving fence
                if (taskCompleted == false) {

                    try {
                        String endTime = endHour + ":" + endMin + ":" + "00";
                        String startTime = startHour + ":" + startMin + ":" + "00";
                        String currentTime = currentHour + ":" + currentMin + ":" + "00";

                        //If current time is within user defined time range
                        if (isTimeBetweenTwoTime(startTime, endTime, currentTime)) {

                            //Set repeating alarm that starts ShowNotification.class every x minutes (approximately). Class shows a notification
                            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), contentIntent);
                        } else {
                            Log.i(TAG, "Outside of time range");
                        }

                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.i(TAG, "Task already completed");
                }
        }

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            //Task completed for the day, update relevant variables

            try {
                String endTime = endHour + ":" + endMin + ":" + "00";
                String startTime = startHour + ":" + startMin + ":" + "00";
                String currentTime = currentHour + ":" + currentMin + ":" + "00";

                //If current time is within user defined time range
                if (isTimeBetweenTwoTime(startTime, endTime, currentTime)) {

                    if (taskCompleted == false) {
                        lastUpdateTime = new Date().getTime();

                        prefs.edit().putLong("lastUpdateTime", lastUpdateTime).commit();
                        taskCompleted = true;
                        Log.i(TAG, taskCompleted.toString());
                        Log.i(TAG, "Fence Exit");
                    } else {
                        Log.i(TAG, "Exit, but task completed");
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }


    public static boolean isTimeBetweenTwoTime(String argStartTime,
                                               String argEndTime, String argCurrentTime) throws ParseException {
        String reg = "^([0-1][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])$";
        //
        if (argStartTime.matches(reg) && argEndTime.matches(reg)
                && argCurrentTime.matches(reg)) {
            boolean valid = false;
            // Start Time
            java.util.Date startTime = new SimpleDateFormat("HH:mm:ss")
                    .parse(argStartTime);
            Calendar startCalendar = Calendar.getInstance();
            startCalendar.setTime(startTime);

            // Current Time
            java.util.Date currentTime = new SimpleDateFormat("HH:mm:ss")
                    .parse(argCurrentTime);
            Calendar currentCalendar = Calendar.getInstance();
            currentCalendar.setTime(currentTime);
            Log.i(TAG, argCurrentTime);

            // End Time
            java.util.Date endTime = new SimpleDateFormat("HH:mm:ss")
                    .parse(argEndTime);
            Calendar endCalendar = Calendar.getInstance();
            endCalendar.setTime(endTime);

            //
            if (currentTime.compareTo(endTime) < 0) {

                currentCalendar.add(Calendar.DATE, 1);
                currentTime = currentCalendar.getTime();

            }

            if (startTime.compareTo(endTime) < 0) {

                startCalendar.add(Calendar.DATE, 1);
                startTime = startCalendar.getTime();

            }
            //
            if (currentTime.before(startTime)) {

                valid = false;
            } else {

                if (currentTime.after(endTime)) {
                    endCalendar.add(Calendar.DATE, 1);
                    endTime = endCalendar.getTime();

                }

                if (currentTime.before(endTime)) {
                    valid = true;
                } else {
                    valid = false;
                }

            }
            return valid;

        } else {
            throw new IllegalArgumentException(
                    "Not a valid time, expecting HH:MM:SS format");
        }

    }

}
