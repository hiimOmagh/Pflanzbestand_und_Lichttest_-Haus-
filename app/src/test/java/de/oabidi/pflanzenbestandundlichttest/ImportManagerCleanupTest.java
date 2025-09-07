package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.oabidi.pflanzenbestandundlichttest.data.util.ImportManager;

@RunWith(RobolectricTestRunner.class)
@Config(application = PlantApp.class)
public class ImportManagerCleanupTest {
    private PlantDatabase db;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, PlantDatabase.class)
            .allowMainThreadQueries()
            .build();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, db);

        Plant existing = new Plant("existing", null, null, null, 0L, null);
        existing.setId(1);
        db.plantDao().insert(existing);
    }

    @After
    public void tearDown() throws Exception {
        db.close();
        Field instance = PlantDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void imagesRemovedOnFailedImport() throws Exception {
        ImportManager importer = new ImportManager(context);

        File baseDir = new File(context.getCacheDir(), "import" );
        baseDir.mkdirs();
        File image = new File(baseDir, "photo.jpg");
        try (FileOutputStream fos = new FileOutputStream(image)) {
            fos.write(new byte[]{1});
        }

        String csv = "Plants\n" +
            "id,name,description,species,location,acquired,photo\n" +
            "1,newPlant,desc,species,loc,0,photo.jpg\n";

        BufferedReader reader = new BufferedReader(new StringReader(csv));
        AtomicBoolean warning = new AtomicBoolean();
        Method m = ImportManager.class.getDeclaredMethod("parseAndInsert", BufferedReader.class, File.class, ImportManager.Mode.class, AtomicBoolean.class);
        m.setAccessible(true);
        boolean success = (Boolean) m.invoke(importer, reader, baseDir, ImportManager.Mode.REPLACE, warning);
        assertFalse(success);

        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }
}
