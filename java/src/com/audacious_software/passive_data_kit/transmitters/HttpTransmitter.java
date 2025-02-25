package com.audacious_software.passive_data_kit.transmitters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.preference.PreferenceManager;
import android.util.Log;

import com.audacious_software.passive_data_kit.DeviceInformation;
import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.Toolbox;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.R;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.tls.HandshakeCertificates;

@SuppressWarnings({"PointlessBooleanExpression", "unused"})
public class HttpTransmitter extends Transmitter implements Generators.GeneratorUpdatedListener {
    public static final String UPLOAD_URI = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.UPLOAD_URI";
    public static final String USER_ID = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.USER_ID";
    private static final String HASH_ALGORITHM = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.HASH_ALGORITHM";
    private static final String HASH_PREFIX = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.HASH_PREFIX";
    public static final String STRICT_SSL_VERIFICATION = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.STRICT_SSL_VERIFICATION";
    private static final String UPLOAD_INTERVAL = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.UPLOAD_INTERVAL";
    public static final String WIFI_ONLY = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.WIFI_ONLY";
    public static final String CHARGING_ONLY = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.CHARGING_ONLY";
    public static final String USE_EXTERNAL_STORAGE = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.USE_EXTERNAL_STORAGE";
    public static final String COMPRESS_PAYLOADS = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.COMPRESS_PAYLOADS";
    public static final String MAX_BUNDLE_SIZE = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.MAX_BUNDLE_SIZE";;

    private static final String STORAGE_FOLDER_NAME = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.STORAGE_FOLDER_NAME";
    public static final String USER_AGENT_NAME = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.USER_AGENT_NAME";

    public static final String PUBLIC_KEY = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.PUBLIC_KEY";
    public static final String PRIVATE_KEY = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.PRIVATE_KEY";

    private static final String LAST_SUCCESSFUL_TRANSMISSION = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.LAST_SUCCESSFUL_TRANSMISSION";

    private static final String ERROR_FILE_EXTENSION = ".error";
    private static final String JSON_EXTENSION = ".json";
    private static final String TEMP_EXTENSION = ".in-progress";
    private static final String TOO_LARGE_FILE_EXTENSION = ".too-large";

    private static final int RESULT_SUCCESS = 0;
    private static final int RESULT_ERROR = 1;

    private static final String COMPRESSION_GZIP = "gzip";
    private static final String COMPRESSION_NONE = "none";

    protected Uri mUploadUri = null;
    protected String mUserId = null;
    private String mHashAlgorithm = null;
    private String mHashPrefix = null;
    protected boolean mStrictSsl = true;
    protected Context mContext = null;
    private long mUploadInterval = 300000;
    protected long mLastAttempt = 0;
    protected boolean mWifiOnly = false;
    protected boolean mChargingOnly = false;
    protected boolean mUseExternalStorage = false;
    private boolean mCompressPayloads = false;

    private String mFolderName = "http-transmitter";
    private String mUserAgent = "http-transmitter";

    protected JsonGenerator mJsonGenerator = null;
    protected File mCurrentFile = null;
    protected long mTransmitted = 0;
    private Thread mLooperThread = null;
    protected Handler mHandler = null;
    private byte[] mPublicKey = null;
    private byte[] mPrivateKey = null;

    private int mCurrentReadingCount = 0;
    private int mMaxReadingCount = 256;
    private int mCurrentBytesWritten = 0;

    public HttpTransmitter() {
        super();

        final HttpTransmitter me = this;

        if (this.mLooperThread == null) {
            this.mLooperThread = new Thread() {
                public void run() {
                    Looper.prepare();

                    me.mHandler = new Handler(Looper.myLooper()) {
                        public void handleMessage(Message message) {
                            Log.e("PDK", "[HTTP] HANDLE MESSAGE: " + message);
                            // process incoming messages here
                        }
                    };

                    Looper.loop();
                }
            };
        }

        this.mLooperThread.start();
    }

    @SuppressWarnings({"StringConcatenationInLoop"})
    @Override
    public void initialize(Context context, HashMap<String, String> options) {
        this.mContext = context.getApplicationContext();

        if (!options.containsKey(HttpTransmitter.UPLOAD_URI)) {
            throw new HttpTransmitter.IncompleteConfigurationException("The upload URI is not specified.");
        }
        else if (!options.containsKey(HttpTransmitter.USER_ID)) {
            throw new HttpTransmitter.IncompleteConfigurationException("The user ID is not specified.");
        }

        this.mUploadUri = Uri.parse(options.get(HttpTransmitter.UPLOAD_URI));
        this.mUserId = options.get(HttpTransmitter.USER_ID);

        if (options.containsKey(HttpTransmitter.HASH_ALGORITHM)) {
            this.mHashAlgorithm = options.get(HttpTransmitter.HASH_ALGORITHM);
        }

        if (options.containsKey(HttpTransmitter.HASH_PREFIX)) {
            this.mHashPrefix = options.get(HttpTransmitter.HASH_PREFIX);
        }

        if (options.containsKey(HttpTransmitter.PUBLIC_KEY)) {
            this.mPublicKey = Toolbox.decodeBase64(options.get(HttpTransmitter.PUBLIC_KEY));
        }

        if (options.containsKey(HttpTransmitter.PRIVATE_KEY)) {
            this.mPrivateKey = Toolbox.decodeBase64(options.get(HttpTransmitter.PRIVATE_KEY));
        }

        if (options.containsKey(HttpTransmitter.MAX_BUNDLE_SIZE)) {
            this.mMaxReadingCount = Integer.parseInt(options.get(HttpTransmitter.MAX_BUNDLE_SIZE));
        }

        if (this.mHashAlgorithm != null) {
            try {
                MessageDigest md = MessageDigest.getInstance(this.mHashAlgorithm);

                if (this.mHashPrefix != null) {
                    this.mUserId = this.mHashPrefix + this.mUserId;
                }

                byte[] digest = md.digest(this.mUserId.getBytes(Charset.forName("UTF-8")));

                this.mUserId = (new BigInteger(1, digest)).toString(16);

                while (this.mUserId.length() < 64) {
                    this.mUserId = "0" + this.mUserId;
                }
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        if (options.containsKey(HttpTransmitter.STRICT_SSL_VERIFICATION)) {
            this.mStrictSsl = "true".equals(options.get(HttpTransmitter.STRICT_SSL_VERIFICATION).toLowerCase(Locale.ENGLISH));
        }

        if (options.containsKey(HttpTransmitter.UPLOAD_INTERVAL)) {
            this.mUploadInterval = Long.parseLong(options.get(HttpTransmitter.UPLOAD_INTERVAL));
        }

        if (options.containsKey(HttpTransmitter.WIFI_ONLY)) {
            this.mWifiOnly = "true".equals(options.get(HttpTransmitter.WIFI_ONLY).toLowerCase(Locale.ENGLISH));
        }

        if (options.containsKey(HttpTransmitter.CHARGING_ONLY)) {
            this.mChargingOnly = "true".equals(options.get(HttpTransmitter.CHARGING_ONLY).toLowerCase(Locale.ENGLISH));
        }

        if (options.containsKey(HttpTransmitter.COMPRESS_PAYLOADS)) {
            this.mCompressPayloads = "true".equals(options.get(HttpTransmitter.COMPRESS_PAYLOADS).toLowerCase(Locale.ENGLISH));
        }

        if (options.containsKey(HttpTransmitter.USE_EXTERNAL_STORAGE)) {
            this.mUseExternalStorage = "true".equals(options.get(HttpTransmitter.USE_EXTERNAL_STORAGE).toLowerCase(Locale.ENGLISH));
        }

        if (options.containsKey(HttpTransmitter.STORAGE_FOLDER_NAME)) {
            this.mFolderName = options.get(HttpTransmitter.STORAGE_FOLDER_NAME);
        }

        if (options.containsKey(HttpTransmitter.USER_AGENT_NAME)) {
            this.mUserAgent = options.get(HttpTransmitter.USER_AGENT_NAME);
        }

        Generators.getInstance(this.mContext).addNewGeneratorUpdatedListener(this);
    }

    public void deinitialize(Context context) {
        Generators.getInstance(this.mContext).removeGeneratorUpdatedListener(this);
    }

    private boolean shouldAttemptUpload(boolean force) {
        if (force) {
            return true;
        }

        if (this.mWifiOnly) {
            if (DeviceInformation.wifiAvailable(this.mContext) == false) {
                return false;
            }
        }

        if (this.mChargingOnly) {
            if (DeviceInformation.isPluggedIn(this.mContext) == false) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean transmit(boolean force) {
        if (this.mContext == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (force) {
            this.mLastAttempt = 0;
        }

        if (this.mHandler == null || now - this.mLastAttempt < this.mUploadInterval || !this.shouldAttemptUpload(force)) {
            return false;
        }

        this.mLastAttempt = now;

        final HttpTransmitter me = this;

        Runnable r = new Runnable() {
            @SuppressWarnings("ResultOfMethodCallIgnored")
            @SuppressLint("TrulyRandom")
            public void run() {
                synchronized (me) {
                    try {
                        File pendingFolder = me.getPendingFolder();

                        me.closeOpenSession();

                        String[] largeFiles = pendingFolder.list(new FilenameFilter() {
                            public boolean accept(File dir, String filename) {
                                if (filename.endsWith(HttpTransmitter.TOO_LARGE_FILE_EXTENSION)) {
                                    return true;
                                }

                                return false;
                            }
                        });

                        if (largeFiles == null) {
                            largeFiles = new String[0];
                        }

                        if (largeFiles.length == 0) {
                            largeFiles = pendingFolder.list(new FilenameFilter() {
                                public boolean accept(File dir, String filename) {
                                    if (filename.endsWith(HttpTransmitter.ERROR_FILE_EXTENSION)) {
                                        return true;
                                    }

                                    return false;
                                }
                            });
                        }

                        if (largeFiles == null) {
                            largeFiles = new String[0];
                        }

                        for (String filename : largeFiles) {
                            File payloadFile = new File(pendingFolder, filename);

                            ObjectMapper mapper = new ObjectMapper();
                            mapper.registerModule(new JsonOrgModule());

                            Iterator<JSONObject> iterator = mapper.readValues(new JsonFactory().createJsonParser(payloadFile), JSONObject.class);

                            JSONArray newReadings = new JSONArray();

                            int length = 0;

                            try {
                                while (iterator.hasNext()) {
                                    JSONObject reading = iterator.next();

                                    newReadings.put(reading);

                                    length += reading.length();

                                    if (newReadings.length() >= 200 || length > (512 * 1024)) {
                                        File newFile = new File(pendingFolder, "" + System.currentTimeMillis() + HttpTransmitter.JSON_EXTENSION);

                                        FileUtils.writeStringToFile(newFile, newReadings.toString(), "UTF-8");

                                        newReadings = new JSONArray();
                                        length = 0;
                                    }
                                }
                            } catch (RuntimeException ex) {
                                if (payloadFile.getAbsolutePath().endsWith(HttpTransmitter.ERROR_FILE_EXTENSION)) {

                                } else {
                                    Logger.getInstance(me.mContext).logThrowable(ex);

                                    payloadFile.renameTo(new File(payloadFile.getAbsolutePath() + HttpTransmitter.ERROR_FILE_EXTENSION));
                                }
                            } catch (JsonParseException ex) {
                                // Incomplete JSON in file...

                                if (payloadFile.getAbsolutePath().endsWith(HttpTransmitter.ERROR_FILE_EXTENSION)) {

                                } else {
                                    Logger.getInstance(me.mContext).logThrowable(ex);

                                    payloadFile.renameTo(new File(payloadFile.getAbsolutePath() + HttpTransmitter.ERROR_FILE_EXTENSION));
                                }
                            }

                            if (newReadings.length() > 1) {
                                File newFile = new File(pendingFolder, "" + System.currentTimeMillis() + HttpTransmitter.JSON_EXTENSION);

                                FileUtils.writeStringToFile(newFile, newReadings.toString(), "UTF-8");
                            }

                            payloadFile.delete();
                        }

                        final MutableInt found = new MutableInt(0);

                        String[] filenames = pendingFolder.list(new FilenameFilter() {
                            public boolean accept(File dir, String filename) {
                                // Only return first 256 for performance reasons...
                                if (found.intValue() >= 256)
                                    return false;

                                if (filename.endsWith(HttpTransmitter.JSON_EXTENSION)) {
                                    found.add(1);

                                    return true;
                                }

                                return false;
                            }
                        });

                        if (filenames == null) {
                            filenames = new String[0];
                        }

                        if (filenames.length < 1) {
                            return;
                        }

                        List<String> fileList = new ArrayList<>(Arrays.asList(filenames));

                        Collections.shuffle(fileList);

                        int pendingCount = fileList.size();

                        if (pendingCount > 0) {
                            String filename = fileList.remove(0);

                            File payloadFile = new File(pendingFolder, filename);

                            if (payloadFile.exists()) {
                                try {
                                    BufferedReader reader = new BufferedReader(new FileReader(payloadFile));

                                    StringBuilder builder = new StringBuilder();
                                    String line = null;

                                    char[] buffer = new char[1024];

                                    int read = 0;

                                    while ((read = reader.read(buffer, 0, buffer.length)) != -1) {
                                        for (int i = 0; i < read; i++) {
                                            builder.append(buffer[i]);
                                        }
                                    }

                                    reader.close();

                                    String payload = builder.toString();

                                    String payloadString = (new JSONArray(payload)).toString();

                                    int result = me.transmitHttpPayload(payload);

                                    if (result == HttpTransmitter.RESULT_SUCCESS) {
                                        me.mTransmitted += payloadFile.length();

                                        payloadFile.delete();

                                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);
                                        SharedPreferences.Editor e = prefs.edit();
                                        e.putLong(HttpTransmitter.LAST_SUCCESSFUL_TRANSMISSION, System.currentTimeMillis());
                                        e.apply();
                                    }
                                } catch (JSONException e) {
                                    // Invalid JSON, log results.

                                    // Logger.getInstance(me.mContext).logThrowable(e);

                                    HashMap<String, Object> details = new HashMap<>();
                                    payloadFile.renameTo(new File(payloadFile.getAbsolutePath() + HttpTransmitter.ERROR_FILE_EXTENSION));

                                    details.put("name", payloadFile.getAbsolutePath());
                                    details.put("size", payloadFile.length());

                                    Logger.getInstance(me.mContext).log("corrupted_file", details);
                                } catch (OutOfMemoryError e) {
                                    // File too large, rename for break-up and transmission later.

                                    Logger.getInstance(me.mContext).logThrowable(e);

                                    HashMap<String, Object> details = new HashMap<>();
                                    payloadFile.renameTo(new File(payloadFile.getAbsolutePath() + HttpTransmitter.TOO_LARGE_FILE_EXTENSION));

                                    details.put("name", payloadFile.getAbsolutePath());
                                    details.put("size", payloadFile.length());

                                    Logger.getInstance(me.mContext).log("too_large_file", details);
                                }
                            }

                            if (fileList.size() > 0) {
                                me.mHandler.post(this);
                            }
                        }
                    } catch (OutOfMemoryError e) {
                        e.printStackTrace();

                        // Clean up memory and try again later...

                        System.gc();
                    } catch (IOException e) {
                        Logger.getInstance(me.mContext).logThrowable(e);
                    }
                }
            }
        };

        this.mHandler.post(r);

        return true;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private int transmitHttpPayload(String payload) {
        if (payload == null || payload.trim().length() == 0) {
            return HttpTransmitter.RESULT_SUCCESS;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        try {
            // Liberal HTTPS setup:
            // http://stackoverflow.com/questions/2012497/accepting-a-certificate-for-https-on-android

            HandshakeCertificates certificates = PassiveDataKit.getInstance(this.mContext).fetchTrustedCertificates();

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.MINUTES)
                    .readTimeout(3, TimeUnit.MINUTES)
                    .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager())
                    .build();

            if (!this.mStrictSsl) {
                // Create a trust manager that does not validate certificate chains
                final TrustManager[] trustAllCerts = new TrustManager[] {
                        new X509TrustManager() {
                            @SuppressLint("TrustAllX509TrustManager")
                            @Override
                            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                            }

                            @SuppressLint("TrustAllX509TrustManager")
                            @Override
                            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                            }

                            @Override
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return new java.security.cert.X509Certificate[]{};
                            }
                        }
                };

                // Install the all-trusting trust manager
                final SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                // Create an ssl socket factory with our all-trusting manager
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                client = new OkHttpClient.Builder()
                        .connectTimeout(3, TimeUnit.MINUTES)
                        .readTimeout(3, TimeUnit.MINUTES)
                        .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                        .build();
            }

            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

            ArrayList<File> toDelete = new ArrayList<>();

            if (payload.contains("\"" + Generator.MEDIA_ATTACHMENT_KEY + "\":")) {
                JSONArray payloadJson = new JSONArray(payload);

                for (int i = 0; i < payloadJson.length(); i++) {
                    JSONObject reading = payloadJson.getJSONObject(i);

                    if (reading.has(Generator.MEDIA_ATTACHMENT_KEY)) {
                        Uri u = Uri.parse(reading.getString(Generator.MEDIA_ATTACHMENT_KEY));

                        String mimeType = "application/octet-stream";

                        if (reading.has(Generator.MEDIA_CONTENT_TYPE_KEY)) {
                            mimeType = reading.getString(Generator.MEDIA_CONTENT_TYPE_KEY);
                        }

                        String guid = reading.getString(Generator.MEDIA_ATTACHMENT_GUID_KEY);
                        File file = new File(u.getPath());

                        if (file.exists()) {
                            builder = builder.addFormDataPart(guid, file.getName(), RequestBody.create(MediaType.parse(mimeType), file));
                            toDelete.add(file);
                        }
                    }
                }
            }

            boolean compressed = false;

            String payloadString = (new JSONArray(payload)).toString();

            if (this.mCompressPayloads) {
                // Compress payload string

                try {
                    byte[] input = payloadString.getBytes("UTF-8");

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    GZIPOutputStream gzip = new GZIPOutputStream(baos);

                    gzip.write(input, 0, input.length);
                    gzip.flush();

                    gzip.close();
                    baos.close();

                    byte[] compressedBytes = baos.toByteArray();

                    payloadString = Toolbox.encodeBase64(compressedBytes);

                    compressed = true;
                } catch (java.io.UnsupportedEncodingException ex) {
                    // handle
                }
            }

            if (this.mPublicKey != null) {
                byte[] nonce = Toolbox.randomNonce();

                String encryptedString = Toolbox.encrypt(this.mPrivateKey, this.mPublicKey, nonce, payloadString);

                builder = builder.addPart(Headers.of("Content-Disposition", "form-data; name=\"payload\""), RequestBody.create(null, encryptedString));
                builder = builder.addPart(Headers.of("Content-Disposition", "form-data; name=\"encrypted\""), RequestBody.create(null, "true"));
                builder = builder.addPart(Headers.of("Content-Disposition", "form-data; name=\"nonce\""), RequestBody.create(null, Toolbox.encodeBase64(nonce)));
            } else {
                builder = builder.addPart(Headers.of("Content-Disposition", "form-data; name=\"payload\""), RequestBody.create(null, payloadString));
            }

            if (compressed) {
                builder = builder.addPart(Headers.of("Content-Disposition", "form-data; name=\"compression\""), RequestBody.create(null, HttpTransmitter.COMPRESSION_GZIP));
            } else {
                builder = builder.addPart(Headers.of("Content-Disposition", "form-data; name=\"compression\""), RequestBody.create(null, HttpTransmitter.COMPRESSION_NONE));
            }

            RequestBody requestBody = builder.build();

            if (this.mUserAgent == null) {
                String version = this.mContext.getPackageManager().getPackageInfo(this.mContext.getPackageName(), 0).versionName;
                String appName = this.mContext.getString(this.mContext.getApplicationInfo().labelRes);

                this.mUserAgent = appName + " " + version;
            }

            Request request = new Request.Builder()
                    .removeHeader("User-Agent")
                    .addHeader("User-Agent", this.mUserAgent)
                    .url(this.mUploadUri.toString())
                    .post(requestBody)
                    .build();

            Response response = client.newCall(request).execute();

            int code = response.code();

            ResponseBody body = response.body();

            String bodyString = body.string();

            response.body().close();

            if (code >= 200 && code < 300) {
                JSONObject responseObject = new JSONObject(bodyString);

                if (responseObject.has("added") && responseObject.getBoolean("added")) {
                    for (File f : toDelete) {
                        f.delete();
                    }

                    SharedPreferences.Editor e = prefs.edit();
                    e.remove(Transmitter.FAILURE_REASON);
                    e.remove(Transmitter.FAILURE_TIMESTAMP);
                    e.apply();

                    return HttpTransmitter.RESULT_SUCCESS;
                }
            } else {
                Log.e("PassiveDataKit", this.mContext.getString(R.string.transmission_failed_server_code, code));
                Log.e("PassiveDataKit", bodyString);

                SharedPreferences.Editor e = prefs.edit();
                e.putString(Transmitter.FAILURE_REASON, this.mContext.getString(R.string.transmission_failed_server_code, code));
                e.putLong(Transmitter.FAILURE_TIMESTAMP, System.currentTimeMillis());
                e.apply();
            }
        } catch (UnknownHostException ex) {
            SharedPreferences.Editor e = prefs.edit();
            e.putString(Transmitter.FAILURE_REASON, this.mContext.getString(R.string.transmission_failed_unknown_host, this.mUploadUri.getHost()));
            e.putLong(Transmitter.FAILURE_TIMESTAMP, System.currentTimeMillis());
            e.apply();
        } catch (Exception ex) {
            ex.printStackTrace();

            SharedPreferences.Editor e = prefs.edit();
            e.putString(Transmitter.FAILURE_REASON, this.mContext.getString(R.string.transmission_failed_exception, ex.getClass().toString(), ex.getLocalizedMessage()));
            e.putLong(Transmitter.FAILURE_TIMESTAMP, System.currentTimeMillis());
            e.apply();
        }

        return HttpTransmitter.RESULT_ERROR;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void closeOpenSession() throws IOException {
        synchronized(this) {
            if (this.mCurrentFile == null) {
                return;
            }

            File tempFile = this.mCurrentFile;

            if (this.mJsonGenerator == null || tempFile == null) {
                return;
            }

            final File pendingFolder = this.getPendingFolder();

            this.mJsonGenerator.writeEndArray();
            this.mJsonGenerator.flush();
            this.mJsonGenerator.close();

            String finalFile = tempFile.getAbsolutePath().replace(HttpTransmitter.TEMP_EXTENSION, HttpTransmitter.JSON_EXTENSION);

            this.mCurrentFile = null;
            this.mJsonGenerator = null;

            FileUtils.moveFile(tempFile, new File(finalFile));

            String[] filenames = pendingFolder.list(new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(HttpTransmitter.TEMP_EXTENSION);
                }
            });

            if (filenames == null) {
                filenames = new String[0];
            }

            for (String filename : filenames) {
                File toDelete = new File(pendingFolder, filename);
                toDelete.delete();
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected File getPendingFolder() {
        File internalStorage = this.mContext.getFilesDir();

        if (internalStorage == null || this.mUseExternalStorage) {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                internalStorage = this.mContext.getExternalFilesDir(null);
            }
        }

        if (internalStorage != null && !internalStorage.exists()) {
            internalStorage.mkdirs();
        }

        File pendingFolder = new File(internalStorage, this.mFolderName);

        if (!pendingFolder.exists()) {
            pendingFolder.mkdirs();
        }

        return pendingFolder;
    }

    protected static long getFileSize(final File file) {
        if (file == null || !file.exists()) {
            return 0;
        }

        if (!file.isDirectory()) {
            return file.length();
        }

        final List<File> dirs = new LinkedList<>();

        dirs.add(file);

        long result = 0;

        while (!dirs.isEmpty()) {
            final File dir = dirs.remove(0);

            if (!dir.exists()) {
                continue;
            }

            final File[] listFiles = dir.listFiles();

            if (listFiles == null || listFiles.length == 0) {
                continue;
            }

            for (final File child : listFiles) {
                result += child.length();

                if (child.isDirectory()) {
                    dirs.add(child);
                }
            }
        }

        return result;
    }

    @Override
    public long pendingSize() {
        return HttpTransmitter.getFileSize(this.getPendingFolder());
    }

    @Override
    public long pendingTransmissionSize() {
        return this.pendingSize();
    }

    @Override
    public long pendingTransmissions() {
        File pendingFolder = this.getPendingFolder();

        if (pendingFolder == null || !pendingFolder.exists()) {
            return 0;
        }

        final List<File> dirs = new LinkedList<>();

        dirs.add(pendingFolder);

        long result = 0;

        while (!dirs.isEmpty()) {
            final File dir = dirs.remove(0);

            if (!dir.exists()) {
                continue;
            }

            final File[] listFiles = dir.listFiles();

            if (listFiles == null || listFiles.length == 0) {
                continue;
            }

            for (final File child : listFiles) {
                if (child.isDirectory()) {
                    dirs.add(child);
                } else {
                    if (child.getAbsolutePath().endsWith(HttpTransmitter.JSON_EXTENSION)) {
                        result += 1;
                    } if (child.getAbsolutePath().endsWith(HttpTransmitter.TOO_LARGE_FILE_EXTENSION)) {
                        result += 1;
                    }
                }
            }
        }

        return result;
    }

    @Override
    public long transmittedSize() {
        return this.mTransmitted;
    }

    @Override
    public void onGeneratorUpdated(final String identifier, final long timestamp, Bundle data) {
        if (this.mHandler == null) {
            return;
        }

        final HttpTransmitter me = this;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                final Parcel parcel = Parcel.obtain();
                parcel.writeBundle(data);
                parcel.setDataPosition(0);

                final boolean largeBundle = (me.mCurrentBytesWritten + parcel.dataSize()) > (1024 * 256);

                if (largeBundle) {
                    Log.e("PDK", "Large data point encountered: " + parcel.dataSize() + " bytes. Saving to dedicated bundle.");
                }

                if (largeBundle || me.mCurrentReadingCount >= me.mMaxReadingCount) {
                    try {
                        me.closeOpenSession();

                        me.mCurrentReadingCount = 0;
                        me.mCurrentBytesWritten = 0;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                Bundle clonedData = parcel.readBundle(getClass().getClassLoader());

                if (clonedData.keySet().size() > 0) {  // Only transmit non-empty bundles...
                    long generatorTimestamp = timestamp / 1000; // Convert to seconds...

                    Generators generators = Generators.getInstance(me.mContext);

                    Bundle metadata = new Bundle();

                    if (clonedData.containsKey(Generator.PDK_METADATA)) {
                        metadata = clonedData.getBundle(Generator.PDK_METADATA);
                    }

                    metadata.putString(Generator.IDENTIFIER, identifier);
                    metadata.putDouble(Generator.TIMESTAMP, generatorTimestamp);
                    metadata.putString(Generator.GENERATOR, generators.getGeneratorFullName(identifier));
                    metadata.putString(Generator.SOURCE, me.mUserId);

                    TimeZone timeZone = TimeZone.getDefault();

                    metadata.putString(Generator.TIMEZONE, timeZone.getID());
                    metadata.putInt(Generator.TIMEZONE_OFFSET, timeZone.getOffset(timestamp) / 1000);

                    clonedData.putBundle(Generator.PDK_METADATA, metadata);

                    synchronized(me) {
                        if (me.mJsonGenerator == null || me.mCurrentFile == null) {
                            me.mCurrentFile = new File(me.getPendingFolder(), System.currentTimeMillis() + HttpTransmitter.TEMP_EXTENSION);

                            try {
                                JsonFactory factory = new JsonFactory();
                                me.mJsonGenerator = factory.createGenerator(me.mCurrentFile, JsonEncoding.UTF8);
                                me.mJsonGenerator.writeStartArray();
                            } catch (IOException e) {
                                Logger.getInstance(me.mContext).logThrowable(e);

                                me.mCurrentFile = null;
                            }
                        }

                        if (me.mJsonGenerator != null) {
                            HttpTransmitter.writeBundle(me.mContext, me.mJsonGenerator, clonedData);

                            me.mCurrentReadingCount += 1;

                            me.mCurrentBytesWritten += parcel.dataSize();

                            if (largeBundle) {
                                me.mCurrentReadingCount = me.mMaxReadingCount;
                            }
                        }
                    }
                }

                parcel.recycle();

                System.gc();
            }
        };

        this.mHandler.post(r);
    }

    private static Map<String, Object> getValues(Context context, final Bundle bundle) {
        HashMap<String, Object> values = new HashMap<>();

        if (bundle == null) {
            return values;
        }

        try {
            for (String key : bundle.keySet()) {
                values.put(key, bundle.get(key));
            }
        } catch (BadParcelableException e) {
            Logger.getInstance(context).logThrowable(e);
        }

        return values;
    }

    @SuppressWarnings("unchecked")
    protected static void writeBundle(Context context, JsonGenerator generator, Bundle bundle) {
        try {
            generator.writeStartObject();

            Map<String, Object> values = HttpTransmitter.getValues(context, bundle);

            for (String key : values.keySet()) {
                Object value = values.get(key);

                if (value != null && key != null) {
                    if (value instanceof String) {
                        generator.writeStringField(key, (String) value);
                    }
                    else if (value instanceof float[]) {
                        float[] floats = (float[]) value;

                        generator.writeArrayFieldStart(key);

                        for (float f : floats) {
                            generator.writeNumber(f);
                        }

                        generator.writeEndArray();
                    }
                    else if (value instanceof int[]) {
                        int[] ints = (int[]) value;

                        generator.writeArrayFieldStart(key);

                        for (int i : ints) {
                            generator.writeNumber(i);
                        }

                        generator.writeEndArray();
                    }
                    else if (value instanceof long[]) {
                        long[] longs = (long[]) value;

                        generator.writeArrayFieldStart(key);

                        for (long l : longs) {
                            generator.writeNumber(l);
                        }

                        generator.writeEndArray();
                    }
                    else if (value instanceof double[]) {
                        double[] doubles = (double[]) value;

                        generator.writeArrayFieldStart(key);

                        for (double d : doubles) {
                            generator.writeNumber(d);
                        }

                        generator.writeEndArray();
                    }
                    else if (value instanceof Float) {
                        Float f = (Float) value;

                        generator.writeNumberField(key, f);
                    }
                    else if (value instanceof Integer) {
                        Integer i = (Integer) value;

                        generator.writeNumberField(key, i);
                    }
                    else if (value instanceof Long) {
                        Long l = (Long) value;

                        generator.writeNumberField(key, l);
                    }
                    else if (value instanceof Boolean) {
                        Boolean b = (Boolean) value;

                        generator.writeBooleanField(key, b);
                    }
                    else if (value instanceof Short) {
                        Short s = (Short) value;

                        generator.writeNumberField(key, s);
                    }
                    else if (value instanceof Double) {
                        Double d = (Double) value;

                        if (d.isInfinite()) {
                            generator.writeNumberField(key, Double.MAX_VALUE);
                        }
                        else {
                            generator.writeNumberField(key, d);
                        }
                    }
                    else if (value instanceof List) {
                        List<Object> list = (List<Object>) value;

                        generator.writeArrayFieldStart(key);

                        for (Object o : list) {
                            if (o instanceof String) {
                                generator.writeString(o.toString());
                            }
                            else if (o instanceof Bundle) {
                                HttpTransmitter.writeBundle(context, generator, (Bundle) o);
                            }
                            else {
                                // Log.e("PDK", "LIST OBJ: " + o.getClass().getCanonicalName() + " IN " + key);
                            }
                        }

                        generator.writeEndArray();
                    }
                    else if (value instanceof Bundle) {
                        generator.writeFieldName(key);
                        HttpTransmitter.writeBundle(context, generator, (Bundle) value);
                    }
                    else {
                        // Log.e("PDK", "GOT TYPE " + value.getClass().getCanonicalName() + " FOR " + key);
                    }
                }
            }

            generator.writeEndObject();
        } catch (Exception e) {
            e.printStackTrace();

            Logger.getInstance(context).logThrowable(e);

            HashMap<String, Object> payload = new HashMap<>();
            payload.put("bundle_string", bundle.toString());
            payload.put("exception_type", e.getClass().getName());

            Logger.getInstance(context).log("pdk_http_transmitter_write_bundle_error", payload);
        }
    }

    public void setUserId(String userId) {
        this.mUserId = userId;
    }

    public void setChargingOnly(boolean onlyCharging) {
        this.mChargingOnly = onlyCharging;
    }

    public void setWiFiOnly(boolean onlyWiFi) {
        this.mWifiOnly = onlyWiFi;
    }

    public void setPublicKey(byte[] publicKey) {
        this.mPublicKey = publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.mPublicKey = Toolbox.decodeBase64(publicKey);
    }

    public void setPrivateKey(String privateKey) {
        this.mPrivateKey = Toolbox.decodeBase64(privateKey);
    }

    public void setMaxBundleSize(int size) {
        this.mMaxReadingCount = size;

        this.transmit(true);
    }

    @SuppressWarnings("unused")
    public static class IncompleteConfigurationException extends RuntimeException {
        public IncompleteConfigurationException(String message) {
            super(message);
        }
    }

    public long lastSuccessfulTransmission() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        return prefs.getLong(HttpTransmitter.LAST_SUCCESSFUL_TRANSMISSION, 0);
    }

    @Override
    public void testTransmission(Handler handler, boolean includeLocation, Runnable success, Runnable failure) {
        final HttpTransmitter me = this;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    String testPayload = me.generateTestPayload(includeLocation);

                    int result = me.transmitHttpPayload(testPayload);

                    if (result == HttpTransmitter.RESULT_SUCCESS) {
                        handler.post(success);
                    } else {
                        handler.post(failure);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();

                    handler.post(failure);
                }
            }
        };

        this.mHandler.post(r);
    }

    private String generateTestPayload(boolean includeLocation) throws JSONException {
        JSONObject testReading = new JSONObject();

        JSONObject metadata = new JSONObject();

        long timestamp = System.currentTimeMillis();

        metadata.put(Generator.IDENTIFIER, "pdk-connection-test");
        metadata.put(Generator.TIMESTAMP, timestamp / 1000);
        metadata.put(Generator.GENERATOR, Generators.getInstance(this.mContext).getGeneratorFullName("pdk-connection-test"));
        metadata.put(Generator.SOURCE, this.mUserId);

        TimeZone timeZone = TimeZone.getDefault();

        metadata.put(Generator.TIMEZONE, timeZone.getID());
        metadata.put(Generator.TIMEZONE_OFFSET, timeZone.getOffset(timestamp) / 1000);

        /*
        if (includeLocation) {
            android.location.Location location = Location.getInstance(this.mContext).getLastKnownLocation();

            if (location != null) {
                testReading.put(Location.LATITUDE, location.getLatitude());
                testReading.put(Location.LONGITUDE, location.getLongitude());
                testReading.put(Location.HISTORY_MOCK_LOCATION_PROVIDER, location.isFromMockProvider());
            }

            JSONArray mockLocationApps = new JSONArray();
            HashSet<String> seenApps = new HashSet<>();

            PackageManager pm = this.mContext.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo applicationInfo : packages) {
                try {
                    PackageInfo packageInfo = pm.getPackageInfo(applicationInfo.packageName, PackageManager.GET_PERMISSIONS);

                    String[] requestedPermissions = packageInfo.requestedPermissions;

                    if (requestedPermissions != null) {
                        for (int i = 0; i < requestedPermissions.length; i++) {
                            if (requestedPermissions[i].equals("android.permission.ACCESS_MOCK_LOCATION")) {
                                if (seenApps.contains(applicationInfo.packageName) == false) {
                                    mockLocationApps.put(applicationInfo.packageName);

                                    seenApps.add(applicationInfo.packageName);
                                }
                            }
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e("PDK" , "MOCK LOCATION STACK TRACE", e);
                }
            }

            testReading.put(Location.HISTORY_MOCK_LOCATION_APPS_COUNT, mockLocationApps.length());
            testReading.put(Location.HISTORY_MOCK_LOCATION_APPS, mockLocationApps);
        }

         */

        testReading.put("passive-data-metadata", metadata);

        JSONArray payload = new JSONArray();
        payload.put(testReading);

        return payload.toString(2);
    }
}
