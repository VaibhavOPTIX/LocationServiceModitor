package com.vaibhav.test;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by Vaibhav Barad on 4/7/17.
 */

public class UtilityClass {

    public static final int PERMISSION_ALL = 1;
    public static final String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION};
    public static final int GET_START_TIME = 1;
    public static final int STOP_SERVICE = 2;
    private final String LOG_TAG = "UtilityClass";

    /*Check if the necessary permissions were granted and return boolean */
    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }
    /* Check if the phone is running  Nougat and above
     * This was to add GnssStatus.Callback instead of GpsStatus.Listener to get the Satellites
     * information as GpsStatus.Listener is deprecated
     * */
    public static boolean isBuildNougat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return true;
        }
        return false;
    }

    // to check if the Location setting is enabled or not
    public static boolean isLocationEnabled(Context context) {
        int locationMode = 0;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }

            return locationMode != Settings.Secure.LOCATION_MODE_OFF;

        } else {
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }


    public static Long getUTC() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        return cal.getTimeInMillis();
    }

    public static String getCurrentDate() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR);
        int minute = c.get(Calendar.MINUTE);
        int second = c.get(Calendar.SECOND);
        return "" + hour + ":" + minute + ":" + second;
    }


    public static AlertDialog ShowAlertDialog(Context mContext, String message, String PositiveButton, String NegativeButton) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setMessage(message);
        builder.setCancelable(true);
        builder.setNegativeButton(NegativeButton, null);
        builder.setPositiveButton(PositiveButton, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        return alertDialog;
    }


    /** This function writes to the file with the data passed
     *  @param context : Context of the calling component/service
     *  @param sFileName : the file name type indicating the file time being written to. values can
     *                      be "active","idle" or "health"
     *  @param sBody : the content that is to be written
     *  @param startTime : the Service start time, that will be used to reference the other file of
     *                      the "sFileName" to query out the most recent file*/
    public static boolean generateNoteOnSD(Context context, String sFileName, String sBody, Long startTime) {
        try {
            File root = new File(Environment.getExternalStorageDirectory(), "Numadic_Data");
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = getFile(root, sFileName, startTime);
            FileWriter writer = new FileWriter(gpxfile, true);
            writer.append(sBody);
            writer.flush();
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** This function is responsible to get the most recent file to write to, here all the files in
     *  the directory are queried, after which the files are filtered for the file type being
     *  written for and finally is is compared to the service start time to get the most recent
     *  after which a final size check is done to confirm file is <1MB else spawn a new file and return that*/
    private static File getFile(File root, String sFileName, Long startTime) {
        String tempName = sFileName + "_" + startTime + ".txt";
        List<String> files = new ArrayList<>();
        List<String> files_temps = new ArrayList<>();
        File tempFile = new File(root, tempName);
        Long size = FileSize(tempFile);
        if (size != null && size < 1.0) {
            return tempFile;
        } else {
            //get all files in the directory
            for (File f : root.listFiles()) {
                if (f.isFile()) {
                    files.add(f.getName());
                }
            }

            for (String fileName : files) {
                if (fileName.split("_")[0].equalsIgnoreCase(sFileName)) {
                    files_temps.add(fileName);
                }
            }
            Collections.sort(files_temps, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    if (Long.parseLong(o2.split("_")[1].split(".")[0]) == Long.parseLong(o1.split("_")[1].split(".")[0]))
                        return 0;
                    else if (Long.parseLong(o2.split("_")[1]) > Long.parseLong(o1.split("_")[1]))
                        return 1;
                    else
                        return -1;
                }
            });

            tempFile = new File(root, files_temps.get(0));
            size = FileSize(tempFile);
            if (size != null && size >= 1.0)
                return new File(root, sFileName + "_" + getUTC() + ".txt");
            else
                return tempFile;
        }
    }

    /*Check if the service is running on app resume and  bing to the service to get relevant data*/
    public static boolean isMyServiceRunning(Context mContext, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static int getBatteryPercentage(Context context) {

        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);

        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;

        float batteryPct = level / (float) scale;

        return (int) (batteryPct * 100);
    }

    public static String getNetworkInfo(Context context) {
        String connectionData = "";
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) { // connected to the internet
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                connectionData += "Connected to : WIFI\n";
                WifiManager wifiManager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();
                connectionData += "SSID/Carrier Name: " + ssid + "\n";
                connectionData += "Link/Signal: " + wifiInfo.getRssi() + " dBm\n";

            } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                String carrierName = manager.getNetworkOperatorName();
                connectionData += "Connect to: Mobile Data\n";
                connectionData += "SSID/Carrier Name: " + carrierName + "\n";
                connectionData += "Link/Signal: " + getSignalStrength(manager) + " dBm\n";
            }
        } else {
            connectionData = "You do not have a active data connection";
        }
        return connectionData;
    }

    private static int getSignalStrength(TelephonyManager manager) {
        int strength = 0;
        List<CellInfo> cellInfos = manager.getAllCellInfo();   //This will give info of all sims present inside your mobile
        if (cellInfos != null) {
            for (int i = 0; i < cellInfos.size(); i++) {
                if (cellInfos.get(i).isRegistered()) {
                    if (cellInfos.get(i) instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) manager.getAllCellInfo().get(0);
                        CellSignalStrengthWcdma cellSignalStrengthWcdma = cellInfoWcdma.getCellSignalStrength();
                        strength = cellSignalStrengthWcdma.getDbm();
                    } else if (cellInfos.get(i) instanceof CellInfoGsm) {
                        CellInfoGsm cellInfogsm = (CellInfoGsm) manager.getAllCellInfo().get(0);
                        CellSignalStrengthGsm cellSignalStrengthGsm = cellInfogsm.getCellSignalStrength();
                        strength = cellSignalStrengthGsm.getDbm();
                    } else if (cellInfos.get(i) instanceof CellInfoLte) {
                        CellInfoLte cellInfoLte = (CellInfoLte) manager.getAllCellInfo().get(0);
                        CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
                        strength = cellSignalStrengthLte.getDbm();
                    }
                    break;
                }
            }
            return strength;
        }
        return strength;
    }

    private static long FileSize(File file) {
        long filesize = file.length();
        return (filesize / 1024) / 1024; //file size In MB
    }

    /* Checks if external storage is available for read and write */
    private static boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

}
