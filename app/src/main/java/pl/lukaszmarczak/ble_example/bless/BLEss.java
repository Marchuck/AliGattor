package pl.lukaszmarczak.ble_example.bless;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by Łukasz Marczak
 *
 * @since 08.03.16
 */
public class BLEss {
    public static final String TAG = BLEss.class.getSimpleName();
    private BluetoothAdapter.LeScanCallback bleScanCallback;
    private boolean isScanning;

    public interface ErrorHandler {
        void onError(String errorMessage);
    }

    public interface BleCallback {
        void onBleReceived(BleDevice bleDevice);
    }

    public BLEss() {
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void onDestroy() {
        if (isScanning) {
            stopScan().subscribe(new Action1<Boolean>() {
                @Override
                public void call(Boolean aBoolean) {
                    Log.d(TAG, "scanner stopped");
                }
            }, onError(null));
        }
    }

    public static Action1<Throwable> onError(@Nullable final ErrorHandler errorHandler) {
        return new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (errorHandler != null) {
                    errorHandler.onError(throwable.getMessage());
                }
            }
        };
    }

    public static boolean isBLE(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public static boolean isBluetooth(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    }

    public static boolean isBtEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }

    /**
     * perform asynchronus call to close current scanner
     */
    public rx.Observable<Boolean> stopScan() {
        return rx.Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                btAdapter.stopLeScan(bleScanCallback);
                bleScanCallback = null;
                isScanning = false;
                subscriber.onNext(true);
            }
        });
    }

    /**
     * make sure bluetooth is enabled!
     *
     * @return
     */
    public rx.Observable<BleDevice> startScan() {
        return rx.Observable.create(new Observable.OnSubscribe<BleDevice>() {
            @Override
            public void call(final Subscriber<? super BleDevice> subscriber) {
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                if (isScanning) {
                    subscriber.onError(new Throwable("Scanning is running"));
                } else if (!btAdapter.isEnabled()) {
                    subscriber.onError(new Throwable("Bluetooth not enabled"));
                } else {
                    isScanning = true;
                    bleScanCallback = new BluetoothAdapter.LeScanCallback() {
                        @Override
                        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                            subscriber.onNext(new BleDevice(device, rssi, scanRecord));
                        }
                    };
                    btAdapter.startLeScan(bleScanCallback);
                }
            }
        });
    }


    public void scan(long timeInMillis, @Nullable final BleCallback bleCallback,
                     @Nullable final ErrorHandler errorHandler, @Nullable final Callable finishCallback) {

        final rx.Subscription subscription = startScan()
                .subscribe(new Subscriber<BleDevice>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        if (errorHandler != null)
                            errorHandler.onError(e.getMessage());
                    }

                    @Override
                    public void onNext(BleDevice bleDevice) {
                        if (bleCallback != null)
                            bleCallback.onBleReceived(bleDevice);
                    }
                });

        Observable.just(true).delay(timeInMillis, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .flatMap(new Func1<Boolean, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Boolean aBoolean) {
                        return stopScan();
                    }
                }).subscribe(new Action1<Boolean>() {
            @Override
            public void call(Boolean aBoolean) {
                if (finishCallback != null) {
                    finishCallback.call();
                }
                if (subscription != null)
                    subscription.unsubscribe();
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                if (subscription != null && !subscription.isUnsubscribed())
                    subscription.unsubscribe();
            }
        });
    }

    public interface Callable {
        void call();
    }

    public static Func1<BleDevice, Boolean> strongerThan(final int rssi) {
        return new Func1<BleDevice, Boolean>() {
            @Override
            public Boolean call(BleDevice bleDevice) {
                return bleDevice.rssi > rssi;
            }
        };
    }
}
