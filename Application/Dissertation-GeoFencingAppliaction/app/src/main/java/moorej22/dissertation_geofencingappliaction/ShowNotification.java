package moorej22.dissertation_geofencingappliaction;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;


public class ShowNotification extends BroadcastReceiver {

    private final static String TAG = "ShowNotification";


    @Override
    public void onReceive(Context context, Intent intent) {

        //When alarm is called, show notification

        Intent mainIntent = new Intent(context, GeofenceTransitionsIntentService.class);

        NotificationManager notificationManager
                = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        //Build new notification
        Notification noti = new NotificationCompat.Builder(context)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setContentTitle("Move Location")
                .setContentText("Remember to move outside the fence once per day")
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.drawable.ic_explore_black_24dp)
                .setTicker("ticker message")
                .setWhen(System.currentTimeMillis())
                .build();

        //Show this notification
        notificationManager.notify(0, noti);

        //Play default notification tone along with notification
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(context, notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.i(TAG, "Notification created");

    }
}
