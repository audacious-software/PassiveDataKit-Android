package com.audacious_software.passive_data_kit.messaging;

import android.app.Application;
import android.util.Log;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.PassiveDataKitApplication;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;

public class FirebasePushNotificationService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        PassiveDataKit.getInstance(this).updateFirebaseDeviceToken(token);
    }

    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d("PDK", "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.e("PDK", "Message data payload: " + remoteMessage.getData());

            if (/* Check if data needs to be processed by long running job */ true) {
                // For long-running tasks (10 seconds or more) use Firebase Job Dispatcher.
                // scheduleJob();
            } else {
                // Handle message within 10 seconds
                // handleNow();
            }
        }

        HashMap<String, Object> payload = new HashMap<>();

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.e("PDK", "Message Notification Body: " + remoteMessage.getNotification().getBody());
            payload.put("body", remoteMessage.getNotification().getBody());
        }

        Application sharedApp = this.getApplication();

        if (sharedApp instanceof PassiveDataKitApplication) {
            PassiveDataKitApplication pdkApp = (PassiveDataKitApplication) sharedApp;

            pdkApp.doBackgroundWork();
        }

        Logger.getInstance(this).log("pdk-received-firebase-message", payload);
    }
}
