package com.vaibhav.test;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import java.util.Iterator;

/**
 * Created by Vaibhav Barad on 4/7/17.
 */

/* https://developer.android.com/guide/components/bound-services.html
 * use bound service to return result from service to activity to avoid data loss if the service was started at boot time
 * */

@RequiresApi(api = Build.VERSION_CODES.N)
public class LocationWriteService extends Service {
    public static final String LOCK_NAME_STATIC = "LocationWriteService.Static";
    private static final String TAG = "LocationWriteService";
    private static final int LOCATION_INTERVAL = 1000 * 60 * 2;
    private static final int SYSTEM_HEALTH_INTERVAL = 1000 * 60 * 10;
    private static final float LOCATION_DISTANCE = 0f;
    private static PowerManager.WakeLock lockStatic = null;
    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    public Long serviceStartTime;
    ResultReceiver receiver;
    // Binder given to clients
    int satelliteCount = 0;
    int batteryLevel;
    Handler handler;
    HandlerThread handlerThread;
    LocationListener[] mLocationListeners = new LocationListener[]{
            new LocationListener(LocationManager.GPS_PROVIDER)
    };

    private String ACTIVE_FILE = "active";
    private String IDLE_FILE = "idle";
    private String SYSTEM_HEALTH_FILE = "health";
    private String FAIL_ENTRY = "fail"; //test file when location is not recorded as validation failed
    private int locationChangeCounter = 0;
    private LocationManager mLocationManager = null;

    //Listener to get the satellite count for API after Nougat
    GnssStatus.Callback GnssStatusCallback;

    Runnable batteryRunnable = new Runnable() {
        @Override
        public void run() {
            acquireStaticLock(LocationWriteService.this);
            batteryLevel = UtilityClass.getBatteryPercentage(LocationWriteService.this);
            StringBuilder builder = new StringBuilder();
            builder.append("Date:" + UtilityClass.getCurrentDate())
                    .append("\n")
                    .append("Battery Level:")
                    .append(batteryLevel)
                    .append("%")
                    .append("\n")
                    .append(UtilityClass.getNetworkInfo(LocationWriteService.this))
                    .append("\n\n");
            Log.e(TAG, "BatteryData: " + builder.toString());
            UtilityClass.generateNoteOnSD(LocationWriteService.this, SYSTEM_HEALTH_FILE, builder.toString(), serviceStartTime);
            Toast.makeText(LocationWriteService.this, "System health write Complete\n" + builder.toString(), Toast.LENGTH_SHORT).show();
            getLock(LocationWriteService.this).release();
            handler.postDelayed(batteryRunnable, SYSTEM_HEALTH_INTERVAL);
        }
    };

    //Listener to get the satellite count
    GpsStatus.Listener GpsStatuslistner = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            satelliteCount = 0;
            if (mLocationManager != null) {
                try {
                    GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
                    if (gpsStatus != null) {
                        Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
                        Iterator<GpsSatellite> sat = satellites.iterator();
                        while (sat.hasNext()) {
                            GpsSatellite satellite = sat.next();
                            if (satellite.usedInFix()) {
                                satelliteCount++;
                            }
                            Log.d("Satellite Count", "" + satelliteCount);
                        }
                    }
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    // this used to get a wakelock when a file is to be written to as it may so happen the CPU is asleep
    public static void acquireStaticLock(Context context) {
        getLock(context).acquire();
    }

    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (lockStatic == null) {
            PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            lockStatic = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    LOCK_NAME_STATIC);
            lockStatic.setReferenceCounted(true);
        }
        return (lockStatic);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        // Extract the receiver passed into the service by the MainActivity and initialize the ResultReceiver.
        if (intent != null && intent.getParcelableExtra("receiver") != null)
            receiver = intent.getParcelableExtra("receiver");
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate");
        serviceStartTime = UtilityClass.getUTC();

        /**reference:
         *  https://medium.com/@ali.muzaffar/handlerthreads-and-why-you-should-be-using-them-in-your-android-apps-dc8bf1540341
         *  https://guides.codepath.com/android/Managing-Threads-and-Custom-Services#executing-runnables-on-handlerthread
         */

        // Creates a new background thread for processing messages or runnables sequentially
        handlerThread = new HandlerThread("LocationThread");
        // Starts the background thread
        handlerThread.start();

        // Create a handler attached to the HandlerThread's Looper
        Handler mHandler = new Handler(handlerThread.getLooper());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                initializeLocationManager();
                //run an handler to get/write battery level and network info every 10 minutes
                if (handler == null) {
                    handler = new Handler();
                }
                handler.postDelayed(batteryRunnable, SYSTEM_HEALTH_INTERVAL);
                try {
                    mLocationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                            mLocationListeners[0]);

                    if (UtilityClass.isBuildNougat()) {
                        GnssStatusCallback = new GnssStatus.Callback() {
                            @Override
                            public void onSatelliteStatusChanged(GnssStatus status) {
                                super.onSatelliteStatusChanged(status);
                                if (status != null) {
                                    final int length = status.getSatelliteCount();
                                    int index = 0;
                                    satelliteCount = 0;
                                    while (index < length) {
                                        if (status.usedInFix(index)) {
                                            satelliteCount++;
                                        }
                                        index++;
                                    }
                                    Log.d("Satellite Count", "" + satelliteCount);
                                }
                            }
                        };
                        mLocationManager.registerGnssStatusCallback(GnssStatusCallback);
                    } else
                        mLocationManager.addGpsStatusListener(GpsStatuslistner);

                } catch (java.lang.SecurityException ex) {
                    Log.i(TAG, "fail to request location update, ignore", ex);
                } catch (IllegalArgumentException ex) {
                    Log.d(TAG, "gps provider does not exist " + ex.getMessage());
                }
            }
        });

    }


    /*
    * removes the handler responsible for getting/writing the battery information and handlerThread
    * is also quit to stop the service process and finally the service itself is stopped , also
    * removes all location updates for the specified LocationListener.
    * */
    public void releaseResources() {
        Log.e(TAG, "releaseResources");
        super.onDestroy();
        if (lockStatic!=null && lockStatic.isHeld())
            getLock(this).release();

        handler.removeCallbacks(batteryRunnable);
        handlerThread.quitSafely();
        stopSelf();
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listners, ignore", ex);
                }
            }
        }
    }

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = new Bundle();
            switch (msg.what) {
                case UtilityClass.GET_START_TIME:
                    bundle.putLong("startTime", serviceStartTime);
                    receiver.send(Activity.RESULT_OK, bundle);
                    break;
                case UtilityClass.STOP_SERVICE:
                    releaseResources();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;

        public LocationListener(String provider) {
            Log.e(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.e(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);
            locationChangeCounter += 2;
            acquireStaticLock(LocationWriteService.this);
            StringBuilder builder = new StringBuilder();
            builder.append("Date:" + UtilityClass.getCurrentDate())
                    .append("\n")
                    .append("Latitude: ")
                    .append(location.getLatitude())
                    .append("\n")
                    .append("Longitude: ")
                    .append(location.getLongitude())
                    .append("\n")
                    .append("UTC Time: ")
                    .append(location.getTime())
                    .append("\n")
                    .append("Velocity: ")
                    .append(location.getSpeed() * 3.6)/*1 m/s	*	3600 m/hr/1 m/s	*	1 km/hr/1000 m/hr	=	3.6 km/hr therefore 1m/s -3.6km/h*/
                    .append("\n")
                    .append("Satellite Count: ")
                    .append(satelliteCount)
                    .append("\n")
                    .append("Accuracy: ")
                    .append(location.getAccuracy())
                    .append("\n\n");
            Log.e("Data to be stored", builder.toString());
            if (location.getProvider() != null
                    && location.getProvider().equalsIgnoreCase(LocationManager.GPS_PROVIDER)
                    && satelliteCount >= 5
                    && location.getAccuracy() >= 2
                    && location.getAccuracy() <= 15) {
                if (UtilityClass.generateNoteOnSD(LocationWriteService.this, ACTIVE_FILE, builder.toString(), serviceStartTime)) {
                    Toast.makeText(LocationWriteService.this, "Active write Complete\n" + builder.toString(), Toast.LENGTH_SHORT).show();
                }
                if (locationChangeCounter == 30) {
                    UtilityClass.generateNoteOnSD(LocationWriteService.this, IDLE_FILE, builder.toString(), serviceStartTime);
                    Toast.makeText(LocationWriteService.this, "Idle write Complete\n" + builder.toString(), Toast.LENGTH_SHORT).show();
                    locationChangeCounter = 0;
                }
            } else {
                UtilityClass.generateNoteOnSD(LocationWriteService.this, FAIL_ENTRY, builder.toString(), serviceStartTime);
                Toast.makeText(LocationWriteService.this, "Fail write Complete\n" + builder.toString(), Toast.LENGTH_SHORT).show();
            }
            getLock(LocationWriteService.this).release();
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.e(TAG, "onProviderDisabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.e(TAG, "onProviderEnabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.e(TAG, "onStatusChanged: " + provider);
        }
    }
}
