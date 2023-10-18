import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HugeLongArrayTest {

    @Test
    void test() throws NoSuchMethodException, IllegalAccessException {
        long size = 10;
        long default_value = 42;
        try (var array = new HugeLongArray(size, default_value, Main.NATIVE_LIB)) {
            for (int index = 0; index < size; index++) {
                assertEquals(default_value, array.get(index));
            }
        }
    }

    @Test
    void setAndGet() throws NoSuchMethodException, IllegalAccessException {
        long size = 10;
        long default_value = 0;
        try (var array = new HugeLongArray(size, default_value, Main.NATIVE_LIB)) {
            array.set(0, 1);
            assertEquals(1, array.get(0));
            array.set(1, 42);
            assertEquals(42, array.get(1));
            assertEquals(default_value, array.get(2));
        }
    }

    @Test
    void sort() throws NoSuchMethodException, IllegalAccessException {
        long size = 10;
        long defaultValue = 0;
        var data = new Random().longs(10).toArray();
        Arrays.sort(data);
        try (var array = new HugeLongArray(size, defaultValue, Main.NATIVE_LIB)) {
            for (int i = 0; i < data.length; i++) {
                array.set(i, data[i]);
            }
            array.sort(Long::compare);

            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i], array.get(i));
            }
        }
    }
}
