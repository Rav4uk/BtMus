package com.example.eugenean.btmus_v4;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Created by eugenean on 09/05/2017.
 */

public class mus_service extends Service {

    // variables

    private boolean running = false;
    private boolean bt_connected = false;

    private BroadcastReceiver mReceiver;

    final String TAG = "mus_service";

    private Intent for_sending;

    private Handler h; // communication between threads

    final int RECIEVE_MESSAGE = 1; // case for Handler

    private AudioManager mAudioManager;

    private BluetoothDevice device = null;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder sb = new StringBuilder();

    private GettingDataThread GetThread; // Get Data from BT device
    private WorkingDataThread WorkThread; // Work with data
    private ConnectThread ConThread; // set up connection

    // SPP UUID
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // BT device address and name
    private String address, name;

    private int data = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        running = true;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        address = intent.getExtras().getString("addr");
        name = intent.getExtras().getString("bt_name");
        Log.d(TAG, address);

        // communication between threads

        h = new Handler() {
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);
                        sb.append(strIncom);
                        int endOfLineIndex = sb.indexOf("\r\n");
                        if (endOfLineIndex > 0) {
                            String sbprint = sb.substring(0, endOfLineIndex);
                            sb.delete(0, sb.length());
                            data = to_int(sbprint);
                        }
                        break;
                }
            };
        };

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        // BtDevice
        device = btAdapter.getRemoteDevice(address);

        // Connection listner

        IntentFilter filter = new IntentFilter();
        filter.addAction(device.ACTION_ACL_CONNECTED);
        filter.addAction(device.ACTION_ACL_DISCONNECTED);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (device.ACTION_ACL_CONNECTED.equals(action)) {
                    bt_connected = true;
                }
                else if (device.ACTION_ACL_DISCONNECTED.equals(action)) {
                    bt_connected = false;
                }
            }
        };

        this.registerReceiver(mReceiver, filter);

        // launch connection thread

        ConThread = new ConnectThread();
        ConThread.start();

        for_sending = new Intent();
        for_sending.setAction("NOW");

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mReceiver);
        running = false;
        if (bt_connected) {
            GetThread.cancel();
            Log.d (TAG, "GetThread.cancel()");
        }
        bt_connected = false;
        Log.d(TAG, "onDestroy");
        for_sending.putExtra("message","Disconnected");
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(for_sending);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return null;
    }

    //cChecking input data from Bluetooth device

    private int to_int(String in) {
            String text = in.replaceAll("[^0-9]+","");
            if (!text.isEmpty())
                return Integer.parseInt(text);
            else
                return 0;
    }

    private void errorExit(String title, String message){
        Log.d (TAG, title + ": " + message);
        onDestroy();
    }

    private class ConnectThread extends Thread {

        private boolean flag;

        public void run () {
            while (running) {
                // connecting
                while (running) {
                    Log.d (TAG, ".......trying to connect");
                    for_sending.putExtra("message","Trying");
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(for_sending);
                    flag = ConThread.connect();
                    if (flag) {
                        WorkThread = new WorkingDataThread();
                        GetThread = new GettingDataThread(btSocket);
                        WorkThread.start();
                        GetThread.start();
                        bt_connected = true;
                        Log.d(TAG, "..............working...");
                        for_sending.putExtra("message","Connected");
                        for_sending.putExtra("name", name);
                        for_sending.putExtra("addr", address);
                        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(for_sending);
                        break;
                    }
                    try {
                        ConnectThread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // connected
                while (running) {
                    Log.d(TAG, ".............................");
                    if (!bt_connected) {
                        // disconnected
                        Log.d (TAG, "......DISCON");
                        break;
                    }
                    try {
                        ConnectThread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        }


        public boolean connect() {

            Log.d(TAG, "connect() start");

            try {
                btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
            }

            btAdapter.cancelDiscovery();

            Log.d(TAG, "...connecting...");
            try {
                btSocket.connect();
                Log.d(TAG, "...Connected...");
                return true;
            } catch (IOException e) {
                try {
                    btSocket.close();
                    Log.d(TAG, "Failed to connect");
                } catch (IOException e2) {
                    errorExit("Fatal Error", "Text: " + e2.getMessage() + ".");
                }
            }
            return false;
        }
    }

    // get data

    private class GettingDataThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;

        public GettingDataThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;

            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (bt_connected) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();	// send data to handler

                } catch (IOException e) {
                    break;
                }
            }
        }



        // close socket
        public void cancel() {
            try {
                Log.d (TAG, "......CLOSED");
                mmSocket.close();
            } catch (IOException e) {
                Log.d (TAG, "......CLOSING FAILED");
            }
        }
    }

    // work with data

    private class WorkingDataThread extends Thread {

        public void run() {
            while(bt_connected) {
                if (data == 111)
                {
                    mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
                    data = 0;
                    try {
                        WorkingDataThread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (data == 222)
                {
                    media_called(KeyEvent.KEYCODE_MEDIA_NEXT);
                }
                if (data == 333)
                {
                    media_called(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                }
                if (data == 444)
                {
                    mAudioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
                    data = 0;
                    try {
                        WorkingDataThread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (data == 555)
                {
                    media_called(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                }
            }
        }


        // Function for previous, play/pause, next
        public void media_called (int key) {
            mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
            mAudioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
            data = 0;
            try {
                WorkingDataThread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
