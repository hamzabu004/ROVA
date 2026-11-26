package rova.android;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Stationary capture only. JPEG bytes never leave memory; only reviewed output can be saved. */
@SuppressWarnings("deprecation")
public final class CameraActivity extends Activity implements SurfaceHolder.Callback {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Camera camera;
    private SurfaceView preview;
    private ImageView result;
    private TextView status;
    private Switch blur;
    private Button capture, save, retry;
    private Bitmap processed;
    private boolean resumed, busy;
    private int generation, cameraId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16,16,16,16);
        TextView title = new TextView(this);
        title.setText("Doorway photo • stop and hold still before capture");
        root.addView(title);
        blur = new Switch(this);
        blur.setText("Blur faces on this device");
        blur.setChecked(state == null || state.getBoolean("face_blur_enabled", true));
        root.addView(blur);
        TextView warning = new TextView(this);
        warning.setText("Face detection can miss faces, especially profiles and small faces. Review every photo. With blur off, faces remain visible. Only Save writes a photo to private app storage.");
        root.addView(warning);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        frame.addView(preview);
        result = new ImageView(this);
        result.setScaleType(ImageView.ScaleType.FIT_CENTER);
        result.setVisibility(View.GONE);
        frame.addView(result);
        root.addView(frame, new LinearLayout.LayoutParams(-1,0,1));
        status = new TextView(this); root.addView(status);
        capture = new Button(this); capture.setText("Capture photo"); root.addView(capture);
        save = new Button(this); save.setText("Save reviewed photo privately"); root.addView(save);
        retry = new Button(this); retry.setText("Discard / new photo"); root.addView(retry);
        capture.setOnClickListener(v -> takePhoto());
        save.setOnClickListener(v -> savePhoto());
        retry.setOnClickListener(v -> { generation++; busy=false; clearResult(); openCamera(); });
        setContentView(root); controls();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("face_blur_enabled", blur.isChecked());
    }

    @Override protected void onResume() {
        super.onResume(); resumed=true;
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, 71);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if (code==71 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) openCamera();
        else { status.setText("Camera permission is required. Reopen this screen to try again."); controls(); }
    }
    @Override protected void onPause() {
        resumed=false; generation++; busy=false; releaseCamera(); clearResult(); super.onPause();
    }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
    private void controls() {
        capture.setEnabled(resumed && camera!=null && !busy && processed==null);
        save.setEnabled(processed!=null && !busy);
        retry.setEnabled(!busy);
        blur.setEnabled(!busy && processed==null);
    }
    private void clearResult() {
        result.setImageDrawable(null); result.setVisibility(View.GONE);
        if (processed!=null) { processed.recycle(); processed=null; }
        preview.setVisibility(View.VISIBLE); controls();
    }
    private void releaseCamera() {
        if (camera!=null) { try { camera.stopPreview(); } catch (RuntimeException ignored) {} camera.release(); camera=null; }
        controls();
    }
    private void openCamera() {
        if (!resumed || busy || processed!=null || camera!=null || !preview.getHolder().getSurface().isValid()
                || checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) return;
        try {
            cameraId=-1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for(int i=0;i<Camera.getNumberOfCameras();i++) {
                Camera.getCameraInfo(i,info);
                if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK) { cameraId=i; break; }
            }
            if(cameraId<0) throw new IOException("No rear camera");
            camera=Camera.open(cameraId);
            camera.setErrorCallback((error,c) -> { generation++; busy=false; releaseCamera(); status.setText("Camera error. Try a new photo."); });
            Camera.Parameters p=camera.getParameters();
            Camera.Size selected=null;
            for(Camera.Size s:p.getSupportedPictureSizes()) {
                if(s.width<=1920 && s.height<=1920 && (selected==null || s.width*s.height>selected.width*selected.height)) selected=s;
            }
            if(selected!=null) p.setPictureSize(selected.width,selected.height);
            List<String> modes=p.getSupportedFocusModes();
            if(modes!=null && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            p.setRotation(rotation()); camera.setParameters(p);
            camera.setDisplayOrientation(rotation());
            camera.setPreviewDisplay(preview.getHolder()); camera.startPreview();
            status.setText("Hold still. Blur setting is fixed when you capture.");
        } catch (Exception e) { releaseCamera(); status.setText("Camera unavailable. Close other camera apps and try a new photo."); }
        controls();
    }
    private int rotation() {
        Camera.CameraInfo info=new Camera.CameraInfo(); Camera.getCameraInfo(cameraId,info);
        int r=getWindowManager().getDefaultDisplay().getRotation();
        int degrees=r==Surface.ROTATION_90?90:r==Surface.ROTATION_180?180:r==Surface.ROTATION_270?270:0;
        return (info.orientation-degrees+360)%360;
    }
    private void takePhoto() {
        if(camera==null || busy || processed!=null) return;
        final boolean useBlur=blur.isChecked(); final int token=++generation;
        busy=true; controls(); status.setText("Capturing and processing on this device…");
        try {
            Camera.Parameters p=camera.getParameters(); p.setRotation(rotation()); camera.setParameters(p);
            camera.takePicture(null,null,(bytes,c) -> {
                releaseCamera();
                if (bytes == null) {
                    busy = false;
                    status.setText("Camera returned no photo. Try a new capture.");
                    controls();
                    return;
                }
                if(!resumed || token!=generation) { Arrays.fill(bytes,(byte)0); return; }
                worker.execute(() -> {
                    CameraProcessor.Output output=null;
                    try { output=CameraProcessor.process(bytes,useBlur); }
                    catch(Exception | OutOfMemoryError error) { /* Fail closed: never expose the raw image. */ }
                    finally { Arrays.fill(bytes,(byte)0); }
                    final CameraProcessor.Output done=output;
                    runOnUiThread(() -> {
                        if(!resumed || token!=generation) { if(done!=null) done.bitmap.recycle(); return; }
                        busy=false;
                        if(done==null) status.setText("Processing failed. Photo discarded; nothing can be saved. Try again.");
                        else { processed=done.bitmap; result.setImageBitmap(processed); result.setVisibility(View.VISIBLE); preview.setVisibility(View.INVISIBLE); status.setText(done.message); }
                        controls();
                    });
                });
            });
        } catch(RuntimeException error) { busy=false; releaseCamera(); status.setText("Capture failed. Nothing saved. Try again."); controls(); }
    }
    private void savePhoto() {
        if(processed==null || busy) return;
        final Bitmap copy;
        try { copy=processed.copy(Bitmap.Config.ARGB_8888,false); }
        catch(RuntimeException | OutOfMemoryError e) { status.setText("Unable to prepare save. Nothing saved."); return; }
        busy=true; controls(); final int token=generation;
        worker.execute(() -> {
            File file=null; boolean success=false;
            try {
                File dir=new File(getFilesDir(),"doorway-photos");
                if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException();
                file=File.createTempFile("doorway-",".jpg",dir);
                try(FileOutputStream stream=new FileOutputStream(file)) {
                    if(!copy.compress(Bitmap.CompressFormat.JPEG,90,stream)) throw new IOException();
                    stream.flush(); stream.getFD().sync();
                }
                success=true;
            } catch(Exception e) { if(file!=null) file.delete(); }
            finally { copy.recycle(); }
            final boolean saved=success;
            runOnUiThread(() -> { if(resumed && token==generation) { busy=false; status.setText(saved?"Saved in private app storage. Discard / new photo clears this preview.":"Save failed. Try again."); controls(); } });
        });
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { openCamera(); }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height) { if(!busy && processed==null) { releaseCamera(); openCamera(); } }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { releaseCamera(); }
}
