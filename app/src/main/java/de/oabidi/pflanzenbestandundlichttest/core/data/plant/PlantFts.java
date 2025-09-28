package de.oabidi.pflanzenbestandundlichttest.core.data.plant;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Fts4;
import androidx.room.PrimaryKey;

/**
 * Full-text search entity indexing plant names, species, locations and notes.
 * <p>
 * The {@code rowid} of this table mirrors the  allowing fast
 * joins between the FTS index and the content table.
 */
@Fts4
@Entity(tableName = "PlantFts")
public class PlantFts {
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    public final long rowId;
    public final String name;
    public final String species;
    public final String locationHint;
    public final String description;

    public PlantFts(long rowId, String name, String species, String locationHint, String description) {
        this.rowId = rowId;
        this.name = name;
        this.species = species;
        this.locationHint = locationHint;
        this.description = description;
    }
}
