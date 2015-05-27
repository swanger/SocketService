package com.fxsl.socket;

import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MessageReceiver extends BroadcastReceiver {

	private static final String TAG = "MessageReceiver";
	
	public static ArrayList<RecMsgHandler> RecMsgList = new ArrayList<RecMsgHandler>();


	@Override
	public void onReceive(Context context, Intent intent) {
		if(intent.getAction().endsWith("com.fxsl.remoteuitest.MsgReceiver"))  {
			
			String msg = intent.getStringExtra("msg2client");
			
			for(int i = 0; i < RecMsgList.size(); i++) {
				((RecMsgHandler)RecMsgList.get(i)).GetSocketMessage(msg);
			}
		}


	}

	public static abstract interface RecMsgHandler {

		public abstract void GetSocketMessage(String msg);
	}
}