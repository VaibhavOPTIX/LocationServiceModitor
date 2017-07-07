package com.vaibhav.test;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

/**
 * Created by Vaibhav Barad on 5/7/17.
 */
//Reference https://guides.codepath.com/android/Starting-Background-Services

/**
 * The ResultReceiver manages the communication via method callbacks between the Service and the activity that started it.:
 */
public class ServiceResultReceiver extends ResultReceiver {
    private Receiver receiver;

    // Constructor takes a handler
    public ServiceResultReceiver(Handler handler) {
        super(handler);
    }

    // Setter for assigning the receiver
    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    // Delegate method which passes the result to the receiver if the receiver has been assigned
    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (receiver != null) {
            receiver.onReceiveResult(resultCode, resultData);
        }
    }

    // Defines our event interface for communication
    public interface Receiver {
        void onReceiveResult(int resultCode, Bundle resultData);
    }
}
