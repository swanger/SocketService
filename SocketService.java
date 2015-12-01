/*
 * Step 1: Add Service in AndroidManifest.xml
 * Step 2: bind in Activity
 * Step 3: Realize ServiceConnection interface
 */
package com.example.fxsl.socket;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;

import static com.example.fxsl.socket.MessageReceiver.RecMsgHandler;

public class SocketService extends Service implements RecMsgHandler {

    private static final String TAG = "SocketService";
    private MyBinder mBinder = new MyBinder();
    public String fxsl_addr = null;
    public int fxsl_port = 0;

    private MessageReceiver msgReceiver = null;
    IntentFilter intentFilter = new IntentFilter();

    Socket clientSocket = null;
    InputStream inStream = null;
    OutputStream outStream = null;

    byte[] rec_buf = new byte[100];
    private boolean send_over_flag = true;
    private boolean receive_begin_flag = false;
    private boolean watchdog_begin_flag = false;
    private boolean register_receive_flag = false;
    private int watchdog_value = 0;
    //private static Mutex lock;


    private Intent intent = new Intent("com.example.fxsl.socket.MsgReceiver");

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate() executed");
        register_receive_flag = false;
        //Log.e(TAG, "MyService thread onCreate " + Thread.currentThread().getId());
        if(isWifiConnected() == true){

            msgReceiver = new MessageReceiver();

            intentFilter.addAction("com.example.fxsl.socket.MsgReceiver");
            if(register_receive_flag == false) {
                registerReceiver(msgReceiver, intentFilter);
                register_receive_flag = true;
            }

            MessageReceiver.RecMsgList.add(this);

            receive_begin_flag = true;
        } else {
            Log.e(TAG,"WIFI not connected");
        }

    }

    private byte rec_index = 2;
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        //Log.e(TAG, "MyService thread onStart " + Thread.currentThread().getId());

        try {
            if (send_over_flag) {
                rec_buf[rec_index] = (byte) (intent.getShortExtra("key", (short) 0x00) | 0x80);
                rec_index++;
                rec_buf[rec_index] = (byte) intent.getShortExtra("key", (short) 0x00);
                rec_index++;
            } else {
                Log.e(TAG, "Wait for key sended over");
            }
        } catch (NullPointerException e) {
            Log.e(TAG,"I am in Null pointException");

        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() executed");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //unregisterReceiver(msgReceiver);

        Log.d(TAG, "onDestroy() executed");
        try {
            mBinder.t_connect.join();
            watchdog.join();
            watchdog_begin_flag = false;
            receive_begin_flag = false;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            outStream.close();
            inStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Log.e(TAG,"io null");
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind() executed");
        watchdog_begin_flag = true;
        watchdog.start();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnBind() executed");
        return true;
    }



    @Override
    public void GetSocketMessage(String msg) throws UnsupportedEncodingException {

        byte[] byBuffer;
        byBuffer=msg.getBytes("UTF-16BE");
        //Log.e(TAG, "MyService thread GetSocketMessage " + Thread.currentThread().getId());

        if(rec_index >= 2 && rec_index<= 14) {

            rec_buf[0] = byBuffer[0];
            rec_buf[1] = 0x00;
            mBinder.fxsl_Send2AP(rec_buf,rec_index);


            rec_index = 2;
            send_over_flag = true;
            //reset watchdog_timer

            watchdog_value = 0;

        } else if(rec_index >14) {

            int i;
            send_over_flag = false;

            rec_buf[0] = byBuffer[0];
            rec_buf[1] = 0x00;
            mBinder.fxsl_Send2AP(rec_buf,14);

            rec_index = (byte) (rec_index - 14 +2);
            for(i=0;i<rec_index-2;i++) {
                rec_buf[i+2] = rec_buf[i+14];
            }

        }
        else {

            Log.e(TAG,"Wrong index");

        }

        Intent intent2 = new Intent("com.example.fxsl.socket.MsgReceiver2");
        intent2.putExtra("datafromwcs", msg);
        sendBroadcast(intent2);
    }

    @Override
    public void UpdateUI(String msg) throws UnsupportedEncodingException {

        //Log.e(TAG,"service UpdateUI");

    }

    /**
     * check wifi is connected or not
     *
     * @return
     */
    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null
                    && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                return true;
            }
        }
        return false;
    }

    //=============================================================
    // new a thread for watchdog
    //=============================================================
    private Thread watchdog = new Thread(new Runnable() {
        @Override
        public void run() {
            while(watchdog_begin_flag) {
                watchdog_value++;
                try {
                    watchdog.sleep(1000);
                    //Log.e(TAG,watchdog_value+"");
                } catch (InterruptedException e) {
                    if(register_receive_flag == true) {
                        unregisterReceiver(msgReceiver);
                        register_receive_flag = false;
                    }

                    e.printStackTrace();
                }

                if(watchdog_value > 30) {
                    Log.e(TAG,"watchdog timerout");

                    try {
                        if( isWifiConnected() == true ) {
                            if(clientSocket == null) {
                                onCreate();
                                receive_begin_flag = true;

                                if(mBinder.t_connect.isAlive()) {

                                    clientSocket = new Socket(fxsl_addr, fxsl_port);
                                    inStream.close();
                                    outStream.close();
                                    inStream = clientSocket.getInputStream();
                                    outStream = clientSocket.getOutputStream();


                                } else {
                                    mBinder.fxsl_SocketConnect("192.168.8.1", 25000);
                                    //mBinder.fxsl_SocketConnect("192.168.173.1", 25000);
                                }

                                watchdog_value = 0;
                                rec_index = 2;
                                watchdog_begin_flag = true;

                                send_over_flag = true;
                                Log.e(TAG,"first socket connection");
                            } else {
                                clientSocket.sendUrgentData(0x06);
                                watchdog_value = 0;
                            }

                        } else {
                            Log.e(TAG,"WIFI or socket is not connected in sending urgentdata");
                        }

                    } catch (NullPointerException e) {
                        Log.e(TAG,"catch a NULLException in sending urgentdata");
                        onDestroy();

                    } catch (IOException e) {

                        Log.e(TAG,"urgentData fail, socket disconnect");
                        watchdog_value = 31;
                        if(register_receive_flag == true) {
                            unregisterReceiver(msgReceiver);
                            register_receive_flag = false;
                        }

                        e.printStackTrace();

                        try {
                            Log.e(TAG,"Try to close socket");

                            if(clientSocket != null) {
                                clientSocket.close();
                            }
                        } catch (IOException e1) {
                            Log.e(TAG,"urgentData close fail");
                            e1.printStackTrace();
                        }

                        try {
                            Log.e(TAG,"Try to reconnect");

                            if(isWifiConnected() == true){



//                                mBinder.t_connect.stop();
//                                mBinder.fxsl_SocketConnect("192.168.8.1", 25000);

                                if(register_receive_flag == false) {
                                    registerReceiver(msgReceiver, intentFilter);
                                    register_receive_flag = true;
                                }

                                clientSocket = new Socket(fxsl_addr, fxsl_port);
                                inStream.close();
                                outStream.close();
                                inStream = clientSocket.getInputStream();
                                outStream = clientSocket.getOutputStream();
                                watchdog_value = 0;
                                rec_index = 2;

                                watchdog_begin_flag = true;
                                receive_begin_flag = true;
                            } else {
                                Log.e(TAG,"WIFI not connected");
                                //wait to connect wifi
                            }

                        } catch (IOException e2) {
                            Log.e(TAG,"reconnect fail");
                            watchdog_value = 31;

                            if(register_receive_flag == true) {
                                unregisterReceiver(msgReceiver);
                                register_receive_flag = false;
                            }

                            e2.printStackTrace();
                        }
                    }

                }
            }
            //System.out.println("threadID_2 " + Thread.currentThread().getId());
        }

    });


    public class MyBinder extends Binder {

        //=============================================================
        // new a thread to connect a socket
        //=============================================================
        public Thread t_connect = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(fxsl_addr !=null && !fxsl_addr.isEmpty() && fxsl_port != 0) {
                        clientSocket = new Socket(fxsl_addr, fxsl_port);
                    } else
                        Log.e(TAG,"without connection");
                } catch (Exception e) {
                    Log.e(TAG,"connect fail");
                    e.printStackTrace();
                }

                if(clientSocket == null) {

                    return;
                }

                try {
                    inStream = clientSocket.getInputStream();
                } catch (IOException e2) {
                    Log.e(TAG,"detected1...");
                    if(register_receive_flag == true) {
                        unregisterReceiver(msgReceiver);
                        register_receive_flag = false;
                    }

                    e2.printStackTrace();
                }





                while (receive_begin_flag) {
                    byte[] buf = new byte[512];
                    String str = null;
                    // listening here

                   /* try {
                        clientSocket.sendUrgentData(0x06);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/

                    try {
                        inStream.read(buf);
                    } catch (IOException e1) {
                        Log.e(TAG,"detected... server side disconnected");
                        watchdog_value = 0;
                        if(register_receive_flag == true) {
                            unregisterReceiver(msgReceiver);
                            register_receive_flag = false;
                        }

                        e1.printStackTrace();
                    } catch (IndexOutOfBoundsException e) {
                        if(register_receive_flag == true) {
                            unregisterReceiver(msgReceiver);
                            register_receive_flag = false;
                        }
                        Log.e(TAG,"IndexOutOfBoundsException");
                    }

                    try {
//                        str = new String(buf, "UTF-16BE").trim();
                          str = new String(buf, "UTF-16BE");
                          Message msg = new Message();
                          msg.obj = str;
                          mHandler.sendMessage(msg);

                    } catch (UnsupportedEncodingException e) {
                        if(register_receive_flag == true) {
                            unregisterReceiver(msgReceiver);
                            register_receive_flag = false;
                        }

                        e.printStackTrace();
                        Log.e(TAG,"I am in UnsupportedEncodingException");
                    }


                }


            }

        });

        public MyBinder() {
        }


        private Boolean isClientClose() {
            if(clientSocket == null)
                return true;

            else if(clientSocket.isClosed())
                return true;

            else if(!clientSocket.isConnected())
                return true;

            else
                return false;

        }

        private Boolean isServerClose() {
            if(clientSocket == null)
                return true;
            try{
                return false;
            }catch(Exception se){
                Log.e(TAG,"true");
                return true;
            }
        }

        //=============================================================
        // Setup socket connection
        //=============================================================




        public void fxsl_SocketConnect(String addr, int port) {
            Log.d("TAG", "start socket connection");
            fxsl_addr = addr;
            fxsl_port = port;
            t_connect.start();
        }

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                String recmeg = (msg.obj).toString();
                // Process the received string message

                intent.putExtra("msg2client", recmeg);
                sendBroadcast(intent);
            }
        };

        //=============================================================
        // Send data to AP
        //=============================================================
        public void fxsl_Send2AP(final String keydata) {

            byte[] msgBuffer = null;

            if(mBinder.isServerClose() || mBinder.isClientClose()) {
                Log.e(TAG,"no connection");
                return;
            }

            try {
                msgBuffer = keydata.getBytes("GB2312");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            try {
                if (clientSocket == null) {
                    Log.e(TAG, "Socket is lost");
                } else {
                    if (clientSocket.isConnected()) {
                        if (!clientSocket.isOutputShutdown()) {

                            outStream = clientSocket.getOutputStream();
                            assert msgBuffer != null;
                            outStream.write(msgBuffer);
                        }
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void fxsl_Send2Buf(final short keydata) {

        }

        public void fxsl_Send2AP(final short keydata) {

            int msgBuffer;

            if(mBinder.isServerClose() || mBinder.isClientClose()) {
                Log.e(TAG,"no connection");
                return;
            }

            msgBuffer = (int)keydata;

            try {
                if (clientSocket == null) {
                    Log.e(TAG, "Socket is lost");
                } else {
                    if (clientSocket.isConnected()) {
                        if (!clientSocket.isOutputShutdown()) {

                            outStream = clientSocket.getOutputStream();
                            outStream.write(msgBuffer);
                        }
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void fxsl_Send2AP(final byte[] data, int cnt) {

            //byte[] buffer = new byte[100];

            if(mBinder.isServerClose() || mBinder.isClientClose()) {
                Log.e(TAG,"no connection");
                return;
            }



            try {
                if (clientSocket == null) {
                    Log.e(TAG, "Socket is lost");
                } else {
                    if (clientSocket.isConnected()) {
                        if (!clientSocket.isOutputShutdown()) {

                            outStream = clientSocket.getOutputStream();
                            outStream.write(data,0,cnt);
                        }
                    }

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}  