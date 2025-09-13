package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Fragment allowing creation or editing of a {@link Plant}.
 * The result is returned to the caller via {@link androidx.fragment.app.FragmentResultOwner}.
 */
public class PlantEditFragment extends Fragment implements PlantEditView {
    public static final String RESULT_KEY = "plant_edit_result";

    private static final String ARG_ID = "id";
    private static final String ARG_NAME = "name";
    private static final String ARG_SPECIES = "species";
    private static final String ARG_LOCATION = "location";
    private static final String ARG_ACQUIRED = "acquired";
    private static final String ARG_NOTES = "notes";
    private static final String ARG_PHOTO = "photo";

    private TextInputEditText nameInput;
    private TextInputEditText speciesInput;
    private TextInputEditText locationInput;
    private TextInputEditText acquiredInput;
    private TextInputEditText notesInput;
    private ImageView photoView;

    private Uri photoUri;
    private long acquiredEpoch = System.currentTimeMillis();
    private PlantEditPresenter presenter;
    private PlantRepository repository;

    private final ActivityResultLauncher<String> photoPicker =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                photoUri = uri;
                photoView.setImageURI(uri);
            }
        });

    public static PlantEditFragment newInstance(@Nullable Plant plant, PlantRepository repository) {
        PlantEditFragment fragment = new PlantEditFragment();
        fragment.repository = repository;
        if (plant != null) {
            Bundle args = new Bundle();
            args.putLong(ARG_ID, plant.getId());
            args.putString(ARG_NAME, plant.getName());
            args.putString(ARG_SPECIES, plant.getSpecies());
            args.putString(ARG_LOCATION, plant.getLocationHint());
            args.putLong(ARG_ACQUIRED, plant.getAcquiredAtEpoch());
            args.putString(ARG_NOTES, plant.getDescription());
            if (plant.getPhotoUri() != null) {
                args.putString(ARG_PHOTO, plant.getPhotoUri().toString());
            }
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plant_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        nameInput = view.findViewById(R.id.input_name);
        speciesInput = view.findViewById(R.id.input_species);
        locationInput = view.findViewById(R.id.input_location);
        acquiredInput = view.findViewById(R.id.input_acquired);
        notesInput = view.findViewById(R.id.input_notes);
        photoView = view.findViewById(R.id.image_photo);

        Button pickPhoto = view.findViewById(R.id.btn_pick_photo);
        Button saveButton = view.findViewById(R.id.btn_save);

        pickPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));

        Bundle args = getArguments();
        if (args != null) {
            nameInput.setText(args.getString(ARG_NAME));
            speciesInput.setText(args.getString(ARG_SPECIES));
            locationInput.setText(args.getString(ARG_LOCATION));
            acquiredEpoch = args.getLong(ARG_ACQUIRED, System.currentTimeMillis());
            acquiredInput.setText(formatDate(acquiredEpoch));
            notesInput.setText(args.getString(ARG_NOTES));
            String photo = args.getString(ARG_PHOTO);
            if (photo != null) {
                photoUri = Uri.parse(photo);
                if ("content".equals(photoUri.getScheme())) {
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(
                            photoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                    }
                }
                photoView.setImageURI(photoUri);
            }
        }

        if (repository == null) {
            repository = new PlantRepository(requireContext().getApplicationContext());
        }
        presenter = new PlantEditPresenterImpl(this, repository, requireContext().getApplicationContext());

        acquiredInput.setOnClickListener(v -> showDatePicker());
        saveButton.setOnClickListener(v -> presenter.savePlant());
    }

    private static String getText(TextInputEditText editText) {
        CharSequence cs = editText.getText();
        return cs != null ? cs.toString().trim() : "";
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(acquiredEpoch);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            Calendar c = Calendar.getInstance();
            c.set(y, m, d, 0, 0, 0);
            acquiredEpoch = c.getTimeInMillis();
            acquiredInput.setText(formatDate(acquiredEpoch));
        }, year, month, day);
        dialog.show();
    }

    private String formatDate(long epoch) {
        return new SimpleDateFormat(getString(R.string.date_pattern), Locale.getDefault())
            .format(new Date(epoch));
    }

    @Override
    public String getName() {
        return getText(nameInput);
    }

    @Override
    public String getSpecies() {
        return getText(speciesInput);
    }

    @Override
    public String getLocation() {
        return getText(locationInput);
    }

    @Override
    public String getNotes() {
        return getText(notesInput);
    }

    @Override
    public long getAcquiredEpoch() {
        return acquiredEpoch;
    }

    @Override
    public Uri getPhotoUri() {
        return photoUri;
    }

    @Override
    public long getPlantId() {
        Bundle args = getArguments();
        return args != null ? args.getLong(ARG_ID, 0) : 0;
    }

    @Override
    public void showNameError() {
        nameInput.setError(getString(R.string.error_required));
    }

    @Override
    public void finishWithResult(Plant plant) {
        Bundle result = new Bundle();
        result.putLong(ARG_ID, plant.getId());
        result.putString(ARG_NAME, plant.getName());
        result.putString(ARG_SPECIES, plant.getSpecies());
        result.putString(ARG_LOCATION, plant.getLocationHint());
        result.putLong(ARG_ACQUIRED, plant.getAcquiredAtEpoch());
        result.putString(ARG_NOTES, plant.getDescription());
        if (plant.getPhotoUri() != null) {
            result.putString(ARG_PHOTO, plant.getPhotoUri().toString());
        }
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        getParentFragmentManager().popBackStack();
    }

    @Override
    public void showError(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
}
