package pl.lukaszmarczak.ble_example.bless;

import android.bluetooth.BluetoothDevice;

import java.util.Arrays;

/**
 * Created by Łukasz Marczak
 *
 * @since 08.03.16
 */
public class BleDevice {
    public BluetoothDevice bluetoothDevice;
    public int rssi;
    public byte[] scanRecord;

    public BleDevice(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
        this.bluetoothDevice = bluetoothDevice;
        this.rssi = rssi;
        this.scanRecord = scanRecord;
    }

    public BleDevice() {
    }

    @Override
    public String toString() {
        return bluetoothDevice == null ? "null" : ("name: " + bluetoothDevice.getName()
                + "addr: " + bluetoothDevice.getAddress() + ", rssi = " + rssi);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BleDevice bleDevice = (BleDevice) o;

        if (rssi != bleDevice.rssi) return false;
        if (!bluetoothDevice.equals(bleDevice.bluetoothDevice)) return false;
        return Arrays.equals(scanRecord, bleDevice.scanRecord);

    }

    @Override
    public int hashCode() {
        int result = bluetoothDevice.hashCode();
        result = 31 * result + rssi;
        result = 31 * result + (scanRecord != null ? Arrays.hashCode(scanRecord) : 0);
        return result;
    }
}
