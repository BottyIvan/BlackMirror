package com.botty.blackmirror.Weather;

/**
 * Created by BottyIvan on 17/04/17.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.icu.util.Calendar;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.botty.blackmirror.DeviceMessage;
import com.botty.blackmirror.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.PublishCallback;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeCallback;
import com.google.android.gms.nearby.messages.SubscribeOptions;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class WeatherFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener {

    private static final int TTL_IN_SECONDS = 3 * 60; // Three minutes.

    // Key used in writing to and reading from SharedPreferences.
    private static final String KEY_UUID = "key_uuid";

    /**
     * Sets the time in seconds for a published message or a subscription to live. Set to three
     * minutes in this sample.
     */
    private static final Strategy PUB_SUB_STRATEGY = new Strategy.Builder()
            .setTtlSeconds(TTL_IN_SECONDS).build();

    /**
     * Creates a UUID and saves it to {@link SharedPreferences}. The UUID is added to the published
     * message to avoid it being undelivered due to de-duplication. See {@link DeviceMessage} for
     * details.
     */
    private static String getUUID(SharedPreferences sharedPreferences) {
        String uuid = sharedPreferences.getString(KEY_UUID, "");
        if (TextUtils.isEmpty(uuid)) {
            uuid = UUID.randomUUID().toString();
            sharedPreferences.edit().putString(KEY_UUID, uuid).apply();
        }
        return uuid;
    }

    /**
     * The entry point to Google Play Services.
     */
    private GoogleApiClient mGoogleApiClient;

    // Views.
    private SwitchCompat mPublishSwitch;
    private SwitchCompat mSubscribeSwitch;

    /**
     * The {@link Message} object used to broadcast information about the device to nearby devices.
     */
    private Message mPubMessage;

    /**
     * A {@link MessageListener} for processing messages from nearby devices.
     */
    private MessageListener mMessageListener;

    /**
     * Adapter for working with messages from nearby publishers.
     */
    private ArrayAdapter<String> mNearbyDevicesArrayAdapter;
    private static final String TAG = "WeatherFragment";
    Typeface weatherFont;

    TextView cityField;
    TextView updatedField;
    TextView detailsField;
    TextView currentTemperatureField;
    TextView weatherIcon;

    TextView mMotivational;
    Calendar c = Calendar.getInstance();
    int timeOfDay = c.get(Calendar.HOUR_OF_DAY);

    TextView mSSID;

    TextToSpeech ttsEngine;
    int timeDaySpeak;
    String phraseToSay;
    String correctPhareseAI;
    String detailsWeatherToSay;
    String cityWeatherToSay;
    String currentTemperatureToSay;

    Handler handler;

    public WeatherFragment(){
        handler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_weather, container, false);



        mSubscribeSwitch = (SwitchCompat)rootView.findViewById(R.id.subscribe_switch);
        mPublishSwitch = (SwitchCompat) rootView.findViewById(R.id.publish_switch);

        final LinearLayout linearLayout =(LinearLayout) rootView.findViewById(R.id.background);
        mPubMessage = DeviceMessage.newNearbyMessage(null);
        mMessageListener = new MessageListener() {
            @Override
            public void onFound(final Message message) {
                // Called when a new message is found.
                String str = DeviceMessage.fromNearbyMessage(message).getMessageBody();
                if (str.contains("viola")){
                    linearLayout.setBackgroundColor(getActivity().getColor(R.color.colorAccent));
                }
            }

            @Override
            public void onLost(final Message message) {
                // Called when a message is no longer detectable nearby.
            }
        };


        mPublishSwitch.setChecked(false);
        mSubscribeSwitch.setChecked(true);
        // If GoogleApiClient is connected, perform sub actions in response to user action.
        // If it isn't connected, do nothing, and perform sub actions when it connects (see
        // onConnected()).
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            if (mPublishSwitch.isChecked()) {
                subscribe();
            } else {
                unsubscribe();
            }
        }

        // If GoogleApiClient is connected, perform pub actions in response to user action.
        // If it isn't connected, do nothing, and perform pub actions when it connects (see
        // onConnected()).
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            if (mSubscribeSwitch.isChecked()) {
                publish();
            } else {
                unpublish();
            }
        }


        buildGoogleApiClient();


        cityField = (TextView)rootView.findViewById(R.id.city_field);
        updatedField = (TextView)rootView.findViewById(R.id.updated_field);
        detailsField = (TextView)rootView.findViewById(R.id.details_field);
        currentTemperatureField = (TextView)rootView.findViewById(R.id.current_temperature_field);
        weatherIcon = (TextView)rootView.findViewById(R.id.weather_icon);

        mMotivational = (TextView) rootView.findViewById(R.id.motivational_phrase);

        if(timeOfDay >= 0 && timeOfDay < 12){
            mMotivational.setText(R.string.good_morning);
            timeDaySpeak = 1;
        }else if(timeOfDay >= 12 && timeOfDay < 16){
            mMotivational.setText(R.string.good_afternoon);
            timeDaySpeak = 2;
        }else if(timeOfDay >= 16 && timeOfDay < 21){
            mMotivational.setText(R.string.good_evening);
            timeDaySpeak = 3;
        }else if(timeOfDay >= 21 && timeOfDay < 24){
            mMotivational.setText(R.string.good_night);
            timeDaySpeak = 4;
        }

        mSSID = (TextView) rootView.findViewById(R.id.ssid);
        mSSID.setText(getCurrentSsid(getContext()));

        weatherIcon.setTypeface(weatherFont);

        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        weatherFont = Typeface.createFromAsset(getActivity().getAssets(), "Fonts/weather.ttf");
        updateWeatherData(new CityPreference(getActivity()).getCity());

    }

    private void updateWeatherData(final String city){
        new Thread(){
            public void run(){
                final JSONObject json = RemoteFetch.getJSON(getActivity(), city);
                if(json == null){
                    handler.post(new Runnable(){
                        public void run(){
                            Toast.makeText(getActivity(),
                                    getActivity().getString(R.string.place_not_found),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    handler.post(new Runnable(){
                        public void run(){
                            renderWeather(json);
                        }
                    });
                }
            }
        }.start();
    }

    private void renderWeather(JSONObject json){
        try {
            cityField.setText(json.getString("name").toUpperCase(Locale.ITALIAN) +
                    ", " +
                    json.getJSONObject("sys").getString("country"));

            cityWeatherToSay = json.getString("name");
            JSONObject details = json.getJSONArray("weather").getJSONObject(0);
            JSONObject main = json.getJSONObject("main");
            detailsField.setText(
                    details.getString("description").toUpperCase(Locale.ITALIAN) +
                            "\n" + "Umindità: " + main.getString("humidity") + "%" +
                            "\n" + "Pressione: " + main.getString("pressure") + " hPa");
            detailsWeatherToSay = details.getString("description");
            currentTemperatureField.setText(
                    String.format("%.2f", main.getDouble("temp"))+ " ℃");

            currentTemperatureToSay = String.valueOf(main.getDouble("temp"));
            DateFormat df = DateFormat.getDateTimeInstance();
            String updatedOn = df.format(new Date(json.getLong("dt")*1000));
            updatedField.setText("Ultimo aggiornamento : " + updatedOn);

            setWeatherIcon(details.getInt("id"),
                    json.getJSONObject("sys").getLong("sunrise") * 1000,
                    json.getJSONObject("sys").getLong("sunset") * 1000);

        }catch(Exception e){
            Log.e("SimpleWeather", "One or more fields not found in the JSON data");
        } finally {
            ttsEngine = new TextToSpeech(getContext(),this);
        }
    }

    private void setWeatherIcon(int actualId, long sunrise, long sunset){
        int id = actualId / 100;
        String icon = "";
        if(actualId == 800){
            long currentTime = new Date().getTime();
            if(currentTime>=sunrise && currentTime<sunset) {
                icon = getActivity().getString(R.string.weather_sunny);
                correctPhareseAI = "Il tempo è bello, è ";
            } else {
                icon = getActivity().getString(R.string.weather_clear_night);
                correctPhareseAI = "E' una bella serata, il cielo è ";
            }
        } else {
            switch(id) {
                case 2 : icon = getActivity().getString(R.string.weather_thunder);
                    correctPhareseAI = "Il tempo è brutto, ci sono i ";
                    break;
                case 3 : icon = getActivity().getString(R.string.weather_drizzle);
                    correctPhareseAI = "Il tmepo è brutto, sta ";
                    break;
                case 7 : icon = getActivity().getString(R.string.weather_foggy);
                    correctPhareseAI = "Il tempo è brutto, c'è la ";
                    break;
                case 8 : icon = getActivity().getString(R.string.weather_cloudy);
                    correctPhareseAI = "Il tempo è abbastanza bello ma ci sono le ";
                    break;
                case 6 : icon = getActivity().getString(R.string.weather_snowy);
                    correctPhareseAI = "Il tempo è bello, c'è la ";
                    break;
                case 5 : icon = getActivity().getString(R.string.weather_rainy);
                    correctPhareseAI = "Il tempo brutto, c'è la ";
                    break;
            }
        }
        weatherIcon.setText(icon);
    }

    public void changeCity(String city){
        updateWeatherData(city);
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static String getCurrentSsid(Context context) {
        String ssid = null;
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
                ssid = connectionInfo.getSSID();
            } else {
                ssid = connManager.getActiveNetwork().toString();
            }
        } else {
            ssid = "Offline";
        }
        return ssid;
    }

    public void onInit(int status) {
        if(status == TextToSpeech.SUCCESS) {
            ttsEngine.setOnUtteranceCompletedListener(this);

            switch (timeDaySpeak){
                case 1:
                    phraseToSay = "buon giorno";
                    break;
                case 2:
                    phraseToSay = "buon pomeriggio";
                    break;
                case 3:
                    phraseToSay = "buona sera";
                    break;
                case 4:
                    phraseToSay = "buona notte";
                    break;
                default:
                    phraseToSay = "Non so proprio che cosa dire";
            }
            // Set up myTTS and speak the prompt text aloud
            ttsEngine.setLanguage(Locale.ITALIAN);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            if (!prefs.getBoolean("firstTime", false)) {
                // <---- run your one time code here
                speak("Ciao, Sono black mirror. Sono una sottospecie di assistente vocale ! Quindi... ");
                // mark first time has runned.
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("firstTime", true);
                editor.commit();
            }
            speak("hey," + phraseToSay + correctPhareseAI + detailsWeatherToSay + " a " + cityWeatherToSay + " e ci sono " + currentTemperatureToSay + " gradi.");
            } else {
            Log.e("TTS", "Initilization Failed!");
        }
    }

    // It's callback
    public void onUtteranceCompleted(String utteranceId) {
        Log.i(TAG, utteranceId); //utteranceId == "SOME MESSAGE"
    }

    private void speak(String text){
        if(text != null){
            HashMap<String, String> myHashAlarm = new HashMap<String, String>();
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "SOME MESSAGE");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ttsEngine.speak(text, TextToSpeech.QUEUE_ADD, null, null);
            }else{
                ttsEngine.speak(text, TextToSpeech.QUEUE_ADD, null);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (ttsEngine != null) {
            ttsEngine.stop();
            ttsEngine.shutdown();
        }
        super.onDestroy();
    }


    /*
    This is for GoogleApi Client
     */

    /**
     * Builds {@link GoogleApiClient}, enabling automatic lifecycle management using
     * {@link GoogleApiClient.Builder#enableAutoManage(FragmentActivity,
     * int, GoogleApiClient.OnConnectionFailedListener)}. I.e., GoogleApiClient connects in
     * {@link AppCompatActivity#onStart}, or if onStart() has already happened, it connects
     * immediately, and disconnects automatically in {@link AppCompatActivity#onStop}.
     */
    private void buildGoogleApiClient() {
        if (mGoogleApiClient != null) {
            return;
        }
        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addApi(Nearby.MESSAGES_API)
                .addConnectionCallbacks(this)
                .enableAutoManage(getActivity(), this)
                .build();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        mPublishSwitch.setEnabled(false);
        mSubscribeSwitch.setEnabled(false);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "GoogleApiClient connected");
        // We use the Switch buttons in the UI to track whether we were previously doing pub/sub (
        // switch buttons retain state on orientation change). Since the GoogleApiClient disconnects
        // when the activity is destroyed, foreground pubs/subs do not survive device rotation. Once
        // this activity is re-created and GoogleApiClient connects, we check the UI and pub/sub
        // again if necessary.
        if (mPublishSwitch.isChecked()) {
            publish();
        }
        if (mSubscribeSwitch.isChecked()) {
            subscribe();
        }
    }

    /**
     * Subscribes to messages from nearby devices and updates the UI if the subscription either
     * fails or TTLs.
     */
    private void subscribe() {
        Log.i(TAG, "Subscribing");
//        mNearbyDevicesArrayAdapter.clear();
        SubscribeOptions options = new SubscribeOptions.Builder()
                .setStrategy(PUB_SUB_STRATEGY)
                .setCallback(new SubscribeCallback() {
                    @Override
                    public void onExpired() {
                        super.onExpired();
                        Log.i(TAG, "No longer subscribing");
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mSubscribeSwitch.setChecked(false);
                            }
                        });
                    }
                }).build();

        Nearby.Messages.subscribe(mGoogleApiClient, mMessageListener, options)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Subscribed successfully.");
                        } else {
                            mSubscribeSwitch.setChecked(false);
                        }
                    }
                });
    }

    /**
     * Publishes a message to nearby devices and updates the UI if the publication either fails or
     * TTLs.
     */
    private void publish() {
        Log.i(TAG, "Publishing");
        PublishOptions options = new PublishOptions.Builder()
                .setStrategy(PUB_SUB_STRATEGY)
                .setCallback(new PublishCallback() {
                    @Override
                    public void onExpired() {
                        super.onExpired();
                        Log.i(TAG, "No longer publishing");
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mPublishSwitch.setChecked(false);
                            }
                        });
                    }
                }).build();

        Nearby.Messages.publish(mGoogleApiClient, mPubMessage, options)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Published successfully.");
                        } else {
                            mPublishSwitch.setChecked(false);
                        }
                    }
                });
    }

    /**
     * Stops subscribing to messages from nearby devices.
     */
    private void unsubscribe() {
        Log.i(TAG, "Unsubscribing.");
        Nearby.Messages.unsubscribe(mGoogleApiClient, mMessageListener);
    }

    /**
     * Stops publishing message to nearby devices.
     */
    private void unpublish() {
        Log.i(TAG, "Unpublishing.");
        Nearby.Messages.unpublish(mGoogleApiClient, mPubMessage);
    }

}