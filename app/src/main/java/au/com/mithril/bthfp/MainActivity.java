package au.com.mithril.bthfp;

import android.app.AlertDialog;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.Inflater;

public class MainActivity extends AppCompatActivity {

    TextView mMemo1;
    Handler mainHandler;
    static StringBuilder mMemo1Holder = new StringBuilder();
    static Set<BluetoothDevice> mDiscovered = new HashSet<>();
    static DevHolderList mDevices = new DevHolderList();
    private BluetoothA2dp mA2DP;
    private BluetoothHeadset mHeadset;
    Timer mTimer=null;
    ListView mListDevices;

    public ArrayAdapter<DevHolder> mDeviceList = null;

    public static final String ACTION_ACTIVE_DEVICE_CHANGED =
            "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED";

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice bd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            DevHolder h = mDevices.getDevice(bd);
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                mDiscovered.add(bd);
                mDevices.updateConnected(mA2DP, mHeadset);
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                showfound();
                mDevices.updateConnected(mA2DP, mHeadset);
            } else if (action.equals(ACTION_ACTIVE_DEVICE_CHANGED)) {
                addln("Audio Device changed: " + friendlyName(bd));
                mDevices.updateConnected(mA2DP, mHeadset);
            } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                addln("ACL Connected: " + friendlyName(bd));
                if (h != null) h.lastSeen = new Date();
                mDevices.updateConnected(mA2DP, mHeadset);
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                addln("ACL Disconnected: " + friendlyName(bd));
                if (h != null) h.lastSeen = new Date();
                mDevices.updateConnected(mA2DP, mHeadset);
            }
            updateDeviceList();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(getMainLooper());
        setContentView(R.layout.activity_main);
        mMemo1 = findViewById(R.id.memo1);
        mMemo1.setMovementMethod(new ScrollingMovementMethod());
        mMemo1.setText(mMemo1Holder.toString());

        mListDevices = findViewById(R.id.device_list);
        mDeviceList = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);
        mListDevices.setAdapter(mDeviceList);

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(ACTION_ACTIVE_DEVICE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        registerReceiver(mReceiver, filter);
        mBluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.A2DP);
        mBluetoothAdapter.getProfileProxy(this, mProfileListener, BluetoothProfile.HEADSET);
        mTimer=new Timer(true);
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mDevices.updateConnected(mA2DP,mHeadset);
                        updateDevices();
                    }
                });
            }
        }, 5000, 5000);
        updateDevices();
    }

    @Override
    protected void onDestroy() {
        mTimer.cancel();
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void updateDevices() {
        for (BluetoothDevice bd : mBluetoothAdapter.getBondedDevices()) {
            if (bd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                DevHolder h = mDevices.getDevice(bd);
                if (h == null) {
                    h = new DevHolder(friendlyName(bd), bd);
                    mDevices.add(h);
                }
            }
        }
        updateDeviceList();
    }

    private void updateDeviceList() {
        mDeviceList.setNotifyOnChange(false);
        mDeviceList.clear();
        mDeviceList.addAll(mDevices);
        mDeviceList.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuTest) {
            info();
        } else if (id == R.id.menuDiscover) {
            addln("Starting discovery");
            mDiscovered.clear();
            mBluetoothAdapter.startDiscovery();
        } else if (id == R.id.menuHfpConnect) {
            selectDevice();
        } else if (id == R.id.menu_disconnect) {
            disconnectAll();
        } else if (id == R.id.menu_showconnect) {
            showConnections();
        } else if (id == R.id.menu_setActive) {
            setActiveDevice();

        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void setActiveDevice() {
        askDevice("Set Active", new DeviceOK() {
            @Override
            public void useDevice(DevHolder bdholder) {
                setActiveAudio(bdholder.file);
            }
        });

    }

    private void setActiveAudio(BluetoothDevice bd) {
        if (mA2DP == null) return;
        Method setactive = null;
        try {
            setactive = mA2DP.getClass().getDeclaredMethod("setActiveDevice", BluetoothDevice.class);
        } catch (NoSuchMethodException e) {
            addln("setActiveDevice not found.");
        }
        if (setactive == null) {
            try {
                setactive = mA2DP.getClass().getDeclaredMethod("connect", BluetoothDevice.class);
            } catch (NoSuchMethodException e1) {
                addln("Connect not found.");
            }
        }
        try {
            setactive.invoke(mA2DP, bd);
            addln("SetActive done. " + friendlyName(bd));
        } catch (IllegalAccessException e) {
            addln("setActiveDevice: illegal access " + e.getMessage());
        } catch (InvocationTargetException e) {
            addln("setActiveDevice: target exception " + e.getMessage());
        }
    }

    private void showConnections() {
        setln("Connected:");
        for (DevHolder h : mDevices) {
            if (h.isConnected()) {
                addln(h.toString());
            }
        }
    }

    private void disconnectAll() {
        for (DevHolder h : mDevices) {
            if (h.hfp != null) h.hfp.closeSocket();
        }
    }

    String getAlias(BluetoothDevice bd) {
        Method m = null;
        String result = null;
        try {
            m = bd.getClass().getDeclaredMethod("getAlias"); // Name may be different in different versions.
        } catch (NoSuchMethodException e) {
            m = null;
        }
        if (m == null) {
            try {
                m = bd.getClass().getDeclaredMethod("getAliasName");
            } catch (NoSuchMethodException e) {
                m = null;
            }
        }
        if (m != null) {
            try {
                result = (String) m.invoke(bd);
            } catch (IllegalAccessException e) {
                result = null;
            } catch (InvocationTargetException e) {
                result = null;
            }
        }
        return result;
    }

    String friendlyName(BluetoothDevice bd) {
        if (bd == null) return "";
        String result = getAlias(bd);
        if (result != null) return result;
        return bd.getName();
    }

    private void info() {
        StringBuilder b = new StringBuilder();
        b.append("Bluetooth");
        if (!mBluetoothAdapter.isEnabled()) {
            b.append("\nDisabled.");
        } else {
            b.append("Paired Devices");
            Set<BluetoothDevice> paired = mBluetoothAdapter.getBondedDevices();
            addbtlist(paired, b);
        }
        setln(b);
    }

    public void showfound() {
        StringBuilder b = new StringBuilder("Nearby Devices");
        addbtlist(mDiscovered, b);
        setln(b);
    }

    private void addbtlist(Set<BluetoothDevice> list, StringBuilder b) {
        for (BluetoothDevice bd : list) {
            if (bd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                b.append("\n" + friendlyName(bd) + " " + bd.getAddress());
            }
        }
    }

    public void addln(Object msg) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) { // Not in main loop.
            final String holdmsg = msg.toString();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    addln(holdmsg);
                }
            });
            return;
        }
        if (mMemo1Holder.length() > 6000) mMemo1Holder.delete(0, mMemo1Holder.length() - 5000);
        mMemo1Holder.append(msg + "\n");
        mMemo1.setText(mMemo1Holder.toString().trim());
        mMemo1.append("\n");
    }

    public void setln(Object msg) {
        mMemo1Holder.delete(0, mMemo1Holder.length());
        mMemo1.setText(msg.toString());
        mMemo1.scrollTo(0, 0);
    }

    void selectDevice() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Destination");
        builder.setMessage("Select Device");
// Set up the input
        final Spinner input = new Spinner(this);
        setDeviceList(input);
        builder.setView(input);

// Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                doHfp((DevHolder) input.getSelectedItem());
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    void askDevice(String caption, final DeviceOK doOk) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(caption);
        builder.setMessage("Select Device");
// Set up the input
        final Spinner input = new Spinner(this);
        setDeviceList(input);
        builder.setView(input);

// Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DevHolder h = (DevHolder) input.getSelectedItem();
                doOk.useDevice(h);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }


    private void doHfp(DevHolder dev) {
        addln("Connecting HFP to " + dev);
        if (dev.hfp != null) {
            dev.hfp.closeSocket();
            dev.hfp = null;
        }
        dev.hfp = new HfpMonitor(this, dev.file);
        (new Thread(dev.hfp)).start();
    }

    public void setDeviceList(Spinner lv) {
        if (lv == null) return;
        ArrayAdapter<DevHolder> la = new ArrayAdapter<DevHolder>(this, android.R.layout.simple_spinner_item);
        la.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        la.addAll(mDevices);
        lv.setAdapter(la);
    }

    private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.A2DP) {
                mA2DP = (BluetoothA2dp) proxy;
                addln("Bluetooth A2DP found: " + proxy);
                mDevices.updateConnected(mA2DP, mHeadset);
            } else if (profile == BluetoothProfile.HEADSET) {
                addln("Bluetooth Headset found.");
                mHeadset = (BluetoothHeadset) proxy;
                mDevices.updateConnected(mA2DP, mHeadset);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.A2DP) {
                mA2DP = null;
            } else if (profile == BluetoothProfile.HEADSET) {
                mHeadset = null;
            }
            mDevices.updateConnected(mA2DP, mHeadset);
        }
    };

    class MyDeviceAdapter extends ArrayAdapter<DevHolder> {

        private LayoutInflater inflater;
        private int layout;

        public MyDeviceAdapter(@NonNull Context context, int resource) {
            super(context, resource);
            inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            layout = resource;
        }

        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(layout,parent);
            }
            DevHolder h = getItem(position);
            TextView caption = convertView.findViewById(R.id.device_list_text);
            Switch connected = convertView.findViewById(R.id.device_list_connected);
            caption.setText(h.toString());
            connected.setChecked(h.isConnected());
            return convertView;
        }
    }

}
