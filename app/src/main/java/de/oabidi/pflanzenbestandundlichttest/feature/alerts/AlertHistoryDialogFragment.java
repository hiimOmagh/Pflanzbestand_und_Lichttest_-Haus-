package de.oabidi.pflanzenbestandundlichttest.feature.alerts;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.oabidi.pflanzenbestandundlichttest.RepositoryProvider;
import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.data.ProactiveAlertLog;
import de.oabidi.pflanzenbestandundlichttest.repository.ProactiveAlertRepository;

/**
 * Dialog fragment listing recent proactive alert events.
 */
public class AlertHistoryDialogFragment extends DialogFragment {
    private static final int HISTORY_LIMIT = 20;

    public static AlertHistoryDialogFragment newInstance() {
        return new AlertHistoryDialogFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle(R.string.alert_history_title)
            .setMessage(R.string.alert_history_loading)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        loadHistory(dialog);
        return dialog;
    }

    private void loadHistory(AlertDialog dialog) {
        ProactiveAlertRepository repository = RepositoryProvider.getAlertRepository(requireContext());
        repository.getRecentAlerts(HISTORY_LIMIT, logs -> {
            if (!isAdded()) {
                return;
            }
            if (logs == null || logs.isEmpty()) {
                dialog.setMessage(getString(R.string.alert_history_empty));
                return;
            }
            dialog.setMessage(formatLogs(logs));
        }, error -> {
            if (isAdded()) {
                dialog.setMessage(getString(R.string.alert_history_error));
            }
        });
    }

    private CharSequence formatLogs(List<ProactiveAlertLog> logs) {
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault());
        StringBuilder builder = new StringBuilder();
        for (ProactiveAlertLog log : logs) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            Date date = new Date(log.getCreatedAt());
            builder.append(formatter.format(date))
                .append(" – ")
                .append(log.getMessage());
        }
        return builder.toString();
    }
}
