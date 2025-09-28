package de.oabidi.pflanzenbestandundlichttest.core.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface NaturalLightEstimateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(NaturalLightEstimate estimate);

    @Query("SELECT * FROM NaturalLightEstimate WHERE zoneId = :zoneId AND date = :date LIMIT 1")
    NaturalLightEstimate getForZoneAndDate(long zoneId, LocalDate date);

    @Query("SELECT * FROM NaturalLightEstimate WHERE zoneId = :zoneId ORDER BY date DESC LIMIT 1")
    NaturalLightEstimate getLatestForZone(long zoneId);

    @Query("DELETE FROM NaturalLightEstimate WHERE date < :cutoff")
    void deleteOlderThan(LocalDate cutoff);

    @Query("SELECT * FROM NaturalLightEstimate WHERE date = :date")
    List<NaturalLightEstimate> getForDate(LocalDate date);
}
