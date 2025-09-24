package de.oabidi.pflanzenbestandundlichttest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
public class SpeciesTargetListFragmentTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void isInputValid_returnsTrueForValidInput() throws Exception {
        Object stage = newStage("10", "20");
        assertTrue(invokeIsInputValid("species", stage));
    }

    @Test
    public void isInputValid_returnsFalseForEmptyKey() throws Exception {
        Object stage = newStage("10", "20");
        assertFalse(invokeIsInputValid("", stage));
    }

    @Test
    public void isInputValid_returnsFalseForInvalidRange() throws Exception {
        Object stage = newStage("20", "10");
        assertFalse(invokeIsInputValid("species", stage));
    }

    private Object newStage(String ppfdMin, String ppfdMax)
        throws Exception {
        Class<?> stageClass = Class.forName(
            "de.oabidi.pflanzenbestandundlichttest.SpeciesTargetListFragment$StageFields");
        Constructor<?> ctor = stageClass.getDeclaredConstructor(
            EditText.class, EditText.class, EditText.class, EditText.class);
        ctor.setAccessible(true);
        return ctor.newInstance(editText(ppfdMin), editText(ppfdMax),
            editText(""), editText(""));
    }

    private EditText editText(String value) {
        EditText editText = new EditText(context);
        editText.setText(value);
        return editText;
    }

    private boolean invokeIsInputValid(String key, Object... stages) throws Exception {
        Class<?> stageClass = Class.forName(
            "de.oabidi.pflanzenbestandundlichttest.SpeciesTargetListFragment$StageFields");
        Object array = Array.newInstance(stageClass, stages.length);
        for (int i = 0; i < stages.length; i++) {
            Array.set(array, i, stages[i]);
        }
        Class<?> arrayClass = array.getClass();
        Method method = SpeciesTargetListFragment.class.getDeclaredMethod(
            "isInputValid", String.class, arrayClass);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, key, array);
    }
}
