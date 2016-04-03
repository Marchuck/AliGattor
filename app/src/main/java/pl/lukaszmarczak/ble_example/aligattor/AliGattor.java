package pl.lukaszmarczak.ble_example.aligattor;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.movisens.smartgattlib.Characteristic;
import com.movisens.smartgattlib.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import pl.lukaszmarczak.ble_example.MainActivity;
import rx.Observable;
import rx.Subscriber;


/**
 * Created by Łukasz Marczak
 *
 * @since 02.04.16
 */
public class AliGattor {
    public static final String TAG = AliGattor.class.getSimpleName();

    public enum ConnectionState {CONNECTED, DISCONNECTED}

    private ConnectionState connectionState = ConnectionState.DISCONNECTED;

    public interface ConnectionStateListener {
        void onStateChange(ConnectionState state);
    }

    public interface GattServicesCallback {
        void onReceived(List<BluetoothGattService> services);
    }

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private ConnectionStateListener connectionStateListener;
    private GattServicesCallback connectedCallback;

    public void setAfterConnectedCallback(GattServicesCallback connectedCallback) {
        this.connectedCallback = connectedCallback;
    }

    public void setConnectionListener(ConnectionStateListener stateListener) {
        this.connectionStateListener = stateListener;
    }

    @NonNull
    private MainActivity activity;
    @NonNull
    private String btDeviceAddress;
    private BluetoothLowEnergyService mBluetoothLeService;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLowEnergyService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                activity.finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(btDeviceAddress);
            mBluetoothLeService.communicationProxy = new BluetoothLowEnergyService.CommunicationProxy() {
                @Override
                public void call(String x) {
                    Log.i(TAG, "call: " + x);
                    if (x.contains("DISCOVERED")) {
                        connectedCallback.onReceived(null);
                        connectionStateListener.onStateChange(ConnectionState.CONNECTED);
                    }
                }
            };
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    public AliGattor(@NonNull MainActivity _activity, @NonNull String _btDeviceAddress) {
        this.activity = _activity;
        this.btDeviceAddress = _btDeviceAddress;
        Intent gattServiceIntent = new Intent(activity, BluetoothLowEnergyService.class);
        this.activity.bindService(gattServiceIntent, mServiceConnection, Activity.BIND_AUTO_CREATE);
    }

    public void onResume() {
        activity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        gattServiceRegistered = true;
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(btDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    private BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.w(TAG, "onReceive: " + action);
            if (BluetoothLowEnergyService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.i(TAG, "onConnected: ");
                connectionState = ConnectionState.CONNECTED;
                if (connectionStateListener != null)
                    connectionStateListener.onStateChange(connectionState);
            } else if (BluetoothLowEnergyService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i(TAG, "onDisconnected");
                connectionState = ConnectionState.DISCONNECTED;
                if (connectionStateListener != null)
                    connectionStateListener.onStateChange(connectionState);
            } else if (BluetoothLowEnergyService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.i(TAG, "onDiscovered");
                // Show all the supported services and characteristics on the user interface.
                if (connectedCallback != null)
                    connectedCallback.onReceived(mBluetoothLeService.getSupportedGattServices());
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLowEnergyService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.i(TAG, "onDataAvailable");
                String extraString = (intent.getStringExtra(BluetoothLowEnergyService.EXTRA_DATA));
                Log.i(TAG, "onReceived extra data: " + extraString);
            }
        }
    };

    public rx.Observable<Boolean> writeValueAsync(String value) {
        return writeValueAsync(value.getBytes());
    }

    public rx.Observable<Boolean> writeValueAsync(final byte[] value) {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                if (mBluetoothLeService == null) return;
                subscriber.onNext(mBluetoothLeService.writeValue(value));
            }
        });
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = "Unknown service";
        String unknownCharaString = "Unknown characteristic";
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
                new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            currentServiceData.put(
                    LIST_NAME, Service.lookup(gattService.getUuid(), unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, Characteristic.lookup(gattCharacteristic.getUuid(), unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

//        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
//                activity,
//                gattServiceData,
//                android.R.layout.simple_expandable_list_item_2,
//                new String[]{LIST_NAME, LIST_UUID},
//                new int[]{android.R.id.text1, android.R.id.text2},
//                gattCharacteristicData,
//                android.R.layout.simple_expandable_list_item_2,
//                new String[]{LIST_NAME, LIST_UUID},
//                new int[]{android.R.id.text1, android.R.id.text2}
//        );
//        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLowEnergyService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLowEnergyService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLowEnergyService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLowEnergyService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onPause() {
        if (gattServiceRegistered) {
            activity.unregisterReceiver(mGattUpdateReceiver);
            gattServiceRegistered = false;
        }
    }

    public void onDestroy() {
        activity.unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    boolean gattServiceRegistered = false;
}
