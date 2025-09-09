package de.oabidi.pflanzenbestandundlichttest;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;
import androidx.room.PrimaryKey;

/**
 * Full-text search entity indexing plant names and notes.
 * <p>
 * The {@code rowid} of this table mirrors the {@link Plant#id} allowing fast
 * joins between the FTS index and the content table.
 */
@Fts4
@Entity(tableName = "PlantFts")
public class PlantFts {
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    public long rowId;
    public String name;
    public String description;

    public PlantFts(long rowId, String name, String description) {
        this.rowId = rowId;
        this.name = name;
        this.description = description;
    }
}
