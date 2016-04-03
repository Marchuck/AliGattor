package pl.lukaszmarczak.ble_example;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pl.lukaszmarczak.ble_example.aligattor.AliGattor;
import pl.lukaszmarczak.ble_example.bless.BLEss;
import pl.lukaszmarczak.ble_example.bless.BleDevice;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();
    final BLEss bless = new BLEss();
    private AliGattor gattor;
    Set<BleDevice> list = new HashSet<>();
    boolean findGattOnce = true;
    FloatingActionButton fab;
    FloatingActionButton fab2;

    boolean blinkAtStartOnce = true;
    boolean switchMessage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab2 = (FloatingActionButton) findViewById(R.id.fab2);
        fab2.setVisibility(View.GONE);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendValue();
            }
        });
        Log.d(TAG, "onCreate: ");

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context ctx = MainActivity.this;
                if (!BLEss.isBluetooth(ctx)) {
                    Toast.makeText(MainActivity.this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!BLEss.isBLE(ctx)) {
                    Toast.makeText(MainActivity.this, "BLE not supported", Toast.LENGTH_SHORT).show();
                    return;
                }
                long seconds = 5;
                Log.d(TAG, "start scan ");
                bless.scan(seconds * 1000, new BLEss.BleCallback() {
                    @Override
                    public void onBleReceived(final BleDevice bleDevice) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "run: " + bleDevice.toString());
                                list.add(bleDevice);
                                Toast.makeText(MainActivity.this, bleDevice.toString(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }, new BLEss.ErrorHandler() {
                    @Override
                    public void onError(final String errorMessage) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, errorMessage,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }, new BLEss.Callable() {
                    @Override
                    public void call() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Finished scan",
                                        Toast.LENGTH_SHORT).show();
                                Log.i(TAG, "received devices:");
                                for (BleDevice device : list) {
                                    Log.i(TAG, "device: " + device.toString());
                                    if (findGattOnce && device.bluetoothDevice.getName().contains("HM")) {
                                        findGattOnce = false;
                                        connect(device.bluetoothDevice);
                                    }
                                }
                            }
                        });
                    }
                });
            }
        });
    }


    void sendValue() {
        if (gattor == null) return;
        switchMessage = !switchMessage;
        String value = switchMessage ? "a" : "b";
        gattor.writeValueAsync(value).subscribeOn(Schedulers.io())
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        Log.i(TAG, "call: " + aBoolean);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.e(TAG, "call: " + throwable.getMessage());
                        throwable.printStackTrace();
                    }
                });
    }


    private void connect(final BluetoothDevice device) {
        Log.d(TAG, "connect: ");
        gattor = new AliGattor(this, device.getAddress());

        gattor.setAfterConnectedCallback(new AliGattor.GattServicesCallback() {
            @Override
            public void onReceived(List<BluetoothGattService> services) {
                Log.i(TAG, "onReceived: ");
            }
        });
        gattor.setConnectionListener(new AliGattor.ConnectionStateListener() {
            @Override
            public void onStateChange(final AliGattor.ConnectionState state) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "run: ");
                        if (blinkAtStartOnce && state == AliGattor.ConnectionState.CONNECTED) {
                            blinkAtStartOnce = false;
                            fab2.setVisibility(View.VISIBLE);
                            sendValue();
                            new Handler(Looper.getMainLooper())
                                    .postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            sendValue();
                                        }
                                    }, 1000);
                        }
                    }
                }, 1000);
            }
        });
    }

    @Override
    protected void onPause() {
        if (gattor != null) gattor.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gattor != null) gattor.onResume();
    }

    @Override
    protected void onDestroy() {
        bless.onDestroy();
        if (gattor != null) gattor.onDestroy();
        super.onDestroy();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
