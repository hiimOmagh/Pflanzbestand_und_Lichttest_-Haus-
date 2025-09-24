package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;
import androidx.room.PrimaryKey;

/**
 * Full-text search entity indexing diary entry notes.
 * <p>
 * The {@code rowid} matches the  to facilitate
 * efficient lookups.
 */
@Fts4
@Entity(tableName = "DiaryEntryFts")
public class DiaryEntryFts {
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    public final long rowId;
    public final String note;
    public final String type;

    public DiaryEntryFts(long rowId, String note, String type) {
        this.rowId = rowId;
        this.note = note;
        this.type = type;
    }
}
