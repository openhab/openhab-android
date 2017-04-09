/* MemorizingTrustManager - a TrustManager which asks the user about invalid
 *  certificates and memorizes their decision.
 *
 * Copyright (c) 2010 Georg Lukas <georg@op-co.de>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.duenndns.ssl;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MemorizingActivity extends Activity
		implements OnClickListener,OnCancelListener {
	final static String TAG = "MemorizingActivity";

	int decisionId;
	String app;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();
		Intent i = getIntent();
		app = i.getStringExtra(MemorizingTrustManager.DECISION_INTENT_APP);
		decisionId = i.getIntExtra(MemorizingTrustManager.DECISION_INTENT_ID, MTMDecision.DECISION_INVALID);
		String cert = i.getStringExtra(MemorizingTrustManager.DECISION_INTENT_CERT);
		Log.d(TAG, "onResume with " + i.getExtras() + " decId=" + decisionId);
		Log.d(TAG, "data: " + i.getData());
		new AlertDialog.Builder(this).setTitle(getResourceIdByName(this.getPackageName(), "string", "mtm_accept_cert"))
			.setMessage(cert)
			.setPositiveButton(getResourceIdByName(this.getPackageName(), "string", "mtm_decision_always"), this)
			.setNeutralButton(getResourceIdByName(this.getPackageName(), "string", "mtm_decision_once"), this)
			.setNegativeButton(getResourceIdByName(this.getPackageName(), "string", "mtm_decision_abort"), this)
			.setOnCancelListener(this)
			.create().show();
	}

	void sendDecision(int decision) {
		Log.d(TAG, "Sending decision to " + app + ": " + decision);
		Intent i = new Intent(MemorizingTrustManager.DECISION_INTENT + "/" + app);
		i.putExtra(MemorizingTrustManager.DECISION_INTENT_ID, decisionId);
		i.putExtra(MemorizingTrustManager.DECISION_INTENT_CHOICE, decision);
		sendBroadcast(i);
		finish();
	}
	
	// react on AlertDialog button press
	public void onClick(DialogInterface dialog, int btnId) {
		int decision;
		dialog.dismiss();
		switch (btnId) {
		case DialogInterface.BUTTON_POSITIVE:
			decision = MTMDecision.DECISION_ALWAYS;
			break;
		case DialogInterface.BUTTON_NEUTRAL:
			decision = MTMDecision.DECISION_ONCE;
			break;
		default:
			decision = MTMDecision.DECISION_ABORT;
		}
		sendDecision(decision);
	}

	public void onCancel(DialogInterface dialog) {
		sendDecision(MTMDecision.DECISION_ABORT);
	}
	
	public static int getResourceIdByName(String packageName, String className, String name) {
		   Class r = null;
		   int id = 0;
		try {
		    r = Class.forName(packageName + ".R");

		    Class[] classes = r.getClasses();
		    Class desireClass = null;

		    for (int i = 0; i < classes.length; i++) {
		        if(classes[i].getName().split("\\$")[1].equals(className)) {
		            desireClass = classes[i];

		            break;
		        }
		    }
		    if(desireClass != null)
		        id = desireClass.getField(name).getInt(desireClass);
		} catch (ClassNotFoundException | IllegalArgumentException | SecurityException | IllegalAccessException | NoSuchFieldException e) {
		    e.printStackTrace();
		}
		return id;
	}

}
