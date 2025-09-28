package de.oabidi.pflanzenbestandundlichttest.feature.plant;

import de.oabidi.pflanzenbestandundlichttest.R;
import android.content.Intent;
import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.oabidi.pflanzenbestandundlichttest.Plant;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;
import de.oabidi.pflanzenbestandundlichttest.core.data.LedProfile;
import de.oabidi.pflanzenbestandundlichttest.core.data.PlantZone;
import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.core.ui.InsetsUtils;
import de.oabidi.pflanzenbestandundlichttest.feature.camera.PlantPhotoCaptureFragment;

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
    private static final String ARG_ZONE_ORIENTATION = "zone_orientation";
    private static final String ARG_ZONE_NOTES = "zone_notes";
    private static final String ARG_LED_PROFILE_ID = "led_profile_id";
    private static final String STATE_LED_PROFILE_ID = "state_led_profile_id";

    private TextInputEditText nameInput;
    private TextInputEditText speciesInput;
    private TextInputEditText locationInput;
    private TextInputEditText acquiredInput;
    private TextInputEditText notesInput;
    private MaterialAutoCompleteTextView zoneOrientationInput;
    private TextInputEditText zoneNotesInput;
    private MaterialAutoCompleteTextView ledProfileInput;
    private ImageView photoView;

    private Uri photoUri;
    private long acquiredEpoch = System.currentTimeMillis();
    private PlantEditPresenter presenter;
    private PlantRepository repository;
    private String[] zoneOrientationValues;
    private String[] zoneOrientationLabels;
    private final List<LedProfile> ledProfiles = new ArrayList<>();
    private ArrayAdapter<String> ledProfileAdapter;
    @Nullable
    private Long selectedLedProfileId;

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
            if (plant.getLedProfileId() != null) {
                args.putLong(ARG_LED_PROFILE_ID, plant.getLedProfileId());
            }
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getParentFragmentManager().setFragmentResultListener(
            PlantPhotoCaptureFragment.RESULT_KEY,
            this,
            (requestKey, bundle) -> {
                String uriString = bundle.getString(PlantPhotoCaptureFragment.EXTRA_PHOTO_URI);
                if (uriString != null && !uriString.isEmpty()) {
                    Uri captured = Uri.parse(uriString);
                    photoUri = captured;
                    if (photoView != null) {
                        photoView.setImageURI(captured);
                        photoView.setVisibility(View.VISIBLE);
                    }
                }
            }
        );
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
        ScrollView scrollView = (ScrollView) view;
        scrollView.setClipToPadding(false);
        InsetsUtils.applySystemWindowInsetsPadding(scrollView, false, false, false, true);
        InsetsUtils.requestApplyInsetsWhenAttached(scrollView);
        nameInput = view.findViewById(R.id.input_name);
        speciesInput = view.findViewById(R.id.input_species);
        locationInput = view.findViewById(R.id.input_location);
        acquiredInput = view.findViewById(R.id.input_acquired);
        notesInput = view.findViewById(R.id.input_notes);
        zoneOrientationInput = view.findViewById(R.id.input_zone_orientation);
        zoneNotesInput = view.findViewById(R.id.input_zone_notes);
        ledProfileInput = view.findViewById(R.id.input_led_profile);
        photoView = view.findViewById(R.id.image_photo);

        Button pickPhoto = view.findViewById(R.id.btn_pick_photo);
        Button capturePhoto = view.findViewById(R.id.btn_capture_photo);
        Button saveButton = view.findViewById(R.id.btn_save);

        pickPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        capturePhoto.setOnClickListener(v -> launchCameraCapture());

        zoneOrientationValues = getResources().getStringArray(R.array.plant_zone_orientation_values);
        zoneOrientationLabels = getResources().getStringArray(R.array.plant_zone_orientation_labels);
        ArrayAdapter<String> orientationAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_list_item_1, zoneOrientationLabels);
        zoneOrientationInput.setAdapter(orientationAdapter);
        zoneOrientationInput.setOnClickListener(v -> zoneOrientationInput.showDropDown());
        zoneOrientationInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                zoneOrientationInput.post(zoneOrientationInput::showDropDown);
            }
        });

        ledProfileAdapter = new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_list_item_1, new ArrayList<>());
        ledProfileInput.setAdapter(ledProfileAdapter);
        ledProfileAdapter.add(getString(R.string.led_profile_none));
        ledProfileInput.setText(getString(R.string.led_profile_none), false);
        ledProfileInput.setOnClickListener(v -> ledProfileInput.showDropDown());
        ledProfileInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                ledProfileInput.post(ledProfileInput::showDropDown);
            }
        });
        ledProfileInput.setOnItemClickListener((parent, view1, position, id) -> {
            if (position == 0) {
                selectedLedProfileId = null;
            } else {
                int index = position - 1;
                if (index >= 0 && index < ledProfiles.size()) {
                    selectedLedProfileId = ledProfiles.get(index).getId();
                }
            }
        });

        Bundle args = getArguments();
        if (args != null) {
            nameInput.setText(args.getString(ARG_NAME));
            speciesInput.setText(args.getString(ARG_SPECIES));
            locationInput.setText(args.getString(ARG_LOCATION));
            acquiredEpoch = args.getLong(ARG_ACQUIRED, System.currentTimeMillis());
            acquiredInput.setText(formatDate(acquiredEpoch));
            notesInput.setText(args.getString(ARG_NOTES));
            applyZoneOrientation(args.getString(ARG_ZONE_ORIENTATION));
            zoneNotesInput.setText(args.getString(ARG_ZONE_NOTES));
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
            if (args.containsKey(ARG_LED_PROFILE_ID)) {
                long id = args.getLong(ARG_LED_PROFILE_ID);
                if (id > 0) {
                    selectedLedProfileId = id;
                }
            }
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_LED_PROFILE_ID)) {
            long savedId = savedInstanceState.getLong(STATE_LED_PROFILE_ID, 0);
            selectedLedProfileId = savedId > 0 ? savedId : null;
        }

        if (repository == null) {
            repository = RepositoryProvider.getRepository(requireContext());
        }
        presenter = new PlantEditPresenterImpl(this, repository, requireContext().getApplicationContext());

        acquiredInput.setOnClickListener(v -> showDatePicker());
        saveButton.setOnClickListener(v -> presenter.savePlant());
        presenter.loadPlantZone();
        presenter.loadLedProfiles();
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

    private void launchCameraCapture() {
        FragmentManager manager = getParentFragmentManager();
        PlantPhotoCaptureFragment.show(manager, android.R.id.content);
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

    @Nullable
    @Override
    public Long getSelectedLedProfileId() {
        return selectedLedProfileId;
    }

    @Override
    public long getPlantId() {
        Bundle args = getArguments();
        return args != null ? args.getLong(ARG_ID, 0) : 0;
    }

    @Nullable
    @Override
    public String getZoneOrientation() {
        if (zoneOrientationInput == null) {
            return null;
        }
        String text = getAutoCompleteText(zoneOrientationInput);
        if (text.isEmpty()) {
            return null;
        }
        if (zoneOrientationLabels != null && zoneOrientationValues != null) {
            for (int i = 0; i < zoneOrientationLabels.length; i++) {
                if (zoneOrientationLabels[i].equalsIgnoreCase(text)) {
                    return zoneOrientationValues[i];
                }
            }
        }
        return PlantZone.normalizeOrientation(text);
    }

    @Nullable
    @Override
    public String getZoneNotes() {
        if (zoneNotesInput == null) {
            return null;
        }
        String text = getText(zoneNotesInput);
        return text.isEmpty() ? null : text;
    }

    @Override
    public void setPlantZone(@Nullable PlantZone zone) {
        if (!isAdded() || zoneOrientationInput == null || zoneNotesInput == null) {
            return;
        }
        if (zone == null) {
            zoneOrientationInput.setText(null, false);
            zoneNotesInput.setText(null);
        } else {
            applyZoneOrientation(zone.getOrientation());
            zoneNotesInput.setText(zone.getNotes());
        }
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
        String zoneOrientation = getZoneOrientation();
        if (zoneOrientation != null) {
            result.putString(ARG_ZONE_ORIENTATION, zoneOrientation);
        }
        String zoneNotes = getZoneNotes();
        if (zoneNotes != null) {
            result.putString(ARG_ZONE_NOTES, zoneNotes);
        }
        if (plant.getPhotoUri() != null) {
            result.putString(ARG_PHOTO, plant.getPhotoUri().toString());
        }
        if (selectedLedProfileId != null) {
            result.putLong(ARG_LED_PROFILE_ID, selectedLedProfileId);
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

    @Override
    public void showLedProfiles(List<LedProfile> profiles, @Nullable Long selectedId) {
        if (!isAdded() || ledProfileInput == null) {
            return;
        }
        ledProfiles.clear();
        if (profiles != null) {
            ledProfiles.addAll(profiles);
        }
        List<String> displayNames = new ArrayList<>();
        displayNames.add(getString(R.string.led_profile_none));
        for (LedProfile profile : ledProfiles) {
            displayNames.add(profile.getName());
        }
        ledProfileAdapter.clear();
        ledProfileAdapter.addAll(displayNames);
        ledProfileAdapter.notifyDataSetChanged();
        if (selectedId != null) {
            selectedLedProfileId = selectedId;
            int index = findProfileIndex(selectedId);
            if (index >= 0 && index + 1 < displayNames.size()) {
                ledProfileInput.setText(displayNames.get(index + 1), false);
            }
        } else if (selectedLedProfileId != null) {
            int index = findProfileIndex(selectedLedProfileId);
            if (index >= 0 && index + 1 < displayNames.size()) {
                ledProfileInput.setText(displayNames.get(index + 1), false);
            } else {
                selectedLedProfileId = null;
                ledProfileInput.setText(displayNames.get(0), false);
            }
        } else {
            ledProfileInput.setText(displayNames.get(0), false);
        }
    }

    private int findProfileIndex(long profileId) {
        for (int i = 0; i < ledProfiles.size(); i++) {
            if (ledProfiles.get(i).getId() == profileId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedLedProfileId != null) {
            outState.putLong(STATE_LED_PROFILE_ID, selectedLedProfileId);
        }
    }

    private void applyZoneOrientation(@Nullable String orientation) {
        if (zoneOrientationInput == null) {
            return;
        }
        if (orientation == null) {
            zoneOrientationInput.setText(null, false);
            return;
        }
        String normalized = PlantZone.normalizeOrientation(orientation);
        if (normalized == null) {
            zoneOrientationInput.setText(orientation, false);
            return;
        }
        int index = findOrientationIndex(normalized);
        if (index >= 0 && zoneOrientationLabels != null && index < zoneOrientationLabels.length) {
            zoneOrientationInput.setText(zoneOrientationLabels[index], false);
        } else {
            zoneOrientationInput.setText(normalized, false);
        }
    }

    private int findOrientationIndex(@NonNull String orientation) {
        if (zoneOrientationValues == null) {
            return -1;
        }
        for (int i = 0; i < zoneOrientationValues.length; i++) {
            if (zoneOrientationValues[i].equalsIgnoreCase(orientation)) {
                return i;
            }
        }
        return -1;
    }

    private static String getAutoCompleteText(MaterialAutoCompleteTextView view) {
        CharSequence cs = view.getText();
        return cs != null ? cs.toString().trim() : "";
    }
}
