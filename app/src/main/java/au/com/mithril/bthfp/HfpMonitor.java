package au.com.mithril.bthfp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class HfpMonitor implements Runnable {
    BluetoothDevice bd;
    BluetoothSocket socket;
    BluetoothSocket scoSocket;

    MainActivity main;
    String bluetoothName;
    BufferedReader input;
    OutputStreamWriter output;

    private boolean isrunning = false;
    private boolean verbose = false;
    public int batteryLevel = -1;
    public int docked = -1;
    private Date handshakeConnected;

    synchronized public Date getHandshakeConnected() {
        return handshakeConnected;
    }

    synchronized public void setHandshakeConnected(Date handshakeConnected) {
        this.handshakeConnected = handshakeConnected;
    }


    public HfpMonitor(MainActivity main, BluetoothDevice device) {
        this.bd = device;
        this.main = main;
        if (bd != null) {
            if (main != null) bluetoothName = main.friendlyName(bd);
            else bluetoothName = bd.getName();
        }
    }

    synchronized public boolean isRunning() {
        return isrunning;
    }

    @Override
    public void run() {
        isrunning = true;
        try {
            handleConnection();
        } finally {
            closeSocket();
            isrunning = false;
        }
    }

    synchronized public boolean isConnected() {
        return (socket != null && socket.isConnected());
    }

    synchronized public boolean isAudioConnected() {
        return (scoSocket != null && scoSocket.isConnected());
    }

    synchronized public void closeSocket() {
        if (socket != null && socket.isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Just ignore.
            }
        }
        socket = null;
        if (scoSocket != null && scoSocket.isConnected()) {
            try {
                scoSocket.close();
            } catch (IOException e) {
                // Just ignore.
            }
        }
        scoSocket = null;
    }

    private void addln(Object msg) {
        if (main != null) {
            main.addln(msg);
        }
    }
    
    private void addVerbose(Object msg) {
        if (verbose) addln(msg);
    }

    private UUID findHfpService() {
        UUID service = null;
        if (bd == null) return service;
        for (ParcelUuid uuid : bd.getUuids()) {
            if (uuid.toString().toUpperCase().startsWith("0000111E")) {
                service = uuid.getUuid();
                break;
            }
        }
        return service;
    }

    private void handleConnection() {
        addln("Connecting to " + bluetoothName);
        UUID service = findHfpService();
        if (service == null) {
            addln("HFP not supported.");
            return;
        }
        try {
            socket = bd.createRfcommSocketToServiceRecord(service);
            socket.connect();
            addln("Connected.");
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            for (; ; ) {
                String s = input.readLine();
                if (s == null) break;
                s = s.trim();
                if (!s.isEmpty()) {
                    processCmd(s);
                }
            }
        } catch (IOException e) {
            addln(bluetoothName +"\nConnection failed. " + e.getMessage());
            return;
        }
    }

    void sendat(String cmd) throws IOException {
        output.write("\r\n" + cmd + "\r\n");
        output.flush();
        addVerbose("Response: " + cmd);
    }

    private void sendok() throws IOException {
        sendat("OK");
    }

    private String getArg(String s) {
        int i = s.indexOf("=");
        if (i < 0) return s;
        return s.substring(i + 1);
    }

    private int atol(String s) {
        int result = 0;
        try {
            result = Integer.parseInt(s, 10);
        } catch (NumberFormatException e) {
            // Ignore error.
        }
        return result;
    }

    private void processCmd(String s) throws IOException {
        String arg;
        int ret;
        addVerbose("Request: " + s);
        if (s.startsWith("AT+BRSF=")) {
            arg = getArg(s);
            ret = atol(arg);
            addln(bluetoothName+": Handshake started.");
            addVerbose("Options: " + parseBRSF(ret));
            sendat("+BRSF: 11");
            sendok();
        } else if (s.startsWith("AT+CIND=?")) {
            sendat("+CIND: (\"call\",(0,1)),(\"callsetup\",(0-3)),(\"service\",(0-1)),(\"signal\",(0-5)),(\"roam\",(0,1)),(\"battchg\",(0-5)),(\"callheld\",(0-2))");
            sendok();
        } else if (s.startsWith("AT+CIND?")) {
            sendat("+CIND: 0,0,0,4,0,1,0");
            sendok();
        } else if (s.startsWith("AT+CMER=")) {
            sendat("OK");
            addln(bluetoothName+": Handshake confirmed.");
            setHandshakeConnected(new Date());
//            startAudioConnection();
        } else if (s.startsWith("AT+CHLD=?")) {
            sendat("+CHLD: 0");
            sendok();
        } else if (s.startsWith("AT+VGS")) {
            ret = atol(getArg(s));
            sendok();
            addVerbose("Set vol=" + ret);
        } else if (s.startsWith("AT+BTRH?")) {
            sendat("+BTRH: 0");
            addVerbose("Query Bt Hold");
            sendok();
        } else if (s.startsWith("AT+CLIP")) {
            addVerbose("CallerID=" + getArg(s));
            sendok();
        } else if (s.startsWith("AT+CCWA")) {
            addVerbose("Call Waiting=" + getArg(s));
            sendok();
        } else if (s.startsWith("AT+NREC")) {
            addVerbose("Noise Red/Echo Can=" + getArg(s));
            sendok();
        } else if (s.startsWith("AT+VGM")) {
            addVerbose("Gain=" + getArg(s));
            sendok();
        } else if (s.startsWith("AT+XAPL")) {
            addVerbose("Apple Vendor=" + getArg(s));
            sendok();
        } else if (s.startsWith("AT+IPHONEACCEV")) {
            addVerbose("Apple Battery Level" + getArg(s));
            updateBattery(getArg(s));
            sendok();
        } else if (s.startsWith("AT+CMEE")) {
            addVerbose("Extended Gateway Codes=" + getArg(s));
            sendok();
        } else sendat("ERROR");
    }

    private void updateBattery(String arg) {
        Scanner scan = new Scanner(arg);
        scan.useDelimiter(",");
        int count=scan.nextInt();
        for (int i=0; i<count; i++) {
            int param = scan.nextInt();
            int value = scan.nextInt();
            if (param==1) batteryLevel=value;
            else if (param==2) docked=value;
        }
        addln(bluetoothName+": Battery="+batteryLevel);
    }

    private BluetoothSocket createScoSocket() {
        Method m = null;
        BluetoothSocket result = null;
        try {
            m = bd.getClass().getDeclaredMethod("createScoSocket");
            result=(BluetoothSocket) m.invoke(bd);
        } catch (NoSuchMethodException e) {
            m=null;
        } catch (IllegalAccessException e) {
            m=null;
        } catch (InvocationTargetException e) {
            m=null;
        }
        if (m==null) result=null;
        return result;
    }

    private void startAudioConnection() {
        addln("Audio connection should go here.");
/*
        scoSocket=createScoSocket();
        if (scoSocket==null) {
            addln("Unable to create SCO socket");
            return;
        }
        try {
            scoSocket.connect();
        } catch (IOException e) {
            addln("Unabled to sco connect: "+e.getMessage());
            return;
        }
        addln("Audio connection made. Listening...");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for(;;) {
                    byte[] b = new byte[8000];
                    try {
                        int ret=scoSocket.getInputStream().read(b);
                        addln("Audio stream recieved "+ret);
                        if (ret<0) break;
                    } catch (IOException e) {
                        break;
                    }
                }
            }
        });
        t.start(); */
    }

    private String parseBRSF(int ret) {
        String result = "";
        if ((ret & 0x01) != 0) result += "EC/NR ";
        if ((ret & 0x02) != 0) result += "3way ";
        if ((ret & 0x04) != 0) result += "CLI ";
        if ((ret & 0x08) != 0) result += "VR ";
        if ((ret & 0x10) != 0) result += "RemVol ";
        if ((ret & 0x20) != 0) result += "EnhCallStatus ";
        if ((ret & 0x40) != 0) result += "EnhCallCont ";
        if ((ret & 0x80) != 0) result += "CodecNeg ";
        if ((ret & 0x100) != 0) result += "HfInd ";
        if ((ret & 0x200) != 0) result += "S4 ";
        return result.trim();
    }
}
