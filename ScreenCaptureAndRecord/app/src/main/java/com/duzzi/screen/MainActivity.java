package com.duzzi.screen;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.orhanobut.logger.Logger;


public class MainActivity extends AppCompatActivity {

    private MediaProjectionManager mMediaProjectionManager;
    public static int REQUEST_CODE_RECORD = 222;
    private ScreenEncoder mScreenEncoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intView();
        initMediaProjection();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void initMediaProjection() {
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private void intView() {
        Button btnScreenCapture = (Button) findViewById(R.id.btn_stop);
        Button btnScreenRecord = (Button) findViewById(R.id.btn_screen_record);

        assert btnScreenCapture != null;
        btnScreenCapture.setOnClickListener(mOnClickListener);
        assert btnScreenRecord != null;
        btnScreenRecord.setOnClickListener(mOnClickListener);
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_stop:
                    if (mScreenEncoder!=null) {
                        mScreenEncoder.stopRecord();
                    }
                    break;
                case R.id.btn_screen_record:
                    start(REQUEST_CODE_RECORD);
                    break;
                default:
                    break;
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void start(int resultCode) {
        Intent intent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, resultCode);
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Logger.d(requestCode);
        MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Logger.w("mediaProjection==null");
            return;
        }
        mScreenEncoder = new ScreenEncoder(mediaProjection);
        new Thread(mScreenEncoder).start();

        moveTaskToBack(true);
    }

}
