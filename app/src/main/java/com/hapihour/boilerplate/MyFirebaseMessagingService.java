package com.hapihour.boilerplate;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Created by zacck on 2016/07/28.
 */

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    public static final String TAG = "MyFMService";

    //this takes charge when we recieve a message from the cloud
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        //Handle the incoming payload
        Log.d(TAG, "FCM MESSAGE Id: " +remoteMessage.getMessageId());
        Log.d(TAG, "FCM Notification Message: "+
            remoteMessage.getNotification());
        Log.d(TAG, "FCM Data Message: " + remoteMessage.getData());
    }
}
