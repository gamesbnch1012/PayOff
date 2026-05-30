package com.dhruvtej.payoff;

import android.app.Application;
import android.view.View;

import androidx.lifecycle.MutableLiveData;

public class ValuePassHelper extends Application {
    public View onTopView;
    public static MutableLiveData<String> sharedValue = new MutableLiveData<>("");

    void setOnTopView(View onTopView){
        this.onTopView = onTopView;
    }

    View getOnTopView(){
        return onTopView;
    }
}
