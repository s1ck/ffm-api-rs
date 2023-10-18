import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

public class MemorySegments {

    static void directBuffer() {
        // struct Point {
        //      long x;
        //      long y;
        // }
        ByteBuffer point = ByteBuffer.allocateDirect(8 * 2);
        point.putLong(0, 42L);
        point.putLong(8, 1337L);

        System.out.println("point.x = " + point.getLong(0));
        System.out.println("point.y = " + point.getLong(8));

        try {
            point.getLong(16);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("IndexOutOfBoundsException: " + ex.getMessage());
        }
    }

    static void memorySegments() {
        // struct Point {
        //      long x;
        //      long y;
        // }
        MemorySegment point = Arena.ofAuto().allocate(8 * 2);
        point.set(ValueLayout.JAVA_LONG, 0, 42L);
        point.set(ValueLayout.JAVA_LONG, 8, 1337L);

        System.out.println("point.x = " + point.get(ValueLayout.JAVA_LONG, 0));
        System.out.println("point.y = " + point.get(ValueLayout.JAVA_LONG, 8));

        try {
            point.set(ValueLayout.JAVA_LONG, 16, 1984L);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("IndexOutOfBoundsException: " + ex.getMessage());
        }
    }

    static void confinedArena() {
        // struct Point {
        //      long x;
        //      long y;
        // }
        try (var offheap = Arena.ofConfined()) {
            var point = offheap.allocate(8 * 2);
            point.set(ValueLayout.JAVA_LONG, 0, 42L);
            point.set(ValueLayout.JAVA_LONG, 8, 1337L);

            System.out.println("point.x = " + point.get(ValueLayout.JAVA_LONG, 0));
            System.out.println("point.y = " + point.get(ValueLayout.JAVA_LONG, 8));
        }
    }

    static void memoryLayout() {
        // struct Point {
        //      long x;
        //      long y;
        // }
        StructLayout pointLayout = MemoryLayout.structLayout(
                ValueLayout.JAVA_LONG.withName("x"),
                ValueLayout.JAVA_LONG.withName("y")
        );
        VarHandle xHandle = pointLayout.varHandle(MemoryLayout.PathElement.groupElement("x"));
        VarHandle yHandle = pointLayout.varHandle(MemoryLayout.PathElement.groupElement("y"));

        // NOTE: new api since 22, additional base offset parameter
        xHandle = MethodHandles.insertCoordinates(xHandle, 1, 0L);
        yHandle = MethodHandles.insertCoordinates(yHandle, 1, 0L);

        try (var offheap = Arena.ofConfined()) {
            MemorySegment point = offheap.allocate(pointLayout);
            xHandle.set(point, 42L);
            yHandle.set(point, 1337L);
            System.out.println("point.x = " + xHandle.get(point));
            System.out.println("point.y = " + yHandle.get(point));
        }
    }
}
