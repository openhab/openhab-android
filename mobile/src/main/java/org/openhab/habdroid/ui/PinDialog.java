package org.openhab.habdroid.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import org.openhab.habdroid.R;


public class PinDialog extends Dialog implements
        View.OnClickListener {


    public PinDialogActivity activity;

    public Button button0, button1, button2, button3, button4, button5, button6, button7, button8, button9, buttonCancel, buttonOk;
    public TextView t1, t2, t3, t4;

    private String enteredText = "";

    private String pin;

    public PinDialog(PinDialogActivity a, String pin){
        super(a);
        activity = a;
        this.pin = pin;

    }


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.pin_dialog);
        button0 = (Button) findViewById(R.id.button0);
        button1 = (Button) findViewById(R.id.button1);
        button2 = (Button) findViewById(R.id.button2);
        button3 = (Button) findViewById(R.id.button3);
        button4 = (Button) findViewById(R.id.button4);
        button5 = (Button) findViewById(R.id.button5);
        button6 = (Button) findViewById(R.id.button6);
        button7 = (Button) findViewById(R.id.button7);
        button8 = (Button) findViewById(R.id.button8);
        button9 = (Button) findViewById(R.id.button9);
        buttonCancel = (Button) findViewById(R.id.buttonCancel);
        buttonOk  = (Button) findViewById(R.id.buttonOk);


        button0.setOnClickListener(this);
        button1.setOnClickListener(this);
        button2.setOnClickListener(this);
        button3.setOnClickListener(this);
        button4.setOnClickListener(this);
        button5.setOnClickListener(this);
        button6.setOnClickListener(this);
        button7.setOnClickListener(this);
        button8.setOnClickListener(this);
        button9.setOnClickListener(this);
        button0.setOnClickListener(this);
        buttonCancel.setOnClickListener(this);
        buttonOk.setOnClickListener(this);

        t1 = (TextView) findViewById(R.id.pinTextView1);
        t2 = (TextView) findViewById(R.id.pinTextView2);
        t3 = (TextView) findViewById(R.id.pinTextView3);
        t4 = (TextView) findViewById(R.id.pinTextView4);


    }




    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.button0:
                enteredText += "0";
                break;
            case R.id.button1:
                enteredText += "1";
                break;
            case R.id.button2:
                enteredText += "2";
                break;
            case R.id.button3:
                enteredText += "3";
                break;
            case R.id.button4:
                enteredText += "4";
                break;
            case R.id.button5:
                enteredText += "5";
                break;
            case R.id.button6:
                enteredText += "6";
                break;
            case R.id.button7:
                enteredText += "7";
                break;
            case R.id.button8:
                enteredText += "8";
                break;
            case R.id.button9:
                enteredText += "9";
                break;
            case R.id.buttonCancel:
                activity.onPinAborted();
                this.dismiss();
                break;
            case R.id.buttonOk:
                if(enteredText.equals(pin)){
                    activity.onPinEntered(enteredText);
                    this.dismiss();
                }else{
                    enteredText = "";
                }

                break;
        }

        if(enteredText.length() > 4){
            enteredText = enteredText.substring(0,4);
        }

        updateTextViews();

    }

    private void updateTextViews(){
        if(enteredText.length() >= 1){
            t1.setText("*");
        }else{
            t1.setText("");
        }

        if(enteredText.length() >= 2){
            t2.setText("*");
        }else{
            t2.setText("");
        }

        if(enteredText.length() >= 3){
            t3.setText("*");
        }else{
            t3.setText("");
        }

        if(enteredText.length() >= 4) {
            t4.setText("*");
        }else{
            t4.setText("");
        }
    }

}

