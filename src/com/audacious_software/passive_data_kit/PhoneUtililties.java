package com.audacious_software.passive_data_kit;

import android.content.Context;

/**
 * Created by cjkarr on 12/13/2016.
 */

public class PhoneUtililties {
    public static String normalizedPhoneNumber(String phoneNumber)
    {
        if (phoneNumber == null) {
            return null;
        }

        phoneNumber = phoneNumber.replaceAll("[^\\d.]", "");

        while (phoneNumber.length() > 10) {
            phoneNumber = phoneNumber.substring(1);
        }

        while (phoneNumber.length() < 10) {
            phoneNumber += "0";
        }


        return phoneNumber;
    }
}

