package com.duzzi.screen;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;


import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

class ScreenEncoder implements Runnable {
    private final int mWidth = 1280;
    private final int mHeight = 720;
    private final int mBitrate = 800 * 1024 * 8;//800 * 1024 * 8
    private final int I_FRAME_INTERVAL = 10;
    private final int FRAME_RATE = 24;

    MediaProjection mMediaProjection;
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding

    private MediaMuxer mMuxer;
    private Surface mSurface;
    private boolean mMuxerStarted;
    private String TAG = "ScreenEncoder";
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private long TIMEOUT_US = 10000;
    private int mVideoTrackIndex = -1;
    private VirtualDisplay mVirtualDisplay;
    private MediaCodec mEncoder;

    ScreenEncoder(MediaProjection mp) {
        mMediaProjection = mp;
        try {
            prepareEncoder();

            final String path = Environment.getExternalStorageDirectory().getPath()
                    + File.separator + getCurrentTime() + ".mp4";
            //path中不能带有 ‘:’ ，否则录制的mp4无法播放  why???

            mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG + "-display",
                mWidth, mHeight, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mSurface, null, null);
        Log.d(TAG, "created virtual display: " + mVirtualDisplay);
    }


    AtomicBoolean mQuit = new AtomicBoolean(false);

    private String getCurrentTime() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh_mm_ss");
        return simpleDateFormat.format(date);

    }


    /**
     * stop task
     */
    public final void stopRecord() {
        mQuit.set(true);
    }

    @Override
    public void run() {
        try {
            recordVirtualDisplay();
        } finally {
            release();
        }
    }

    private void recordVirtualDisplay() {
        while (!mQuit.get()) {
            int index = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            Log.i(TAG, "dequeue output buffer index=" + index);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                resetOutputFormat();

            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "retrieving buffers time out!");
                SystemClock.sleep(10);
            } else if (index >= 0) {
                if (!mMuxerStarted) {
                    throw new IllegalStateException("MediaMuxer dose not call addTrack(format) ");
                }
                encodeToVideoTrack(index);

                mEncoder.releaseOutputBuffer(index, false);
            }
        }
    }

    private void encodeToVideoTrack(int index) {
        ByteBuffer encodedData = mEncoder.getOutputBuffer(index);

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.
            // Ignore it.
            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
        }
        if (mBufferInfo.size == 0) {
            Log.d(TAG, "info.size == 0, drop it.");
            encodedData = null;
        } else {
            Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
                    + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
                    + ", offset=" + mBufferInfo.offset);
        }
        if (encodedData != null) {
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);
            Log.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer...");
        }
    }

    private void resetOutputFormat() {
        // should happen before receiving buffers, and should only happen once
        if (mMuxerStarted) {
            throw new IllegalStateException("output format already changed!");
        }
        MediaFormat newFormat = mEncoder.getOutputFormat();

        Log.i(TAG, "output format changed.\n new format: " + newFormat.toString());
        mVideoTrackIndex = mMuxer.addTrack(newFormat);
        mMuxer.start();
        mMuxerStarted = true;
        Log.i(TAG, "started media muxer, videoIndex=" + mVideoTrackIndex);
    }

    private void prepareEncoder() throws IOException {

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        Log.d(TAG, "created video format: " + format);
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mEncoder.createInputSurface();
        Log.d(TAG, "created input surface: " + mSurface);
        mEncoder.start();
    }

    private void release() {
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }
}
