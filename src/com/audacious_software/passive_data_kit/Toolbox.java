package com.audacious_software.passive_data_kit;

import android.util.Base64;

import org.libsodium.jni.NaCl;
import org.libsodium.jni.Sodium;

import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class Toolbox {
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern EDGESDHASHES = Pattern.compile("(^-|-$)");

    public static String toSlug(String input) {

        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = EDGESDHASHES.matcher(slug).replaceAll("");

        return slug.toLowerCase(Locale.ENGLISH);
    }

    public static byte[] decodeBase64(String encoded) {
        return Base64.decode(encoded, Base64.DEFAULT);
    }

    public static String encodeBase64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.CRLF);
    }

    public static String encrypt(byte[] privateKey, byte[] publicKey, byte[] nonce, String payload) {
        NaCl.sodium();

        byte[] message = payload.getBytes(Charset.forName("UTF-8"));

        long ciphertextLength = Sodium.crypto_box_macbytes() + message.length;

        byte[] ciphertext = new byte[(int) ciphertextLength];

        if (Sodium.crypto_box_easy(ciphertext, message, message.length, nonce, publicKey, privateKey) == 0) {
            return Toolbox.encodeBase64(ciphertext);
        }

        return payload;
    }

    public static byte[] randomNonce() {
        NaCl.sodium();

        long nonceLength = Sodium.crypto_box_noncebytes();

        byte[] nonce = new byte[(int) nonceLength];

        Sodium.randombytes_buf(nonce,(int) nonceLength);

        return nonce;
    }
}