package com.example.fxsl.socket;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MessageReceiver extends BroadcastReceiver {

	private static final String TAG = "MessageReceiver";
	
	public static ArrayList<RecMsgHandler> RecMsgList = new ArrayList<>();


	@Override
	public void onReceive(Context context, Intent intent) {
		if(intent.getAction().endsWith("com.example.fxsl.socket.MsgReceiver"))  {
			
			String msg = intent.getStringExtra("msg2client");
			
			for(int i = 0; i < RecMsgList.size(); i++) {
                try {
                    (RecMsgList.get(i)).GetSocketMessage(msg);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }catch (RuntimeException e) {
                    Log.e(TAG,"runtime exception1");
                }
            }
		} else if (intent.getAction().endsWith("com.example.fxsl.socket.MsgReceiver2")) {
            String msg = intent.getStringExtra("datafromwcs");

            for(int i = 0; i < RecMsgList.size(); i++) {
                try {
                    (RecMsgList.get(i)).UpdateUI(msg);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (RuntimeException e) {
                    Log.e(TAG,"runtime exception2");
                }
            }
        }



	}

	public static abstract interface RecMsgHandler {

		public abstract void GetSocketMessage(String msg) throws UnsupportedEncodingException;
        public abstract void UpdateUI(String msg) throws UnsupportedEncodingException;
	}
}