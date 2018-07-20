package au.com.mithril.bthfp;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;

import java.util.HashSet;

class DevHolderList extends HashSet<DevHolder> {

    public DevHolder getDevice(BluetoothDevice bd) {
        if (bd==null) return null;
        for (DevHolder h : this) {
            if (h.address().equalsIgnoreCase(bd.getAddress())) return h;
        }
        return null;
    }

    public DevHolder getDevice(String address) {
        for (DevHolder h : this) {
            if (h.address().equalsIgnoreCase(address)) return h;
        }
        return null;
    }

    public boolean containsDevice(BluetoothDevice bd) {
        return getDevice(bd)!=null;
    }

    public boolean containsDevice(String address) {
        return getDevice(address)!=null;
    }

    public void updateConnected(BluetoothA2dp ad2p, BluetoothHeadset headset) {
        clearconnected();
        if (ad2p!=null) {
            for (BluetoothDevice d : ad2p.getConnectedDevices()) {
                DevHolder h = getDevice(d);
                if (h!=null) h.setA2DPConnected(true);
            }
        }
        if (headset!=null) {
            for (BluetoothDevice d : headset.getConnectedDevices()) {
                DevHolder h = getDevice(d);
                if (h!=null) h.setHeadsetConnected(true);
            }
        }

    }

    private void clearconnected() {
        for (DevHolder h : this) {
            h.setA2DPConnected(false);
            h.setHeadsetConnected(false);
        }
    }
}
