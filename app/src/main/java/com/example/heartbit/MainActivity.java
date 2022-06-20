package com.example.heartbit;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;

    private int intBufferSize;
    private short[] shortAudioData;
    String saved_audio_name=null;
    private boolean isActive = false;
    private Thread thread;
    static Context context;
    Recorder recorder;
    TextView status,recorder_timer;
    Button recordBtn;
    int count=0,wait_for_server=0;
    Timer T;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try
        {
            this.getSupportActionBar().hide();
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);

        }
        catch (NullPointerException e){}
        setContentView(R.layout.activity_main);
         context = getApplicationContext();
        status=findViewById(R.id.textViewStatus);
        recorder_timer=findViewById(R.id.record_timer);
        recordBtn=findViewById(R.id.start_btn);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PackageManager.PERMISSION_GRANTED);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);
        recorder= new Recorder();


        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //threadLoop();
               recorder.recordWavStart();
            }
        });
    }

    public void buttonAction(View view) {

        if(isActive)
        {
            stopRecording();
        }
        else
        {
            start_recording();
        }


    }
    public void start_recording()
    {
        recordBtn.setText("\uf0c8");
        isActive = true;
        start_Timer();
        thread.start();
    }
    public void stopRecording() {
        recordBtn.setText("\uf130");
        isActive = false;
        stopTimer();
        recorder_timer.setText("Recording Stopped.Tap Send for result.");
        saved_audio_name=recorder.recordWavStop();

    }
    public void buttonSend(View view) {
        UploadFileAsync.audio_name=saved_audio_name;
        new UploadFileAsync().execute("saved_audio_name");
        T=new Timer();
        recorder_timer.setVisibility(View.VISIBLE);
        T.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if(wait_for_server>=3)
                        {
                            status.setText("Your Heart condition is "+UploadFileAsync.status);
                        }

                        //recorder_timer.setText("Recording: "+count+"s");

                    }
                });

                wait_for_server++;
            }
        }, 1000, 1000);
    }

public void start_Timer()
{
    T=new Timer();
    recorder_timer.setVisibility(View.VISIBLE);
    T.scheduleAtFixedRate(new TimerTask() {
        @Override
        public void run() {

            runOnUiThread(new Runnable() {

                @Override
                public void run() {


                    //recorder_timer.setText("Your Heart condition is "+UploadFileAsync.status);
                    recorder_timer.setText("Recording: "+count+"s");

                }
            });

            count++;
        }
    }, 1000, 1000);
}
public void stopTimer(){
    T.cancel();
    count=0;
}
}

class UploadFileAsync extends AsyncTask<String, Void, String> {
    static String audio_name;
    static String status;
    @Override
    protected String doInBackground(String... params) {

        try {

            String sourceFileUri = Environment.getExternalStorageDirectory() + File.separator + "AudioRecord/"+audio_name+".wav";;

            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****";
            int bytesRead, bytesAvailable, bufferSize;
            byte[] buffer;
            int maxBufferSize = 1 * 1024 * 1024;
            File sourceFile = new File(sourceFileUri);

            if (sourceFile.isFile()) {

                try {
                    String upLoadServerUri = "https://heartbeat-classification-rest.herokuapp.com/api/upload/";

                    // open a URL connection to the Servlet
                    FileInputStream fileInputStream = new FileInputStream(
                            sourceFile);
                    URL url = new URL(upLoadServerUri);

                    // Open a HTTP connection to the URL
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setDoInput(true); // Allow Inputs
                    conn.setDoOutput(true); // Allow Outputs
                    conn.setUseCaches(false); // Don't use a Cached Copy
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("ENCTYPE",
                            "multipart/form-data");
                    conn.setRequestProperty("Content-Type",
                            "multipart/form-data;boundary=" + boundary);
                    conn.setRequestProperty("bill", sourceFileUri);

                    dos = new DataOutputStream(conn.getOutputStream());

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"bill\";filename=\""
                            + sourceFileUri + "\"" + lineEnd);

                    dos.writeBytes(lineEnd);

                    // create a buffer of maximum size
                    bytesAvailable = fileInputStream.available();

                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    buffer = new byte[bufferSize];

                    // read file and write it into form...
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                    while (bytesRead > 0) {

                        dos.write(buffer, 0, bufferSize);
                        bytesAvailable = fileInputStream.available();
                        bufferSize = Math
                                .min(bytesAvailable, maxBufferSize);
                        bytesRead = fileInputStream.read(buffer, 0,
                                bufferSize);

                    }

                    // send multipart form data necesssary after file
                    // data...
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(twoHyphens + boundary + twoHyphens
                            + lineEnd);

                    // Responses from the server (code and message)
                    int serverResponseCode = conn.getResponseCode();
                    String serverResponseMessage = conn
                            .getResponseMessage();



                    Log.e("temp", String.valueOf(serverResponseCode));
                    Log.e("temp",serverResponseMessage);




                    if (serverResponseCode == 200) {

                        InputStream in = new BufferedInputStream(conn.getInputStream());
                        BufferedReader r = new BufferedReader(new InputStreamReader(in));
                        StringBuilder total = new StringBuilder();
                        for (String line; (line = r.readLine()) != null; ) {
                            total.append(line).append('\n');
                        }
                        Log.e("temp", String.valueOf(total));
                        UploadFileAsync.status=String.valueOf(total);

                    }
                    else {
                        InputStream in = new BufferedInputStream(conn.getErrorStream());
                        BufferedReader r = new BufferedReader(new InputStreamReader(in));
                        StringBuilder total = new StringBuilder();
                        for (String line; (line = r.readLine()) != null; ) {
                            total.append(line).append('\n');
                        }
                        Log.e("temp", String.valueOf(total));


                    }

                    // close the streams //
                    fileInputStream.close();
                    dos.flush();
                    dos.close();

                } catch (Exception e) {

                    // dialog.dismiss();
                    e.printStackTrace();

                }
                // dialog.dismiss();

            } // End else block


        } catch (Exception ex) {
            // dialog.dismiss();

            ex.printStackTrace();
        }
        return "Executed";
    }

    @Override
    protected void onPostExecute(String result) {

    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected void onProgressUpdate(Void... values) {
    }
}
