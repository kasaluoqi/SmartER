package com.smarter.com.smarter;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Parcel;
import android.support.design.widget.Snackbar;
import android.util.JsonReader;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.smarter.tools.Datetools;
import com.smarter.tools.ParcelableUtil;
import com.smarter.tools.RandomGenerator;
import com.smarter.tools.Weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static com.smarter.tools.Tool.BASE_URL;

public class DbUpdateReceiver extends BroadcastReceiver {

    private static int count = 0;
    private Context context;
    protected Database dbManager;
    private RandomGenerator rng;

    private int resid;
    private String address;
    private static int continuous = 0;
    private int hour;
    private static int counter = 0;
    private double temperature;
    private double fridge;
    private double conditioner;
    private double washmach;

    private Resident object;
    private boolean flag;

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onReceive(final Context context, Intent intent) {

        byte[] bytes = intent.getByteArrayExtra("resident");
        Parcel parcel = ParcelableUtil.unmarshall(bytes);
        object = new Resident(parcel);
        flag = intent.getBooleanExtra("flag",false);
        resid = object.getResid();
        address = object.getAddress();

        this.context = context;

        dbManager = new Database(context);
        new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }
            @Override
            protected String doInBackground(Void... voids) {
                ConnectivityManager cm =
                        (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null &&
                        activeNetwork.isConnectedOrConnecting();
                if(!isConnected)
                    return "Network unavailable";
                temperature = Weather.getWeather(address,context);
                return null;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                if (s!= null && s.equals("Network unavailable")) {
//                                Toast.makeText(getApplicationContext(), "Login Done", Toast.LENGTH_SHORT).show();
                    Log.e("error", "network unavailable");
                }
                else {
                    hour = Datetools.getCurHour();
                    rng = new RandomGenerator();
                    count++;
                    Log.i("Alarmer_Check", "alarm!");
                    updateToDatabase();
                    if (count == 24 || flag) {
                        count = 0;
                        updateToServer();
                    }
//                Toast.makeText(context, "Alarm....", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }


    public JSONObject readData(Cursor c) throws ParseException, JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("usagedate", c.getString(1));
        jsonObject.put("hours", c.getInt(2));
        jsonObject.put("fridge", c.getDouble(3));
        jsonObject.put("aircond", c.getDouble(4));
        jsonObject.put("washmach", c.getDouble(5));
        jsonObject.put("temperature", c.getDouble(6));
        return jsonObject;
    }

    public void deleteAllData(){
        try {
            dbManager.open();
        }catch(SQLException e) {
            e.printStackTrace();
        }
        dbManager.deleteAll();
        dbManager.close();
    }

    public void deleteData(){
        try {
            dbManager.open();
        }catch(SQLException e) {
            e.printStackTrace();
        }
        for (int i = 1; i <= 24; i++) {
            dbManager.deleteUsage(String.valueOf(i));
        }
        dbManager.close();
    }

    @SuppressLint("StaticFieldLeak")
    private void updateToServer() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {

                ConnectivityManager cm =
                        (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null &&
                        activeNetwork.isConnectedOrConnecting();
                if(!isConnected)
                    return "Network unavailable";

                String url = BASE_URL + "/Assignment/webresources/restws.usage/postUsage ";
                try {
                    URL restful = new URL(url);
                    HttpURLConnection connection;
                    Gson gson = new Gson();
                    String myData = gson.toJson(object);
                    JSONObject resid = new JSONObject(myData);
//                    deleteData();
                    try {
                        dbManager.open();
                    }catch(SQLException e) {
                        e.printStackTrace();
                    }
                    Cursor c = dbManager.getAllUsages();

                    if (c.moveToFirst()) {
                        do {

                            connection = (HttpURLConnection) restful.openConnection();
                            connection.setReadTimeout(10000);
                            connection.setConnectTimeout(15000);
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Content-Type", "application/json");
                            connection.setDoOutput(true);
                            JSONObject jsonObject = readData(c);
                            jsonObject.put("resid",resid);
                            String json = jsonObject.toString();
                            connection.getOutputStream().write(json.getBytes());

                            if (connection.getResponseCode() != 200) {
                                Log.e("Login","Connect Error: Code is " + connection.getResponseCode());
                            }

                            connection.disconnect();
                        } while (c.moveToNext());
                    }



                    dbManager.close();
                } catch (ProtocolException e1) {
                    e1.printStackTrace();
                } catch (MalformedURLException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                } catch (ParseException e1) {
                    e1.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return "Done";
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
//                deleteData();
                if (s!= null && s.equals("Network unavailable")) {
//                                Toast.makeText(getApplicationContext(), "Login Done", Toast.LENGTH_SHORT).show();
                   Log.e("error", "network unavailable");
                }
                else
                    deleteAllData();

                Log.i("Alarmer_Check","Done Upload!");
            }
        }.execute();
    }

    private void updateToDatabase() {
        if(hour == 0) {
            continuous = 0;
            counter = 0;
        }
        if(continuous < 3) {
            if (conditioner > 0.f)
                continuous++;
            else
                continuous = 0;
        }
        if(counter < 10) {
            if (washmach > 0.f)
                counter++;
        }
        rng.generateHour(continuous, hour, counter, temperature);
        fridge = rng.getFridge();
        conditioner = rng.getConditioner();
        washmach = rng.getWashmach();
        try {
            dbManager.open();
        } catch(SQLException e) {
            e.printStackTrace();
        }
        dbManager.insertUsage(resid, Datetools.getDate(), hour, fridge, conditioner, washmach, temperature);
        dbManager.close();
    }
}
