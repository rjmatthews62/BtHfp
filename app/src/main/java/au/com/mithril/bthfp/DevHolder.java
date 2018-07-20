package au.com.mithril.bthfp;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;

import java.util.Date;

public class DevHolder implements Comparable<DevHolder> {
    public String name;
    public BluetoothDevice file;
    public Date lastSeen;
    private boolean mConnected;

    public HfpMonitor hfp;

    private boolean a2DPConnected;
    private boolean headsetConnected;


    public DevHolder(String name, BluetoothDevice file) {
        this.name = name;
        this.file = file;
    }

    static long seconds(Date d) {
        long diff=(new Date()).getTime()-d.getTime();
        return diff/1000L;
    }

    public String address() {
        if (file==null) return "";
        return file.getAddress();
    }

    @Override
    public String toString() {
        String result;
        if (name != null && !name.isEmpty()) result = name;
        else if (file != null) result = file.getName();
        else result = "(null)";
        if (lastSeen!=null) {
            result+=" ("+seconds(lastSeen)+")";
        }
        if (isConnected()) result+="*";
        if (isA2DPConnected()) result+="A";
        if (isHeadsetConnected()) result+="H";
        int level=getBatteryLevel();
        if (level>=0) result+="B"+level;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof  DevHolder) {
            DevHolder d = (DevHolder) obj;
            if (this.file==null || d.file==null ) return false;
            return this.file.getAddress().equals(d.file.getAddress());
        }
        return super.equals(obj);
    }

    @Override
    public int compareTo(@NonNull DevHolder devHolder) {
        return toString().compareTo(devHolder.toString());
    }

    public void setA2DPConnected(boolean isconnected) {
        this.a2DPConnected = isconnected;
        if (isconnected) lastSeen=new Date();
    }

    public void setHeadsetConnected(boolean isconnected) {
        this.headsetConnected = isconnected;
        if (isconnected) lastSeen=new Date();
    }

    public boolean isA2DPConnected() {
        if (a2DPConnected) lastSeen=new Date();
        return a2DPConnected;
    }

    public boolean isHeadsetConnected() {
        if (headsetConnected) lastSeen=new Date();
        return headsetConnected;
    }


    public int getBatteryLevel() {
        int result=-1;
        if (hfp!=null) result=hfp.batteryLevel;
        return result;
    }

    public boolean isConnected() {
        if (hfp!=null && hfp.isConnected()) return true;
        if (isA2DPConnected() || isHeadsetConnected()) return true;
        return false;
    }
}

