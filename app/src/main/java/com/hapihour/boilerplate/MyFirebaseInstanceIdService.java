package com.hapihour.boilerplate;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Created by zacck on 2016/07/28.
 */

public class MyFirebaseInstanceIdService extends FirebaseInstanceIdService {
     private static final String TAG = "MYFirebaseIIDService";
    private static final String FRIENDLY_ENGAGE_TOPIC = "friendly_engage";


    /*
    * THe application's current Istance is no longer valid
    * and thus a new one must be requested
    * */

    @Override
    public void onTokenRefresh() {
        //If you need to hadnle the generation of a token, initially or
        //after a refresh this is where we need to to that
        String token = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "FCM Token: "+token);
        //Once a token is generated , we subsribe to topic
        FirebaseMessaging.getInstance()
                .subscribeToTopic(FRIENDLY_ENGAGE_TOPIC);

    }
}
