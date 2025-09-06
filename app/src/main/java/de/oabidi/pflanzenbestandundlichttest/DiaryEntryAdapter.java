package de.oabidi.pflanzenbestandundlichttest;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/**
 * RecyclerView adapter displaying diary entries with click and long-click
 * callbacks.
 */
public class DiaryEntryAdapter extends ListAdapter<DiaryEntry, DiaryEntryAdapter.ViewHolder> {

    /** Listener invoked when a diary entry is tapped. */
    public interface OnEntryClickListener {
        void onEntryClick(DiaryEntry entry);
    }

    /** Listener invoked when a diary entry is long-pressed. */
    public interface OnEntryLongClickListener {
        void onEntryLongClick(DiaryEntry entry);
    }

    private final OnEntryClickListener clickListener;
    private final OnEntryLongClickListener longClickListener;

    public DiaryEntryAdapter(OnEntryClickListener clickListener,
                             OnEntryLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    private static final DiffUtil.ItemCallback<DiaryEntry> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<DiaryEntry>() {
            @Override
            public boolean areItemsTheSame(@NonNull DiaryEntry oldItem,
                                           @NonNull DiaryEntry newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull DiaryEntry oldItem,
                                              @NonNull DiaryEntry newItem) {
                return oldItem.getPlantId() == newItem.getPlantId()
                    && oldItem.getTimeEpoch() == newItem.getTimeEpoch()
                    && Objects.equals(oldItem.getType(), newItem.getType())
                    && Objects.equals(oldItem.getNote(), newItem.getNote())
                    && Objects.equals(oldItem.getPhotoUri(), newItem.getPhotoUri());
            }
        };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.list_item_diary_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DiaryEntry entry = getItem(position);
        holder.bind(entry, clickListener, longClickListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        private final ImageView photoView;
        private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.diary_entry_text);
            photoView = itemView.findViewById(R.id.diary_entry_photo);
        }

        void bind(DiaryEntry entry, OnEntryClickListener clickListener,
                  OnEntryLongClickListener longClickListener) {
            String note = entry.getNote() != null ? entry.getNote() : "";
            String item = df.format(new Date(entry.getTimeEpoch())) + " – "
                + labelFromCode(itemView.getContext(), entry.getType());
            if (!note.isEmpty()) {
                item += " – " + note;
            }
            textView.setText(item);

            if (entry.getPhotoUri() != null) {
                photoView.setVisibility(View.VISIBLE);
                Uri uri = Uri.parse(entry.getPhotoUri());
                photoView.setImageURI(uri);
                photoView.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    v.getContext().startActivity(intent);
                });
            } else {
                photoView.setVisibility(View.GONE);
                photoView.setOnClickListener(null);
            }

            itemView.setOnClickListener(v -> clickListener.onEntryClick(entry));
            itemView.setOnLongClickListener(v -> {
                longClickListener.onEntryLongClick(entry);
                return true;
            });
        }

        private static String labelFromCode(android.content.Context context, String code) {
            switch (code) {
                case DiaryEntry.TYPE_WATER:
                    return context.getString(R.string.diary_type_water);
                case DiaryEntry.TYPE_FERTILIZE:
                    return context.getString(R.string.diary_type_fertilize);
                case DiaryEntry.TYPE_PRUNE:
                    return context.getString(R.string.diary_type_prune);
                default:
                    return code;
            }
        }
    }
}
