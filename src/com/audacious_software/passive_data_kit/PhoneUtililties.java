package com.audacious_software.passive_data_kit;

public class PhoneUtililties {
    @SuppressWarnings("StringConcatenationInLoop")
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

