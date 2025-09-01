package de.oabidi.pflanzenbestandundlichttest;

import android.content.Context;

import java.util.List;

public class PlantRepository {
    private final PlantDao plantDao;

    public PlantRepository(Context context) {
        PlantDatabase db = PlantDatabase.getDatabase(context);
        plantDao = db.plantDao();
    }

    public List<Plant> getAllPlants() {
        return plantDao.getAll();
    }

    public void insert(Plant plant) {
        plantDao.insert(plant);
    }

    public void update(Plant plant) {
        plantDao.update(plant);
    }

    public void delete(Plant plant) {
        plantDao.delete(plant);
    }
}
