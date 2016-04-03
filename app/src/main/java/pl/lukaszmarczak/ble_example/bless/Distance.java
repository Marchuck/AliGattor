package pl.lukaszmarczak.ble_example.bless;

import rx.functions.Func1;

/**
 * Created by Łukasz Marczak
 *
 * @since 08.03.16
 */
public enum Distance implements Func1<BleDevice, Boolean> {
    IMMEDIATE(-20),
    NEAR(-50),
    FAR(-80);

    int rssi;

    Distance(int _rssi) {
        this.rssi = _rssi;
    }

    @Override
    public Boolean call(BleDevice bleDevice) {
        return bleDevice.rssi > rssi;
    }
}
