package com.audacious_software.passive_data_kit;

import android.content.Context;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Locale;

public class PhoneUtililties {
    @SuppressWarnings("StringConcatenationInLoop")
    public static String normalizedPhoneNumber(String phoneNumber, int digits) {
        if (phoneNumber == null) {
            return null;
        }

        phoneNumber = phoneNumber.replaceAll("[^\\d.]", "");

        while (phoneNumber.length() > digits) {
            phoneNumber = phoneNumber.substring(1);
        }

        while (phoneNumber.length() < digits) {
            phoneNumber += "0";
        }

        return phoneNumber;
    }

    public static String e164PhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        PhoneNumberUtil instance = PhoneNumberUtil.getInstance();

        try {
            Phonenumber.PhoneNumber parsedNumber = instance.parse(phoneNumber, Locale.getDefault().getCountry());

            return instance.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            e.printStackTrace();
        }

        return PhoneUtililties.normalizedPhoneNumber(phoneNumber, 10);
    }
}

