package de.oabidi.pflanzenbestandundlichttest.feature.lighting;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import de.oabidi.pflanzenbestandundlichttest.R;
import de.oabidi.pflanzenbestandundlichttest.data.LedProfile;

/** Adapter for editing {@link LedProfile.ScheduleEntry} rows. */
class LedProfileScheduleAdapter extends RecyclerView.Adapter<LedProfileScheduleAdapter.EntryViewHolder> {

    interface OnEntriesChangedListener {
        void onEntriesChanged();
    }

    private final List<LedProfile.ScheduleEntry> entries = new ArrayList<>();
    private final OnEntriesChangedListener listener;

    LedProfileScheduleAdapter(OnEntriesChangedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_led_schedule_entry, parent, false);
        return new EntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        holder.bind(entries.get(position));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    void setEntries(List<LedProfile.ScheduleEntry> newEntries) {
        entries.clear();
        if (newEntries != null) {
            for (LedProfile.ScheduleEntry entry : newEntries) {
                LedProfile.ScheduleEntry copy = new LedProfile.ScheduleEntry();
                copy.setStartTime(entry.getStartTime());
                copy.setEndTime(entry.getEndTime());
                copy.setIntensityPercent(entry.getIntensityPercent());
                entries.add(copy);
            }
        }
        notifyDataSetChanged();
        notifyChanged();
    }

    void addEntry() {
        entries.add(new LedProfile.ScheduleEntry());
        notifyItemInserted(entries.size() - 1);
        notifyChanged();
    }

    void removeEntry(int position) {
        if (position < 0 || position >= entries.size()) {
            return;
        }
        entries.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, entries.size() - position);
        notifyChanged();
    }

    List<LedProfile.ScheduleEntry> getEntries() {
        List<LedProfile.ScheduleEntry> copy = new ArrayList<>(entries.size());
        for (LedProfile.ScheduleEntry entry : entries) {
            LedProfile.ScheduleEntry duplicate = new LedProfile.ScheduleEntry();
            duplicate.setStartTime(entry.getStartTime());
            duplicate.setEndTime(entry.getEndTime());
            duplicate.setIntensityPercent(entry.getIntensityPercent());
            copy.add(duplicate);
        }
        return copy;
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onEntriesChanged();
        }
    }

    class EntryViewHolder extends RecyclerView.ViewHolder {
        private final TextInputEditText startInput;
        private final TextInputEditText endInput;
        private final TextInputEditText intensityInput;
        private final Button removeButton;

        private LedProfile.ScheduleEntry boundEntry;
        private final TextWatcher startWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (boundEntry != null) {
                    boundEntry.setStartTime(nonEmptyOrNull(s));
                    notifyChanged();
                }
            }
        };
        private final TextWatcher endWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (boundEntry != null) {
                    boundEntry.setEndTime(nonEmptyOrNull(s));
                    notifyChanged();
                }
            }
        };
        private final TextWatcher intensityWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (boundEntry != null) {
                    try {
                        int value = Integer.parseInt(s.toString());
                        boundEntry.setIntensityPercent(value);
                    } catch (NumberFormatException e) {
                        boundEntry.setIntensityPercent(0);
                    }
                    notifyChanged();
                }
            }
        };

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            startInput = itemView.findViewById(R.id.input_schedule_start);
            endInput = itemView.findViewById(R.id.input_schedule_end);
            intensityInput = itemView.findViewById(R.id.input_schedule_intensity);
            removeButton = itemView.findViewById(R.id.btn_remove_entry);
            startInput.addTextChangedListener(startWatcher);
            endInput.addTextChangedListener(endWatcher);
            intensityInput.addTextChangedListener(intensityWatcher);
            removeButton.setOnClickListener(v -> removeEntry(getBindingAdapterPosition()));
        }

        void bind(LedProfile.ScheduleEntry entry) {
            boundEntry = entry;
            setTextSafely(startInput, entry.getStartTime());
            setTextSafely(endInput, entry.getEndTime());
            intensityInput.setText(entry.getIntensityPercent() == 0
                ? ""
                : Integer.toString(entry.getIntensityPercent()));
        }

        private String nonEmptyOrNull(CharSequence cs) {
            if (cs == null) {
                return null;
            }
            String trimmed = cs.toString().trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private void setTextSafely(TextInputEditText editText, String value) {
            if (value == null) {
                editText.setText(null);
            } else {
                editText.setText(value);
            }
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
