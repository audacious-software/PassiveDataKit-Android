package com.audacious_software.passive_data_kit.generators.environment.services;

import android.app.IntentService;
import android.content.Intent;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.generators.environment.Geofences;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;

import java.util.HashMap;
import java.util.List;

import androidx.annotation.Nullable;

public class GeofencesService extends IntentService {
    public GeofencesService() {
        super("Passive Data Kit Geofence Service");
    }

    public GeofencesService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        long now = System.currentTimeMillis();

        if (geofencingEvent.hasError()) {
            HashMap<String, Object> payload = new HashMap<>();

            payload.put("error_code", geofencingEvent.getErrorCode());

            switch (geofencingEvent.getErrorCode()) {
                case GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE:
                    payload.put("error", "GEOFENCE_NOT_AVAILABLE: Geofence service is not available now.");
                    break;
                case GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES:
                    payload.put("error", "GEOFENCE_TOO_MANY_GEOFENCES: App has registered more than 100 geofences.");
                    break;
                case GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS:
                    payload.put("error", "GEOFENCE_TOO_MANY_PENDING_INTENTS: App has provided more than 5 different PendingIntents.");
                    break;
                default:
                    payload.put("error", "Non-geofence error code encountered.");
            }

            Logger.getInstance(this).log("pdk_geofence_error", payload);
        } else {
            String transition = Geofences.GEOFENCE_TRANSITION_UNKNOWN;

            switch (geofencingEvent.getGeofenceTransition()) {
                case Geofence.GEOFENCE_TRANSITION_DWELL:
                    transition = Geofences.GEOFENCE_TRANSITION_DWELL;
                    break;
                case Geofence.GEOFENCE_TRANSITION_ENTER:
                    transition = Geofences.GEOFENCE_TRANSITION_ENTER;
                    break;
                case Geofence.GEOFENCE_TRANSITION_EXIT:
                    transition = Geofences.GEOFENCE_TRANSITION_EXIT;
                    break;
            }

            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            for (Geofence fence : triggeringGeofences) {
                Geofences.getInstance(this).recordTransition(fence.getRequestId(), transition, now);
            }
        }
    }
}
