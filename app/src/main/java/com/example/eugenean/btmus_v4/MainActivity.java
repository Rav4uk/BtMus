package com.example.eugenean.btmus_v4;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Set;


public class MainActivity extends Activity {
    private static final String TAG = "BtMus_v4";

    private Button btn_show, btn_con, btn_discon;
    private TextView name, address, state;

    private ListView devices;

    private BluetoothAdapter mBtAdapter;

    private String[] names;
    private String[] addresses;

    private String BtAddress = "";
    private String BtName = "";
    private String con_name = "";
    private String con_addr = "";

    private Intent i_send;

    private boolean con = false;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_main);

        btn_show = (Button) findViewById(R.id.button_show_devices);
        btn_con = (Button) findViewById(R.id.button_connect);
        btn_discon = (Button) findViewById(R.id.button_disconnect);

        name  = (TextView) findViewById(R.id.device_name);
        address  = (TextView) findViewById(R.id.device_address);
        state  = (TextView) findViewById(R.id.status);

        devices = (ListView) findViewById(R.id.devices);
        devices.setVisibility(View.INVISIBLE);

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        i_send = new Intent(MainActivity.this, mus_service.class);

        btn_con.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!BtAddress.isEmpty()) {
                    i_send.putExtra("addr", BtAddress);
                    i_send.putExtra("bt_name", BtName);
                    startService(i_send);
                    btn_con.setEnabled(false);
                }
                else
                    state.setText("Choose device");
            }
        });

        btn_discon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, mus_service.class));
                btn_con.setEnabled(true);
            }
        });

        btn_show.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                devices.setVisibility(View.VISIBLE);
                search();
            }
        });
    }

    public void search () {
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        names = new String[pairedDevices.size()];
        addresses = new String[pairedDevices.size()];

        int i=0;
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName(); // name
                names[i] = deviceName;
                String deviceHardwareAddress = device.getAddress(); // address
                addresses[i] = deviceHardwareAddress;
                i++;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, names);

        // присваиваем адаптер списку
        devices.setAdapter(adapter);
        devices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View itemClicked, int position, long id) {
                BtAddress = addresses[position];
                BtName = names[position];
                name.setText(BtName);
                address.setText(BtAddress);
                devices.setVisibility(View.INVISIBLE);
            }
        });
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra("message");
            Log.d(TAG, "Status: " + type);
            switch (type) {
                case "Connected":
                    state.setText("Connected to " + BtName);
                    con_name = intent.getStringExtra("name");
                    con_addr = intent.getStringExtra("addr");
                    con = true;
                    break;
                case "Disconnected":
                    state.setText("Disconnected");
                    con = false;
                    break;
                case "Trying":
                    state.setText("Connecting");
                    con = false;
                    break;
            }
        }
    };

    @Override
    protected void onResume()
    {
        super.onResume();
        LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(broadcastReceiver, new IntentFilter("NOW"));
        if (con) {
            name.setText(con_name);
            address.setText(con_addr);
            state.setText("Connected to " + con_name);
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        stopService(i_send);
    }



}