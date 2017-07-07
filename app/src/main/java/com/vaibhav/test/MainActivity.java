package com.vaibhav.test;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public ServiceResultReceiver receiver;

    //to get the reference of the start time of the service to calculate the service elapsed time
    public Long serviceStartTime;

    Button startService, stopService;
    TextView startTime, status;

    boolean permissionGranted;
    Context mContext;
    Handler handler;
    boolean mBound = false;
    Messenger mService = null;
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (mBound && serviceStartTime != null) {
                Long timeDelta = UtilityClass.getUTC() - serviceStartTime;
                startTime.setText("" + TimeUnit.MILLISECONDS.toMinutes(timeDelta) + " Minutes");
                Log.e(TAG, "Time Updated");
            } else {
                if (mBound) {
                    Log.e(TAG, "start time not available");
                    // Create and send a message to the service, using a supported 'what' value
                    Message msg = Message.obtain(null, UtilityClass.GET_START_TIME, 0, 0);
                    try {
                        mService.send(msg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
            handler.postDelayed(runnable, 60000);
        }
    };
    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            mService = new Messenger(service);
            mBound = true;
            status.setText("Running");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = MainActivity.this;
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        initializeViewId();

        //Check if the required permissions are granted. Required for system after API 23
        checkFileWritePermission();

        // Setup the callback for when data is received from the service
        setupServiceReceiver();

        startService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (permissionGranted) {
                    //start Service
                    if (UtilityClass.isLocationEnabled(mContext)) {
                        startServiceAndBind();
                    } else {
                        /*
                         * In case the user has his location service turned off we prompt the user
                         * to start the service
                         * */
                        final AlertDialog dialog = UtilityClass.ShowAlertDialog(MainActivity.this, mContext.getResources().getString(R.string.turn_on_location), "OK", "Cancel");
                        Button btnPositive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        btnPositive.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(myIntent);
                                dialog.dismiss();
                            }
                        });
                        Button btnNegative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                        btnNegative.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                dialog.dismiss();
                            }
                        });
                    }
                }
            }
        });

        stopService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBound) {
                    Message msg = Message.obtain(null, UtilityClass.STOP_SERVICE, 0, 0);
                    try {
                        mService.send(msg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    unbindService(mConnection);
                    mBound = false;
                }

            }
        });

    }

    /**
     * This function is called when the user either presses the start service button or the app
     * resumed and called onResume() to bind to the service again.
     * This function is responsible for setting up the alarm manager to keep polling the service
     * start and keeping it alive
     */
    private void startServiceAndBind() {
        startTime.setText("0 Minutes");
        AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(this, OnAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                i, 0);

        mgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60000,
                300000,
                pi);
        Intent serviceIntent = new Intent(mContext, LocationWriteService.class);
        serviceIntent.putExtra("receiver", receiver);
        startService(serviceIntent);
        if (!mBound)
            bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
        updateServiceRunningTime();
    }

    // Setup the callback for when data is received from the service
    public void setupServiceReceiver() {
        receiver = new ServiceResultReceiver(new Handler());
        // This is where we specify what happens when data is received from the service
        receiver.setReceiver(new ServiceResultReceiver.Receiver() {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == RESULT_OK) {
                    serviceStartTime = resultData.getLong("startTime");
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*Check if the service is running on app resume and  bing to the service to get relevant data*/
        if (UtilityClass.isMyServiceRunning(MainActivity.this, LocationWriteService.class)) {
            startServiceAndBind();
            status.setText("Running");
        } else {
            status.setText("Not Running");
            stopService.setEnabled(false);
            stopService.setOnClickListener(null);
        }
    }

    /**
     * Start a handler to update the service running time as long as the activity is running and
     * remove the handler otherwise, initially the call is made at 500 millisecond but later the
     * handler is called every 1 minute
     */
    private void updateServiceRunningTime() {
        if (handler == null) {
            handler = new Handler();
        }
        handler.postDelayed(runnable, 500);
    }

    /**
     * Check if the necessary permissions were granted before starting the application, in case the
     * permissions were not granted prompt he user to grant the permissions
     */
    private void checkFileWritePermission() {
        if (!UtilityClass.hasPermissions(this, UtilityClass.PERMISSIONS)) {
            permissionGranted = false;
            ActivityCompat.requestPermissions(this, UtilityClass.PERMISSIONS, UtilityClass.PERMISSION_ALL);
        } else {
            permissionGranted = true;
        }
    }

    /**
     * this is called after the ActivityCompat.requestPermissions() to get the result for the request permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case UtilityClass.PERMISSION_ALL:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    permissionGranted = true;
                } else {
                    if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    }
                    if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION);
                    }
                    Toast.makeText(MainActivity.this, "The app was not allowed some permissions, some of the app functionality might not work", Toast.LENGTH_LONG).show();
                }
        }
    }

    /**
     * Initializing the view ID for the activity
     */
    private void initializeViewId() {
        startService = (Button) findViewById(R.id.startService);
        stopService = (Button) findViewById(R.id.stopService);
        startTime = (TextView) findViewById(R.id.startTime);
        status = (TextView) findViewById(R.id.status);
    }


    /**
     * If the activity was removed from foreground for a long time and was stopped by either
     * swiping it, but since the service was bound to the activity we have to unbind the service
     * to avoid service leaks
     */
    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }
}
