package com.wifidirect.group.send;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.king.zxing.CaptureActivity;
import com.wifidirect.group.R;

public class ScanCodeActivity extends CaptureActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_scan_code;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
