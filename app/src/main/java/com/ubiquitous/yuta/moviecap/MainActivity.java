package com.ubiquitous.yuta.moviecap;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    String TAG = this.getClass().getSimpleName();

    private CameraManager cameraManager;
    private StreamConfigurationMap streamConfigurationMap;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureBuilder;
    private CameraCaptureSession cameraCaptureSession;
    private Size cameraPreviewSize;
    private Size videoSize;

    // record
    private boolean isRecordingVideo;
    private MediaRecorder mediaRecorder;
    private Surface recorderSurface;

    private Handler backgroundHandler;


    // GUI
    private TextureView cameraTextureView;
    private Button recordButton;

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        } else {
            return false;
        }
    }

    private void prepareCamera() {
        CameraManager cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String cameraID : cameraManager.getCameraIdList()) {

                // Log.d(TAG, cameraID);
                // Log.d( TAG, CameraCharacteristics.LENS_FACING.getName() );

                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                cameraPreviewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];

                videoSize = chooseVideoSize(streamConfigurationMap.getOutputSizes(MediaRecorder.class));

                // this.configureTransform();

                cameraManager.openCamera(cameraID, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        Log.d(TAG, "onOpend()");
                        cameraDevice = camera;
                        createCameraPreviewSession();
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        camera.close();
                        cameraDevice = null;
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        camera.close();
                        cameraDevice = null;
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        return choices[choices.length - 1];
    }

    protected void createCameraPreviewSession() {
        if (null == cameraDevice || !cameraTextureView.isAvailable() || null == cameraPreviewSize) {
            return;
        }

        SurfaceTexture surfaceTexture = this.cameraTextureView.getSurfaceTexture();

        surfaceTexture.setDefaultBufferSize(cameraPreviewSize.getWidth(), cameraPreviewSize.getHeight());

        Surface surface = new Surface(surfaceTexture);

        try {
            captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        captureBuilder.addTarget(surface);

        try {
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d(TAG, "onConfigured()");
                    cameraCaptureSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            return;
        }

        HandlerThread handlerThread = new HandlerThread("CameraPreview");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        try {
            // Previewに描画
            cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    private final TextureView.SurfaceTextureListener previewStatusChagedListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable()");
            prepareCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void prepareMediaRecorder() {

        mediaRecorder = new MediaRecorder();

        // mic
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mediaRecorder.setOutputFile(this.getVideoOutputPath());
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        // set orientation?

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String getVideoOutputPath() {
        return this.getApplicationContext().getExternalFilesDir(null).getAbsolutePath() + "/" + System.currentTimeMillis() + ".mp4";
    }

    ;

    private final Button.OnClickListener recordButtonListerner = new Button.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (isRecordingVideo) {
                // stop Recording
                isRecordingVideo = false;

                mediaRecorder.stop();
                mediaRecorder.reset();
                createCameraPreviewSession();

            } else {
                // start Recording
                isRecordingVideo = true;

                Log.d(TAG, "start Recording");

                if (cameraCaptureSession != null) {
                    Log.d(TAG, "close preview session");
                    cameraCaptureSession.close();
                    cameraCaptureSession = null;
                }

                prepareMediaRecorder();

                try {
                    captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                List<Surface> surfaces = new ArrayList<Surface>();

                SurfaceTexture surfaceTexture = cameraTextureView.getSurfaceTexture();
                surfaceTexture.setDefaultBufferSize(cameraPreviewSize.getWidth(), cameraPreviewSize.getHeight());
                Surface surface = new Surface(surfaceTexture);

                surfaces.add(surface);
                captureBuilder.addTarget(surface);

                Surface mediaRecorderSurface = mediaRecorder.getSurface();
                if (mediaRecorderSurface == null) {
                    Log.d(TAG, "mediaRecorderSurface is null");
                }
                surfaces.add(mediaRecorderSurface);
                captureBuilder.addTarget(mediaRecorderSurface);

                HandlerThread handlerThread = new HandlerThread("VideoCapture");
                handlerThread.start();
                backgroundHandler = new Handler(handlerThread.getLooper());

                Log.d(TAG, "setup backgroundHandler");

                Log.d(TAG, "cameraDevice is " + (cameraDevice != null));

                try {
                    cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            cameraCaptureSession = session;
                            updatePreview();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mediaRecorder.start();
                                }
                            });
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {

                        }
                    }, backgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.camera_activity);

        isRecordingVideo = false;

        cameraTextureView = (TextureView) this.findViewById(R.id.cameraView);
        cameraTextureView.setSurfaceTextureListener(previewStatusChagedListener);

        recordButton = (Button) this.findViewById(R.id.recordButton);
        recordButton.setOnClickListener(recordButtonListerner);

        /*
        if (this.checkCameraHardware(this.getApplicationContext())) {
            this.prepareCamera();
        }*/
    }
}
