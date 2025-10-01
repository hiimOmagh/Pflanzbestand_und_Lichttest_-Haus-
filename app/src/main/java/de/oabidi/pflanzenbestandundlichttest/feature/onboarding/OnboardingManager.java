package de.oabidi.pflanzenbestandundlichttest.feature.onboarding;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.common.util.SettingsKeys;
import de.oabidi.pflanzenbestandundlichttest.feature.main.MainActivity;
import de.oabidi.pflanzenbestandundlichttest.feature.plant.PlantDetailActivity;
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

/**
 * Coordinates the interactive onboarding tour displayed on top of {@link MainActivity}.
 */
public final class OnboardingManager implements Application.ActivityLifecycleCallbacks {
    /**
     * Callbacks implemented by {@link MainActivity} to provide navigation hooks for the tour.
     */
    public interface HostCallbacks {
        /**
         * Ensure the plant list screen is visible before the supplied runnable executes.
         */
        void ensurePlantListVisible(@NonNull Runnable onReady);

        /**
         * Launch a plant detail screen for showcasing the detail tabs.
         */
        void openFirstPlantDetail(@NonNull PlantDetailLaunchCallback callback);

        /**
         * Show the reminders screen so the floating action button is available.
         */
        void showRemindersScreen(@NonNull Runnable onReady);

        /**
         * Restore the default navigation once the tour finishes.
         */
        void returnToDefaultScreen();
    }

    /**
     * Callback describing the result of attempting to launch the plant detail screen.
     */
    public interface PlantDetailLaunchCallback {
        /**
         * Invoked when a plant detail screen was launched successfully.
         */
        void onLaunched();

        /**
         * Invoked when the plant detail screen cannot be opened (for instance when no plants exist).
         */
        void onUnavailable();
    }

    private enum Step {
        PLANT_LIST,
        ADD_PLANT,
        MEASUREMENT,
        WAITING_FOR_DETAIL,
        DETAIL_TABS,
        WAITING_FOR_MAIN,
        REMINDERS,
        COMPLETED
    }

    private final MainActivity activity;
    private final SharedPreferences preferences;
    private final HostCallbacks callbacks;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private MaterialTapTargetPrompt currentPrompt;
    private boolean running;
    private boolean awaitingPlantDetail;
    private boolean awaitingMainReturn;
    private Step currentStep = Step.PLANT_LIST;
    private boolean lifecycleRegistered;
    @Nullable
    private WeakReference<Activity> secondaryActivityRef;
    @Nullable
    private Snackbar skipSnackbar;

    public OnboardingManager(@NonNull MainActivity activity,
                             @NonNull SharedPreferences preferences,
                             @NonNull HostCallbacks callbacks) {
        this.activity = activity;
        this.preferences = preferences;
        this.callbacks = callbacks;
    }

    /**
     * Start the tour if it has not been completed previously.
     */
    public void maybeStartTour() {
        if (!preferences.getBoolean(SettingsKeys.KEY_ONBOARDING_DONE, false)) {
            startInternal(false);
        }
    }

    /**
     * Launch the tour even if it has already been marked as completed.
     */
    public void restartTour() {
        startInternal(true);
    }

    /**
     * Cancel any active prompt and unregister lifecycle callbacks.
     */
    public void onStop() {
        if (!running) {
            return;
        }
        dismissCurrentPrompt();
        dismissSkipSnackbar();
        unregisterLifecycleCallbacks();
        running = false;
        awaitingPlantDetail = false;
        awaitingMainReturn = false;
        currentStep = Step.PLANT_LIST;
        secondaryActivityRef = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable android.os.Bundle savedInstanceState) {
        // No-op
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // No-op
    }

    @Override
    public void onActivityResumed(@NonNull Activity resumedActivity) {
        if (!running) {
            return;
        }
        if (awaitingPlantDetail && resumedActivity instanceof PlantDetailActivity) {
            awaitingPlantDetail = false;
            secondaryActivityRef = new WeakReference<>(resumedActivity);
            handler.post(() -> showDetailTabsPrompt((PlantDetailActivity) resumedActivity));
        } else if (awaitingMainReturn && resumedActivity instanceof MainActivity) {
            awaitingMainReturn = false;
            handler.post(this::startRemindersStep);
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // No-op
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (secondaryActivityRef != null && secondaryActivityRef.get() == activity) {
            secondaryActivityRef = null;
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull android.os.Bundle outState) {
        // No-op
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (secondaryActivityRef != null && secondaryActivityRef.get() == activity) {
            secondaryActivityRef = null;
        }
    }

    private void startInternal(boolean force) {
        if (running) {
            onStop();
        }
        if (activity.isFinishing()) {
            return;
        }
        if (!force && preferences.getBoolean(SettingsKeys.KEY_ONBOARDING_DONE, false)) {
            return;
        }
        running = true;
        currentStep = Step.PLANT_LIST;
        registerLifecycleCallbacks();
        callbacks.ensurePlantListVisible(() -> handler.post(this::showPlantListPrompt));
    }

    private void showPlantListPrompt() {
        if (!running) {
            return;
        }
        waitForView(activity, R.id.plant_list, view -> showPrompt(activity,
            view,
            R.string.onboarding_prompt_plants_title,
            R.string.onboarding_prompt_plants_body,
            this::startAddPlantStep));
    }

    private void startAddPlantStep() {
        if (!running) {
            return;
        }
        currentStep = Step.ADD_PLANT;
        waitForToolbarTarget(activity, R.id.action_add, view -> showPrompt(activity,
            view,
            R.string.onboarding_prompt_add_title,
            R.string.onboarding_prompt_add_body,
            this::startMeasurementStep));
    }

    private void startMeasurementStep() {
        if (!running) {
            return;
        }
        currentStep = Step.MEASUREMENT;
        waitForView(activity, R.id.bottom_nav, bottomNav -> {
            View measureItem = bottomNav.findViewById(R.id.nav_measure);
            View target = measureItem != null ? measureItem : bottomNav;
            showPrompt(activity,
                target,
                R.string.onboarding_prompt_measure_title,
                R.string.onboarding_prompt_measure_body,
                this::startDetailStep);
        });
    }

    private void startDetailStep() {
        if (!running) {
            return;
        }
        currentStep = Step.WAITING_FOR_DETAIL;
        awaitingPlantDetail = true;
        callbacks.openFirstPlantDetail(new PlantDetailLaunchCallback() {
            @Override
            public void onLaunched() {
                // Await activity lifecycle callback.
            }

            @Override
            public void onUnavailable() {
                awaitingPlantDetail = false;
                showInfoSnackbar(activity, R.string.onboarding_prompt_missing_plant);
                handler.post(OnboardingManager.this::startRemindersStep);
            }
        });
    }

    private void showDetailTabsPrompt(@NonNull PlantDetailActivity detailActivity) {
        if (!running) {
            detailActivity.finish();
            return;
        }
        currentStep = Step.DETAIL_TABS;
        waitForView(detailActivity, R.id.detail_tab_layout, tabLayout -> showPrompt(detailActivity,
            tabLayout,
            R.string.onboarding_prompt_tabs_title,
            R.string.onboarding_prompt_tabs_body,
            () -> finishDetailStep(detailActivity)));
    }

    private void finishDetailStep(@NonNull Activity detailActivity) {
        awaitingMainReturn = true;
        currentStep = Step.WAITING_FOR_MAIN;
        detailActivity.finish();
    }

    private void startRemindersStep() {
        if (!running) {
            return;
        }
        currentStep = Step.REMINDERS;
        callbacks.showRemindersScreen(() -> waitForView(activity,
            R.id.fab_add_reminder,
            fab -> showPrompt(activity,
                fab,
                R.string.onboarding_prompt_reminders_title,
                R.string.onboarding_prompt_reminders_body,
                this::completeTour)));
    }

    private void completeTour() {
        if (!running) {
            return;
        }
        currentStep = Step.COMPLETED;
        markTourFinished();
        finishSecondaryActivity();
        callbacks.returnToDefaultScreen();
        onStop();
    }

    private void skipTour() {
        if (!running) {
            return;
        }
        markTourFinished();
        finishSecondaryActivity();
        callbacks.returnToDefaultScreen();
        onStop();
    }

    private void markTourFinished() {
        preferences.edit()
            .putBoolean(SettingsKeys.KEY_ONBOARDING_DONE, true)
            .putBoolean(SettingsKeys.KEY_ONBOARDING_COMPLETE, true)
            .putBoolean(SettingsKeys.KEY_HAS_ONBOARDED, true)
            .apply();
    }

    private void showPrompt(@NonNull Activity host,
                            @NonNull View target,
                            int titleRes,
                            int bodyRes,
                            @NonNull Runnable onFinished) {
        dismissCurrentPrompt();
        MaterialTapTargetPrompt.Builder builder = new MaterialTapTargetPrompt.Builder(host)
            .setTarget(target)
            .setPrimaryText(host.getString(titleRes))
            .setSecondaryText(host.getString(bodyRes))
            .setAnimationInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .setAutoDismiss(true)
            .setAutoFinish(true)
            .setBackButtonDismissEnabled(true)
            .setFocalPadding(host.getResources().getDimensionPixelSize(R.dimen.onboarding_prompt_focal_padding));
        builder.setPromptStateChangeListener((p, state) -> {
            if (!running) {
                return;
            }
            if (state == MaterialTapTargetPrompt.STATE_FINISHED) {
                dismissSkipSnackbar();
                handler.post(onFinished);
            } else if (state == MaterialTapTargetPrompt.STATE_NON_FOCAL_PRESSED
                || state == MaterialTapTargetPrompt.STATE_DISMISSED
                || state == MaterialTapTargetPrompt.STATE_BACK_BUTTON_PRESSED) {
                dismissSkipSnackbar();
                handler.post(this::skipTour);
            }
        });
        MaterialTapTargetPrompt prompt = builder.create();
        prompt.show();
        currentPrompt = prompt;
        showSkipSnackbar(host);
    }

    private void waitForView(@NonNull Activity host,
                             @IdRes int viewId,
                             @NonNull ViewReadyCallback callback) {
        waitForView(host, viewId, callback, 0);
    }

    private void waitForView(@NonNull Activity host,
                             @IdRes int viewId,
                             @NonNull ViewReadyCallback callback,
                             int attempt) {
        if (!running) {
            return;
        }
        View view = host.findViewById(viewId);
        if (view != null && view.isShown()) {
            callback.onViewReady(view);
            return;
        }
        if (attempt >= 30) {
            View fallback = host.findViewById(android.R.id.content);
            if (fallback != null) {
                callback.onViewReady(fallback);
            }
            return;
        }
        handler.postDelayed(() -> waitForView(host, viewId, callback, attempt + 1), 100);
    }

    private void waitForToolbarTarget(@NonNull Activity host,
                                      @IdRes int viewId,
                                      @NonNull ViewReadyCallback callback) {
        waitForToolbarTarget(host, viewId, callback, 0);
    }

    private void waitForToolbarTarget(@NonNull Activity host,
                                      @IdRes int viewId,
                                      @NonNull ViewReadyCallback callback,
                                      int attempt) {
        if (!running) {
            return;
        }
        View target = host.findViewById(viewId);
        if (target != null && target.isShown()) {
            callback.onViewReady(target);
            return;
        }
        if (attempt >= 10) {
            View toolbar = host.findViewById(R.id.top_app_bar);
            View fallback = toolbar != null ? toolbar : host.findViewById(android.R.id.content);
            if (fallback != null) {
                callback.onViewReady(fallback);
            }
            return;
        }
        handler.postDelayed(() -> waitForToolbarTarget(host, viewId, callback, attempt + 1), 120);
    }

    private void dismissCurrentPrompt() {
        if (currentPrompt != null) {
            currentPrompt.dismiss();
            currentPrompt = null;
        }
    }

    private void showSkipSnackbar(@NonNull Activity host) {
        dismissSkipSnackbar();
        View root = host.findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        skipSnackbar = Snackbar.make(root,
                host.getString(R.string.onboarding_prompt_skip_message),
                Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.onboarding_action_skip, v -> skipTour());
        skipSnackbar.show();
    }

    private void dismissSkipSnackbar() {
        if (skipSnackbar != null) {
            skipSnackbar.dismiss();
            skipSnackbar = null;
        }
    }

    private void showInfoSnackbar(@NonNull Activity host, int messageResId) {
        View root = host.findViewById(android.R.id.content);
        if (root != null) {
            Snackbar.make(root, messageResId, Snackbar.LENGTH_LONG).show();
        }
    }

    private void finishSecondaryActivity() {
        if (secondaryActivityRef != null) {
            Activity activity = secondaryActivityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.finish();
            }
            secondaryActivityRef = null;
        }
    }

    private void registerLifecycleCallbacks() {
        if (!lifecycleRegistered) {
            activity.getApplication().registerActivityLifecycleCallbacks(this);
            lifecycleRegistered = true;
        }
    }

    private void unregisterLifecycleCallbacks() {
        if (lifecycleRegistered) {
            activity.getApplication().unregisterActivityLifecycleCallbacks(this);
            lifecycleRegistered = false;
        }
    }

    private interface ViewReadyCallback {
        @MainThread
        void onViewReady(@NonNull View view);
    }
}
