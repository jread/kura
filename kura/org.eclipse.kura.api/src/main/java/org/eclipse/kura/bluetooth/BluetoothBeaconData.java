package org.eclipse.kura.bluetooth;

public class BluetoothBeaconData {

    public String uuid;
    public String address;
    public int major, minor;
    public int rssi;
    public int txpower;

    @Override
    public String toString() {
        return "BluetoothBeaconData [uuid=" + this.uuid + ", address=" + this.address + ", major=" + this.major
                + ", minor=" + this.minor + ", rssi=" + this.rssi + ", txpower=" + this.txpower + "]";
    }
}
