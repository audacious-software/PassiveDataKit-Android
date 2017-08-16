package com.audacious_software.passive_data_kit.transmitters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;

import com.audacious_software.passive_data_kit.DeviceInformation;
import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.transmitters.util.LiberalSSLSocketFactory;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings({"PointlessBooleanExpression", "unused"})
public class HttpTransmitter extends Transmitter implements Generators.GeneratorUpdatedListener {
    public static final String UPLOAD_URI = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.UPLOAD_URI";
    public static final String USER_ID = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.USER_ID";
    private static final String HASH_ALGORITHM = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.HASH_ALGORITHM";
    private static final String HASH_PREFIX = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.HASH_PREFIX";
    private static final String STRICT_SSL_VERIFICATION = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.STRICT_SSL_VERIFICATION";
    private static final String UPLOAD_INTERVAL = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.UPLOAD_INTERVAL";
    public static final String WIFI_ONLY = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.WIFI_ONLY";
    public static final String CHARGING_ONLY = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.CHARGING_ONLY";
    public static final String USE_EXTERNAL_STORAGE = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.CHARGING_ONLY";
    private static final String STORAGE_FOLDER_NAME = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.STORAGE_FOLDER_NAME";
    public static final String USER_AGENT_NAME = "com.audacious_software.passive_data_kit.transmitters.HttpTransmitter.USER_AGENT_NAME";

    private static final String ERROR_FILE_EXTENSION = ".error";
    private static final String JSON_EXTENSION = ".json";
    private static final String TEMP_EXTENSION = ".in-progress";

    private static final int RESULT_SUCCESS = 0;
    private static final int RESULT_ERROR = 1;

    private Uri mUploadUri = null;
    private String mUserId = null;
    private String mHashAlgorithm = null;
    private String mHashPrefix = null;
    private boolean mStrictSsl = true;
    private Context mContext = null;
    private long mUploadInterval = 300000;
    private long mLastAttempt = 0;
    private boolean mWifiOnly = false;
    private boolean mChargingOnly = false;
    private boolean mUseExternalStorage = false;
    private String mFolderName = "http-transmitter";
    private String mUserAgent = "http-transmitter";

    private JsonGenerator mJsonGenerator = null;
    private File mCurrentFile = null;
    private long mTransmitted = 0;

    @SuppressWarnings("TryWithIdenticalCatches")
    @Override
    public void initialize(Context context, HashMap<String, String> options) {
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

        if (this.mHashAlgorithm != null) {
            try {
                MessageDigest md = MessageDigest.getInstance(this.mHashAlgorithm);

                if (this.mHashPrefix != null) {
                    this.mUserId = this.mHashPrefix + this.mUserId;
                }

                byte[] digest = md.digest(this.mUserId.getBytes("UTF-8"));

                this.mUserId = (new BigInteger(1, digest)).toString(16);

                while (this.mUserId.length() < 64) {
                    this.mUserId = "0" + this.mUserId;
                }
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (UnsupportedEncodingException e) {
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

        if (options.containsKey(HttpTransmitter.USE_EXTERNAL_STORAGE)) {
            this.mUseExternalStorage = "true".equals(options.get(HttpTransmitter.USE_EXTERNAL_STORAGE).toLowerCase(Locale.ENGLISH));
        }

        if (options.containsKey(HttpTransmitter.STORAGE_FOLDER_NAME)) {
            this.mFolderName = options.get(HttpTransmitter.STORAGE_FOLDER_NAME);
        }

        if (options.containsKey(HttpTransmitter.USER_AGENT_NAME)) {
            this.mUserAgent = options.get(HttpTransmitter.USER_AGENT_NAME);
        }

        this.mContext = context.getApplicationContext();

        Generators.getInstance(this.mContext).addNewGeneratorUpdatedListener(this);
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
    public void transmit(boolean force) {
        long now = System.currentTimeMillis();

        if (force) {
            this.mLastAttempt = 0;
        }

        if (now - this.mLastAttempt < this.mUploadInterval || !this.shouldAttemptUpload(force)) {
            return;
        }

        this.mLastAttempt = now;

        final HttpTransmitter me = this;

        Runnable r = new Runnable()
        {
            @SuppressWarnings("ResultOfMethodCallIgnored")
            @SuppressLint("TrulyRandom")
            public void run()
            {
                synchronized (me) {
                    try
                    {
                        File pendingFolder = me.getPendingFolder();

                        me.closeOpenSession();

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

                        SecureRandom random = new SecureRandom();

                        int index = 0;

                        if (filenames.length > 1) {
                            index = random.nextInt(filenames.length);
                        }

                        String filename = filenames[index];

                        File payloadFile = new File(pendingFolder, filename);

                        String payload = FileUtils.readFileToString(payloadFile, "UTF-8");

                        int result = me.transmitHttpPayload(payload);

                        if (result == HttpTransmitter.RESULT_SUCCESS) {
                            me.mTransmitted += payloadFile.length();

                            payloadFile.delete();

                            me.mLastAttempt = 0;
                            me.transmit(true);
                        }
                        else {
                            try {
                                new JSONArray(payload);

                                // JSON is valid
                            } catch (JSONException e) {
                                // Invalid JSON, log results.

                                Logger.getInstance(me.mContext).logThrowable(e);

                                HashMap<String, Object> details = new HashMap<>();
                                payloadFile.renameTo(new File(payloadFile.getAbsolutePath() + HttpTransmitter.ERROR_FILE_EXTENSION));

                                details.put("name", payloadFile.getAbsolutePath());
                                details.put("size", payloadFile.length());

                                Logger.getInstance(me.mContext).log("corrupted_file", details);
                            }
                        }
                    } catch (IOException e) {
                        Logger.getInstance(me.mContext).logThrowable(e);
                    }
                }
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private int transmitHttpPayload(String payload) {
        if (payload == null || payload.trim().length() == 0) {
            return HttpTransmitter.RESULT_SUCCESS;
        }

        try {
            // Liberal HTTPS setup:
            // http://stackoverflow.com/questions/2012497/accepting-a-certificate-for-https-on-android

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

            SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();

            if (!this.mStrictSsl) {
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(null, null);

                socketFactory = new LiberalSSLSocketFactory(trustStore);
            }

            registry.register(new Scheme("https", socketFactory, 443));

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.MINUTES)
                    .readTimeout(3, TimeUnit.MINUTES)
                    .build();

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

            builder = builder.addPart(Headers.of("Content-Disposition", "form-data; name=\"payload\""), RequestBody.create(null, payload));

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

            if (response.code() >= 200 && response.code() < 300) {
                for (File f : toDelete) {
                    f.delete();
                }

                return HttpTransmitter.RESULT_SUCCESS;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return HttpTransmitter.RESULT_ERROR;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void closeOpenSession() throws IOException {
        File tempFile = this.mCurrentFile;

        if (this.mJsonGenerator == null || tempFile == null) {
            return;
        }

        synchronized (this) {
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
    private File getPendingFolder() {
        File internalStorage = this.mContext.getFilesDir();

        if (this.mUseExternalStorage) {
            internalStorage = this.mContext.getExternalFilesDir(null);
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

    private static long getFileSize(final File file)
    {
        if (file == null||!file.exists()) {
            return 0;
        }

        if (!file.isDirectory()) {
            return file.length();
        }

        final List<File> dirs = new LinkedList<>();

        dirs.add(file);

        long result=0;

        while(!dirs.isEmpty()) {
            final File dir = dirs.remove(0);

            if (!dir.exists()) {
                continue;
            }

            final File[] listFiles = dir.listFiles();

            if (listFiles==null||listFiles.length==0) {
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
    public long transmittedSize() {
        return this.mTransmitted;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onGeneratorUpdated(final String identifier, final long timestamp, Bundle data) {
        final HttpTransmitter me = this;

        final Parcel p = Parcel.obtain();
        p.writeBundle(data);
        p.setDataPosition(0);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                Bundle clonedData = p.readBundle();

                if (clonedData.keySet().size() > 1) {  // Only transmit non-empty bundles...
                    long generatorTimestamp = timestamp / 1000; // Convert to seconds...

                    Generators generators = Generators.getInstance(me.mContext);

                    Bundle metadata = new Bundle();

                    if (clonedData.containsKey(Generator.PDK_METADATA)) {
                        metadata = clonedData.getBundle(Generator.PDK_METADATA);
                    }

                    metadata.putString(Generator.IDENTIFIER, identifier);
                    metadata.putDouble(Generator.TIMESTAMP, generatorTimestamp);
                    metadata.putString(Generator.GENERATOR, generators.getGeneratorFullName(identifier));
                    metadata.putString(Generator.SOURCE, generators.getSource());
                    metadata.putString(Generator.SOURCE, me.mUserId);
                    clonedData.putBundle(Generator.PDK_METADATA, metadata);

                    synchronized (this) {
                        if (me.mJsonGenerator == null) {
                            me.mCurrentFile = new File(me.getPendingFolder(), System.currentTimeMillis() + HttpTransmitter.TEMP_EXTENSION);

                            try {
                                JsonFactory factory = new JsonFactory();
                                me.mJsonGenerator = factory.createGenerator(me.mCurrentFile, JsonEncoding.UTF8);
                                me.mJsonGenerator.writeStartArray();
                            } catch (IOException e) {
                                Logger.getInstance(me.mContext).logThrowable(e);
                            }
                        }

                        HttpTransmitter.writeBundle(me.mContext, me.mJsonGenerator, clonedData);
                    }
                }
            }
        };

        Thread t = new Thread(r);
        t.start();
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
    private static void writeBundle(Context context, JsonGenerator generator, Bundle bundle)
    {
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
                                Log.e("PDK", "LIST OBJ: " + o.getClass().getCanonicalName() + " IN " + key);
                            }
                        }

                        generator.writeEndArray();
                    }
                    else if (value instanceof Bundle) {
                        generator.writeFieldName(key);
                        HttpTransmitter.writeBundle(context, generator, (Bundle) value);
                    }
                    else {
                        Log.e("PR", "GOT TYPE " + value.getClass().getCanonicalName() + " FOR " + key);
                    }
                }
            }

            generator.writeEndObject();
        } catch (Exception e) {
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

    @SuppressWarnings("unused")
    public static class IncompleteConfigurationException extends RuntimeException {
        public IncompleteConfigurationException(String message) {
            super(message);
        }
    }
}
