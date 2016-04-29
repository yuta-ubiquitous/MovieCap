package com.ubiquitous.yuta.moviecap;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    String TAG = this.getClass().getSimpleName();

    private CameraManager cameraManager;
    private StreamConfigurationMap streamConfigurationMap;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureBuilder;
    private CameraCaptureSession cameraCaptureSession;

    private TextureView cameraTextureView;
    private Size cameraPreviewSize;

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

    protected void updatePreview(){
        if(null == cameraDevice){
            return;
        }

        HandlerThread handlerThread = new HandlerThread("CameraPreview");
        handlerThread.start();
        final Handler backgroundHandler = new Handler(handlerThread.getLooper());

        try{
            cameraCaptureSession.setRepeatingRequest(captureBuilder.build(), null, backgroundHandler);
        }catch(CameraAccessException e){
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.camera_activity);

        cameraTextureView = (TextureView) this.findViewById(R.id.cameraView);

        cameraTextureView.setSurfaceTextureListener(previewStatusChagedListener);

        /*
        if (this.checkCameraHardware(this.getApplicationContext())) {
            this.prepareCamera();
        }*/
    }
}
