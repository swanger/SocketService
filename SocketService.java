/*
 * Step 1: Add Service in AndroidManifest.xml
 * Step 2: bind in Activity
 * Step 3: Realize ServiceConnection interface
 */
package com.fxsl.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class SocketService extends Service {  
  
    private static final String TAG = "SocketService";  
    
	public String fxsl_addr = null;
	public int fxsl_port = 0;	
    private MyBinder mBinder = new MyBinder();  
  

    @Override  
    public void onCreate() {  
        super.onCreate();  
        Log.d(TAG, "onCreate() executed");  
    }  
  
    @Override  
    public int onStartCommand(Intent intent, int flags, int startId) {  
        Log.d(TAG, "onStartCommand() executed");                               
        return super.onStartCommand(intent, flags, startId);  
    }  
      
    @Override  
    public void onDestroy() {  
        super.onDestroy();
       
//        try {        	
//			inStream.close();
//			outStream.close();
//			clientSocket.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
        mBinder.heartbeatTimer.cancel();
        
        Log.d(TAG, "onDestroy() executed");  
    }  
  
    @Override  
    public IBinder onBind(Intent intent) {  
        Log.d(TAG, "onBind() executed");  
        return mBinder;  
    }    
       
    
    public class MyBinder extends Binder {
    	
    	private Handler mHandler = null;

    	private Socket clientSocket = null;
    	private InputStream inStream = null;
    	private OutputStream outStream = null;
    	private boolean connectflag = false;
    	//=============================================================
    	// new a thread to connect a socket
    	//=============================================================
        Thread t_connect = new Thread(new Runnable() {
            @Override  
            public void run() {  
                try {
                	if(fxsl_addr !=null && !fxsl_addr.isEmpty() && fxsl_port != 0) {
                		clientSocket = new Socket(fxsl_addr, fxsl_port);
                	} else
                		Log.e(TAG,"without connection");
    			} catch (UnknownHostException e) {
    				e.printStackTrace();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}  catch (Exception e) {  
    	            e.printStackTrace();  
    	        } 
                
                if(clientSocket == null) {
                    Log.e(TAG,""+clientSocket);
                    return;                	
                }

                	               
                // listening here
    			try {
    				inStream = clientSocket.getInputStream();
    			} catch (IOException e2) {
    				e2.printStackTrace();
    			}

    			while (true) {
    				byte[] buf = new byte[512];
    				String str = null;
    			
    				try {
						inStream.read(buf);
					} catch (IOException e1) {
						e1.printStackTrace();
					} catch (IndexOutOfBoundsException e) {
						Log.e(TAG,"IndexOutOfBoundsException");
					}
    		
    				try {
    					str = new String(buf, "GB2312").trim();
    				} catch (UnsupportedEncodingException e) {
    					e.printStackTrace();
    					Log.e(TAG,"I am in UnsupportedEncodingException");
    				}

    				Message msg = new Message();
    				msg.obj = str;
    				mHandler.sendMessage(msg);
    			}
            }

        });  
	  
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
    		   clientSocket.sendUrgentData(0x61);
    	    return false;  
    	   }catch(Exception se){  
    		   Log.e(TAG,"true");
    	    return true;  
    	   }  
    	} 
    	
    			
    	//=============================================================
    	// Setup a heartbeat timer
    	//============================================================= 
		public class heartbeatTask extends TimerTask {
			
			public void run() {					
				if(isServerClose() || isClientClose()) {
					connectflag = false;
				} else {
					connectflag = true;
				}
				
				Log.e(TAG,""+connectflag);
				//close socketService
				
			}
			
		}
		TimerTask heartbeat = new heartbeatTask();	
		Timer heartbeatTimer = new Timer();
		
       
    	//=============================================================
    	// Setup socket connection
    	//=============================================================  	
    	private Intent intent = new Intent("com.fxsl.remoteuitest.MsgReceiver"); 	
    	
        @SuppressLint("HandlerLeak")
		public void fxsl_SocketConnect(String addr, int port) {  
            Log.d("TAG", "start socket connection");  
            fxsl_addr = addr;
            fxsl_port = port;
            t_connect.start();
			
			heartbeatTimer.scheduleAtFixedRate(heartbeat, 5000, 1500);    
			
			this.mHandler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					String recmeg = (msg.obj).toString();
					// Process the received string message
					
					 intent.putExtra("msg2client", recmeg);
					 sendBroadcast(intent);
				}
			};
        }
    
    	//=============================================================
    	// Send data to AP
    	//=============================================================  	        
        public void fxsl_Send2AP(final String keydata) {
        	        	        	
		
			//make sure the heartbeat is on
			if(mBinder.isServerClose() || mBinder.isClientClose()) {
//				mBinder.reconnect();	
				Log.e(TAG,"no connection");
				return;
			}	
	 
			byte[] msgBuffer = null;

			try {
				msgBuffer = keydata.getBytes("GB2312");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			try {
				if (clientSocket == null) {
					Log.e(TAG, "Socket is lost");
					return;
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
	}  
}  