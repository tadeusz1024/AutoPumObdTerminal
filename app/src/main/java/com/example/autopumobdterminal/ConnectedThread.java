package com.example.autopumobdterminal;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.os.SystemClock.sleep;

public class ConnectedThread {//extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;

    public ConnectedThread(BluetoothSocket socket) {
        mmSocket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output streams, using temp objects because
        // member streams are final
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public byte[] run() {
        byte[] buffer = new byte[1024];  // buffer store for the stream
        int bytes; // bytes returned from read()
        // Keep listening to the InputStream until an exception occurs
        while (true) {
            try {
                // Read from the InputStream

                //  sleep(200); //dla komputera oki
                sleep(500); //dla samochodu
                bytes = mmInStream.available();
                if(bytes != 0) {
                    buffer = new byte[1024];
                    bytes = mmInStream.available(); // how many bytes are ready to be read?
                    bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                    return (buffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
        return buffer;
    }

    /* Call this from the main activity to send data to the remote device */
    public void write(String input) {
        byte[] bytes = input.getBytes();           //converts entered String into bytes
        try {
            mmOutStream.write(bytes);
            MainActivity.czyWyslanodoObd=true;
        } catch (IOException e) {
            MainActivity.czyWyslanodoObd=false;
            MainActivity.mConnectedThread.cancel();
            MainActivity.mConnectedThread=null;
            try {


                MainActivity.mBTSocket.close();
                MainActivity.mBTSocket=null;
                MainActivity.mBluetoothStatus.setText("Straciłem połączenie z "+MainActivity.nazwaOBD);
            } catch (IOException e1) {
                Log.e(MainActivity.TAG, "BT czyWyslanoObd- musze zamknac a nie moge");
            }
        }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) { }
    }
}
