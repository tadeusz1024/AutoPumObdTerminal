package com.example.autopumobdterminal;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import static android.os.SystemClock.*;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity {

    // GUI Components
    public static TextView mBluetoothStatus;
    private TextView mReadBuffer;
    private Button mListPairedDevicesBtn;
    private Button mDiscoverBtn;
    public static boolean czyWyslanodoObd=false;

    private Button mWriteBtn;
    private Button mClearBtn;
    private EditText mReadOBD;
    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    public final static String TAG = MainActivity.class.getSimpleName();
    private Handler mHandler; // Our main handler that will receive callback notifications
    public static ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    public static BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path
    private Switch mSwichBT;
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier
    private BluetoothDevice activeDevice;

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    public static String nazwaOBD=null;
    private SharedPreferences mSharedPreferences;

    @Override
    protected void onStop(){
        mSharedPreferences = getSharedPreferences("nazwaObd", 0);
        SharedPreferences.Editor edytor = mSharedPreferences.edit();
        edytor.putString("nazwaObd", nazwaOBD);
        edytor.commit();
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nazwaOBD = getSharedPreferences("nazwaObd", 0).getString("nazwaObd",null);
        mBluetoothStatus = (TextView)findViewById(R.id.bluetoothStatus);
        mReadBuffer = (TextView) findViewById(R.id.readBuffer);
        mDiscoverBtn = (Button)findViewById(R.id.discover);
        mReadOBD=(EditText)findViewById(R.id.readOBD);
        mClearBtn = (Button)findViewById(R.id.clear);
        mWriteBtn = (Button)findViewById(R.id.write);
        mSwichBT= (Switch)findViewById(R.id.switchBT);
        mListPairedDevicesBtn = (Button)findViewById(R.id.PairedBtn);
        mBTArrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio
        mDevicesListView = (ListView)findViewById(R.id.devicesListView);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);
        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "ascii");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    mReadBuffer.setText(readMessage);
                }
                if(msg.what == CONNECTING_STATUS){
                    //nazwaOBD=(String)msg.obj;
                    if(msg.arg1 == 1){
                        nazwaOBD=(String)msg.obj;
                        mBluetoothStatus.setText("Połączenie z: " + nazwaOBD);
                    }
                    else
                        mBluetoothStatus.setText("Brak usługi-połącz się");
                }
            }
        };
        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText("Status: Bluetooth nie znaleziony");
            Toast.makeText(getApplicationContext(),"Nie ma urządzenia Bluetooth!",Toast.LENGTH_SHORT).show();
        }
        else {
            if (mBTAdapter.isEnabled()) {
                mBluetoothStatus.setText("Bluetooth włączony");
                mSwichBT.setText("Bluetooth ON  ");
                mSwichBT.setChecked(true);
            }
            else{
                mBluetoothStatus.setText("Bluetooth wyłączony");
                mSwichBT.setText("Bluetooth OFF ");
                mSwichBT.setChecked(false);
            }
            mSwichBT.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mSwichBT.getText()=="Bluetooth ON  ")
                        bluetoothOff(v);
                    else
                        bluetoothOn(v);
                }
            });

            mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    listPairedDevices(v);

                }
            });

            mDiscoverBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    discover(v);
                }
            });
            mClearBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String znak="";
                    mReadBuffer.setText(znak);
                    mReadOBD.setText("");
                }
            });
            mWriteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "BT adapter: " +  mBTAdapter+"  BT adapter socket: "+ mBTSocket+"  BT adapter socket thread: "+mConnectedThread+"  Nazwa OBD: "+nazwaOBD+ "  przed");
                    if (mBTSocket==null&&(mBTAdapter.isEnabled())&&nazwaOBD!=null){//(mConnectedThread==null){
                        findBluetoothDevice(mBTAdapter,nazwaOBD);
                        //"LAPTOP-I86FB2IG"
                    }
                    if((mConnectedThread==null&&(mBTAdapter.isEnabled())&&mBTSocket==null)&&nazwaOBD!=null){
                        // Get the device MAC address, which is the last 17 chars in the View
                        //String info = ((TextView) v).getText().toString();
                        // boolean fail = false;
                        if (activeDevice!=null){
                            mBluetoothStatus.setText("Łączę...");
                            final String address = activeDevice.getAddress();
                            final String name = activeDevice.getName();
                            podlaczGniazdo(address,name);
                            sleep(2000); //moze byc potrzebne 3000
                        }
                        else {
                            mBluetoothStatus.setText("Brak usługi-połącz się z " +nazwaOBD);
                        }

                    }
                    if (mConnectedThread!=null){
                        ConnectedThread c = new ConnectedThread(mBTSocket);
                        mReadOBD=(EditText)findViewById(R.id.readOBD);
                        String wyslij = (mReadOBD.getText().toString()+"\r\n").toUpperCase();
                        c.write(wyslij);
                        if(czyWyslanodoObd){
                            final byte[] mBuffer = c.run();
                            String znak = "";
                            for (int i =0 ; mBuffer[i]!=0;i++){
                                if (!((mBuffer[i]==0b1101)||(mBuffer[i]==0b1010)||
                                        (mBuffer[i]==0b111110)||mBuffer[i]==0b100000)){
                                    znak=znak+ ((char)(mBuffer[i]));
                                }
                            }
                            String wynik="";
                            boolean ciag_OK=true;
                            String blad="";
                            if(((znak.charAt(znak.length()-1))== 0b00111111)&&ciag_OK){
                                ciag_OK=false;
                                blad="Podano nie znane polecenie OBD";
                            }
                            if((wyslij.length()==5)&&(wyslij.charAt(0)==0b1000001)&&
                                    (wyslij.charAt(1)==0b1010100)&&
                                    (wyslij.charAt(2)==0b1011010)&&ciag_OK){
                                ciag_OK=false;
                                wynik=znak.substring(3,znak.length());
                                blad="Wersja OBD: "+wynik;
                            }
                            if ((znak.length()>8)&&ciag_OK){
                                wynik= znak.substring(8,znak.length());
                            }
                            else {
                                if (ciag_OK){
                                    wynik=znak;
                                    ciag_OK=false;
                                    blad="Otrzymana odpowiedz ma mniej niz 8 znakow";
                                }
                            }
                            if (znak.charAt(znak.length()-1)==(byte)(0b1000001)&&
                                    znak.charAt(znak.length()-2)==(byte)(0b1010100)&&
                                    znak.charAt(znak.length()-3)==(byte)(0b1000001)&&
                                    znak.charAt(znak.length()-4)==(byte)(0b1000100)&&
                                    znak.charAt(znak.length()-5)==(byte)(0b1001111)&&
                                    znak.charAt(znak.length()-6)==(byte)(0b1001110)&&ciag_OK)
                            {
                                wynik="NODATA";
                                ciag_OK=false;
                                blad="OBD nie ma takich danych";
                            }
                            if (ciag_OK){
                                for (int i=0; i<4 ;i++){
                                    if (!(znak.charAt(i)==wyslij.charAt(i))){
                                        ciag_OK=false;
                                        blad= "Odpowiedż na inne polecenie";
                                        break;
                                    }
                                    if ((i==0)&& (znak.charAt(i+4)!=((byte)(wyslij.charAt(i))+4))){
                                        ciag_OK=false;
                                        blad="W odpowiedzi na polecenie 1 znak nie jest większy o 4";
                                        break;
                                    }
                                    if ((i!=0)&& (znak.charAt(i+4)!=wyslij.charAt(i))){
                                        ciag_OK=false;
                                        blad="Różnią się znaki odpowiedzi pola:"+getString(i)+"i"+getString(i+4);
                                        break;
                                    }
                                }
                            }
                            long waroscZwracana=0;
                            if (ciag_OK){
                                for (int i=0;i<wynik.length();i++){
                                    if ((wynik.charAt(i)<0b111010)&&(wynik.charAt(i)>0b101111)){
                                        waroscZwracana=waroscZwracana*16+ (int)(wynik.charAt(i)-0b110000);
                                    }
                                    else {
                                        if ((wynik.charAt(i)<0b1000111)&&(wynik.charAt(i)>0b1000000)){

                                            waroscZwracana=waroscZwracana*16+ (int)(wynik.charAt(i)-0b110111);
                                        }
                                        else {
                                            blad="Zwrocone dane to nie liczba heksadecymalna";
                                            ciag_OK=false;
                                        }
                                    }
                                }
                            }
                            if (ciag_OK){
                                mReadBuffer.setText(znak+"\r\n"+wynik+"\r\n"+waroscZwracana);
                            }
                            else {
                                mReadBuffer.setText(znak+"\r\n"+wynik+"\r\n"+blad);
                            }
                        }
                    }
                    else {
                        mReadBuffer.setText("Połącz się z OBD");
                    }
                }
            });
        }
    }

    void findBluetoothDevice(BluetoothAdapter myBluetoothAdapter,
                             String filter) {
        Log.d(TAG, "(*) Initialising Bluetooth connection for device: " + filter);
        filter=filter.substring(0,filter.length()-1);
        boolean jest=false;
        if(myBluetoothAdapter.isEnabled()) {
            for (BluetoothDevice pairedDevice : myBluetoothAdapter.getBondedDevices()) {
                Log.d(TAG, "* Initialising Bluetooth connection :"+pairedDevice.getName()+":"+pairedDevice.getName().length()+":czy rowne:"
                        +filter+":"+filter.length()+":"+(pairedDevice.getName()==filter));
                if (pairedDevice.getName().contains(filter /*Like MI*/)) {
                    Log.d(TAG, "\tDevice Name: " +  pairedDevice.getName());
                    Log.d(TAG, "\tDevice MAC: " + pairedDevice.getAddress());

                    activeDevice =  pairedDevice;
                    jest=true;
                    break;
                }
            }
            Log.d(TAG, "\tDidnt find any device!");
        }
    }

    private void bluetoothOn(View view){
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText("Bluetooth włączony");
            Toast.makeText(getApplicationContext(),"Bluetooth włączony",Toast.LENGTH_SHORT).show();
            mSwichBT.setText("Bluetooth ON  ");
            mSwichBT.setChecked(true);
        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth jest już włączony", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText("Włączony");
                mSwichBT.setText("Bluetooth ON  ");
                mSwichBT.setChecked(true);
            }
            else {
                mBluetoothStatus.setText("Wyłączony");
                mSwichBT.setText("Bluetooth OFF ");
                mSwichBT.setChecked(false);
            }
        }
    }

    private void bluetoothOff(View view){
        mBTAdapter.disable(); // turn off
        mBluetoothStatus.setText("Bluetooth wyłączony");
        Toast.makeText(getApplicationContext(),"Bluetooth jest wyłączony", Toast.LENGTH_SHORT).show();
        mSwichBT.setText("Bluetooth OFF ");
        mSwichBT.setChecked(false);
    }

    private void discover(View view){
        // Check if the device is already discovering
        if(mBTAdapter.isDiscovering()){
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),"Szukanie zatrzymane",Toast.LENGTH_SHORT).show();
        }
        else{
            if(mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Wyszukuję urządzeń", Toast.LENGTH_SHORT).show();
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            }
            else{
                Toast.makeText(getApplicationContext(), "Bluetooth nie jest włączony", Toast.LENGTH_SHORT).show();
            }
        }
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void listPairedDevices(View view){
        mPairedDevices = mBTAdapter.getBondedDevices();
        if(mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "Sparowane urządzenia", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth nie jest włączony", Toast.LENGTH_SHORT).show();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            if(!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth nie jest właczony", Toast.LENGTH_SHORT).show();
                return;
            }
            mBluetoothStatus.setText("Łączę...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0,info.length() - 17);
            Log.e(TAG, "BT Socket nazwa OBD: "+name);
            podlaczGniazdo(address,name);
        }
    };

    private void podlaczGniazdo(final String address,final String name){
        // Spawn a new thread to avoid blocking the GUI one
        new Thread()
        {
            public void run() {
                boolean fail = false;
                BluetoothDevice device = mBTAdapter.getRemoteDevice(address);
                try {
                    mBTSocket = createBluetoothSocket(device);
                    Log.e(TAG, "BT Socket OK");
                } catch (IOException e) {
                    fail = true;
                    Toast.makeText(getBaseContext(), "Tworzenie Socket-u zakończone błędem", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "BT Socket ERROR");
                }
                // Establish the Bluetooth socket connection.
                try {
                    mBTSocket.connect();
                    Log.e(TAG, "BT Socket OK Connect za 1 razem");
                } catch (IOException e) {
                    try {
                        try{
                            mBTSocket = device.createRfcommSocketToServiceRecord(BTMODULEUUID);
                            mBTSocket.connect();
                            Log.e(TAG, "BT Socket OK Connect za 2 razem");
                        }
                        catch (IOException e1){
                            fail = true;
                            mBTSocket.close();
                            mBTSocket=null;
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                            Log.e(TAG, "BT Socket CLOSE",e);
                        }
                    } catch (IOException e2) {
                        //insert code to deal with this
                        Toast.makeText(getBaseContext(), "Tworzenie Socket-u zakończone błędem", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "BT Socket ERROR Connect");
                    }
                }
                if(fail == false) {
                    mConnectedThread = new ConnectedThread(mBTSocket);
                    // mConnectedThread.start();
                    mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget();
                }
            }
        }.start();
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            Log.e(TAG, "BT Socket OK z createBluetoothSosket"+device.getName()+device.getAddress());
            return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }


}

