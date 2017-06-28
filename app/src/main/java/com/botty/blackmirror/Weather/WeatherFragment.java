package com.botty.blackmirror.Weather;

/**
 * Created by BottyIvan on 17/04/17.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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

public class WeatherFragment extends Fragment implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener {

    private static final int TTL_IN_SECONDS = 600000; // 10 minutes.

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
    TextView mVerApp;

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

    private Thread repeatTaskThread;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_weather, container, false);

        cityField = (TextView)rootView.findViewById(R.id.city_field);
        updatedField = (TextView)rootView.findViewById(R.id.updated_field);
        detailsField = (TextView)rootView.findViewById(R.id.details_field);
        currentTemperatureField = (TextView)rootView.findViewById(R.id.current_temperature_field);
        weatherIcon = (TextView)rootView.findViewById(R.id.weather_icon);

        mMotivational = (TextView) rootView.findViewById(R.id.motivational_phrase);

        mVerApp = (TextView) rootView.findViewById(R.id.appVer);
        mVerApp.setText(getVersionApp(getActivity()));

        mSSID = (TextView) rootView.findViewById(R.id.ssid);
        mSSID.setText(getCurrentSsid(getContext()));

        weatherIcon.setTypeface(weatherFont);

        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        weatherFont = Typeface.createFromAsset(getActivity().getAssets(), "Fonts/weather.ttf");
        RepeatTask();
    }

    private void RepeatTask() {
        repeatTaskThread = new Thread() {
            public void run() {
                while (true) {
                    // Update TextView in runOnUiThread
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateWeatherData(new CityPreference(getActivity()).getCity());
                            Log.w(TAG,"Fetching new data and saying it");
                        }
                    });
                    try {
                        // Sleep for 10 minutes
                        Thread.sleep(TTL_IN_SECONDS);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
        };
        repeatTaskThread.start();
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
                switch (TimeOf()){
                    case 1:
                        mMotivational.setText(R.string.good_morning);
                        break;
                    case 2:
                        mMotivational.setText(R.string.good_afternoon);
                        break;
                    case 3:
                        mMotivational.setText(R.string.good_evening);
                        break;
                    case 4:
                        //notare l'esater egg :D
                        mMotivational.setText(R.string.good_night);
                        break;
                }
            } else {
                icon = getActivity().getString(R.string.weather_clear_night);
                correctPhareseAI = "E' una bella serata, il cielo è ";
                mMotivational.setText("E' una bella serata");
            }
        } else {
            switch(id) {
                case 2 : icon = getActivity().getString(R.string.weather_thunder);
                    correctPhareseAI = "Il tempo è brutto, ci sono i ";
                    switch (TimeOf()){
                        case 1:
                            mMotivational.setText(R.string.thunder_morning);
                            break;
                        case 2:
                            mMotivational.setText(R.string.thunder_afternoon);
                            break;
                        case 3:
                            mMotivational.setText(R.string.thunder_evening);
                            break;
                        case 4:
                            //notare l'esater egg :D
                            mMotivational.setText(R.string.good_night);
                            break;
                    }
                    break;
                case 3 : icon = getActivity().getString(R.string.weather_drizzle);
                    correctPhareseAI = "Il tempo è brutto, sta ";
                    switch (TimeOf()){
                        case 1:
                            mMotivational.setText(R.string.drizzle_morning);
                            break;
                        case 2:
                            mMotivational.setText(R.string.drizzle_afternoon);
                            break;
                        case 3:
                            mMotivational.setText(R.string.drizzle_evening);
                            break;
                        case 4:
                            //notare l'esater egg :D
                            mMotivational.setText(R.string.good_night);
                            break;
                    }
                    break;
                case 7 : icon = getActivity().getString(R.string.weather_foggy);
                    correctPhareseAI = "Il tempo è brutto, c'è la ";
                    switch (TimeOf()){
                        case 1:
                            mMotivational.setText(R.string.foggy_morning);
                            break;
                        case 2:
                            mMotivational.setText(R.string.foggy_afternoon);
                            break;
                        case 3:
                            mMotivational.setText(R.string.foggy_evening);
                            break;
                        case 4:
                            //notare l'esater egg :D
                            mMotivational.setText(R.string.good_night);
                            break;
                    }
                    break;
                case 8 : icon = getActivity().getString(R.string.weather_cloudy);
                    correctPhareseAI = "Il tempo è abbastanza bello ma ci sono le ";
                    switch (TimeOf()){
                        case 1:
                            mMotivational.setText(R.string.cloudy_morning);
                            break;
                        case 2:
                            mMotivational.setText(R.string.cloudy_afternoon);
                            break;
                        case 3:
                            mMotivational.setText(R.string.cloudy_evening);
                            break;
                        case 4:
                            //notare l'esater egg :D
                            mMotivational.setText(R.string.good_night);
                            break;
                    }
                    break;
                case 6 : icon = getActivity().getString(R.string.weather_snowy);
                    correctPhareseAI = "Il tempo è bello, c'è la ";
                    switch (TimeOf()){
                        case 1:
                            mMotivational.setText(R.string.snowy_morning);
                            break;
                        case 2:
                            mMotivational.setText(R.string.snowy_afternoon);
                            break;
                        case 3:
                            mMotivational.setText(R.string.snowy_evening);
                            break;
                        case 4:
                            //notare l'esater egg :D
                            mMotivational.setText(R.string.good_night);
                            break;
                    }
                    break;
                case 5 : icon = getActivity().getString(R.string.weather_rainy);
                    correctPhareseAI = "Il tempo brutto, c'è la ";
                    switch (TimeOf()){
                        case 1:
                            mMotivational.setText(R.string.rainy_morning);
                            break;
                        case 2:
                            mMotivational.setText(R.string.rainy_afternoon);
                            break;
                        case 3:
                            mMotivational.setText(R.string.rainy_evening);
                            break;
                        case 4:
                            //notare l'esater egg :D
                            mMotivational.setText(R.string.good_night);
                            break;
                    }
                    break;
            }
        }
        weatherIcon.setText(icon);
    }

    public int TimeOf(){
        if(timeOfDay >= 0 && timeOfDay < 12){
            timeDaySpeak = 1;
        }else if(timeOfDay >= 12 && timeOfDay < 16){
            timeDaySpeak = 2;
        }else if(timeOfDay >= 16 && timeOfDay < 21){
            timeDaySpeak = 3;
        }else if(timeOfDay >= 21 && timeOfDay < 24){
            timeDaySpeak = 4;
        }
        return timeDaySpeak;
    }

    public void changeCity(String city){
        updateWeatherData(city);
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

    public static String getVersionApp(Context context){
        PackageInfo pInfo = null;
        String str = null;
        try {
            pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String version = pInfo.versionName;
        int verCode = pInfo.versionCode;
        str =  context.getString(R.string.app_name)+" "+" "+version+" ( "+verCode+" ) ";
        return str;
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
                speak("Ciao, Sono black mirror. Sono una sottospecie di assistente vocale ! Quindi... Ecco le informazioni " + phraseToSay + correctPhareseAI + detailsWeatherToSay + " a " + cityWeatherToSay + " e ci sono " + currentTemperatureToSay + " gradi.");
                // mark first time has runned.
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("firstTime", true);
                editor.commit();
            } else {
                speak("Ciao," + phraseToSay + correctPhareseAI + detailsWeatherToSay + " a " + cityWeatherToSay + " e ci sono " + currentTemperatureToSay + " gradi.");
            }
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

}