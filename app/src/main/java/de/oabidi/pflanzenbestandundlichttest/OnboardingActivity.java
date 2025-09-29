package de.oabidi.pflanzenbestandundlichttest;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import de.oabidi.pflanzenbestandundlichttest.core.system.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.PlantRepository;

/**
 * Activity hosting the onboarding flow so it can be launched independently.
 */
public class OnboardingActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        if (savedInstanceState == null) {
            PlantRepository repository = ((RepositoryProvider) getApplication()).getRepository();
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.onboarding_container, OnboardingFragment.newInstance(repository))
                .commit();
        }
    }
}
