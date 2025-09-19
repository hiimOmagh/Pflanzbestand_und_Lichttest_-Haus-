package de.oabidi.pflanzenbestandundlichttest.feature.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.oabidi.pflanzenbestandundlichttest.BuildConfig;
import de.oabidi.pflanzenbestandundlichttest.R;

/**
 * Full-screen fragment that captures plant photos using CameraX.
 */
public class PlantPhotoCaptureFragment extends Fragment {
    public static final String RESULT_KEY = "plant_photo_capture_result";
    public static final String EXTRA_PHOTO_URI = "photo_uri";
    private static final String TAG = "PlantPhotoCapture";

    private PreviewView previewView;
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ActivityResultLauncher<String[]> permissionLauncher;

    /**
     * Displays the capture fragment by replacing the provided container.
     */
    public static void show(@NonNull FragmentManager fragmentManager, int containerId) {
        fragmentManager.beginTransaction()
            .add(containerId, new PlantPhotoCaptureFragment(), TAG)
            .addToBackStack(TAG)
            .commit();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::onPermissionResult
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plant_photo_capture, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        previewView = view.findViewById(R.id.viewfinder);
        view.findViewById(R.id.button_capture).setOnClickListener(v -> capturePhoto());
        view.findViewById(R.id.button_close).setOnClickListener(v -> closeSelf());
        view.post(this::checkPermissionsAndStartCamera);
    }

    @Override
    public void onPause() {
        super.onPause();
        unbindCamera();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbindCamera();
        previewView = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    private void onPermissionResult(@NonNull Map<String, Boolean> results) {
        boolean granted = true;
        for (Boolean value : results.values()) {
            if (Boolean.FALSE.equals(value)) {
                granted = false;
                break;
            }
        }
        if (granted) {
            startCamera();
        } else {
            Context context = getContext();
            if (context != null) {
                Toast.makeText(context, R.string.photo_capture_permission_denied, Toast.LENGTH_SHORT).show();
            }
            closeSelf();
        }
    }

    private void checkPermissionsAndStartCamera() {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        List<String> toRequest = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            toRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (toRequest.isEmpty()) {
            startCamera();
        } else {
            permissionLauncher.launch(toRequest.toArray(new String[0]));
        }
    }

    private void startCamera() {
        if (!isAdded()) {
            return;
        }
        final ListenableFuture<ProcessCameraProvider> providerFuture =
            ProcessCameraProvider.getInstance(requireContext());
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindUseCases();
            } catch (ExecutionException | InterruptedException e) {
                handleCameraError();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindUseCases() {
        if (cameraProvider == null || previewView == null) {
            return;
        }
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        int rotation = Surface.ROTATION_0;
        if (previewView.getDisplay() != null) {
            rotation = previewView.getDisplay().getRotation();
        }
        imageCapture = new ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build();

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_BACK_CAMERA,
            preview, imageCapture);
    }

    private void capturePhoto() {
        ImageCapture capture = imageCapture;
        if (capture == null || previewView == null || !isAdded()) {
            return;
        }
        Context context = requireContext();
        File directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "plants");
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(context, R.string.photo_capture_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File photoFile = new File(directory, "plant_" + timestamp + ".jpg");
        ImageCapture.OutputFileOptions options =
            new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        capture.takePicture(options, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> onPhotoSaved(photoFile));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> {
                    Context ctx = getContext();
                    if (ctx != null) {
                        Toast.makeText(ctx, R.string.photo_capture_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void onPhotoSaved(@NonNull File photoFile) {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        Uri uri = FileProvider.getUriForFile(context,
            BuildConfig.APPLICATION_ID + ".fileprovider", photoFile);
        Bundle result = new Bundle();
        result.putString(EXTRA_PHOTO_URI, uri.toString());
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        Toast.makeText(context, R.string.photo_capture_success, Toast.LENGTH_SHORT).show();
        closeSelf();
    }

    private void handleCameraError() {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, R.string.photo_capture_failed, Toast.LENGTH_SHORT).show();
        }
        closeSelf();
    }

    private void unbindCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private void closeSelf() {
        if (isAdded()) {
            getParentFragmentManager().popBackStack(TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }
}
