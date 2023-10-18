import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class HugeLongArray implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment vecPtr;
    private final MethodHandle getDowncall;
    private final MethodHandle setDowncall;
    private final MethodHandle sortDowncall;
    private final MethodHandle dropDowncall;
    private final long size;
    private final long defaultValue;

    public HugeLongArray(long size, long defaultValue, NativeLib nativeLib) throws NoSuchMethodException, IllegalAccessException {
        assert size > 0;

        this.size = size;
        this.defaultValue = defaultValue;
        this.arena = Arena.ofConfined();
        var linker = Linker.nativeLinker();
        var lib = SymbolLookup.libraryLookup(nativeLib.path(), arena);

        // allocate Vec<i64> with given size and filled with default value
        var newDowncall = linker.downcallHandle(
                lib.find("vec_with_capacity").orElseThrow(),
                // &Vec with_capacity(long size, long default_value);
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );
        try {
            this.vecPtr = (MemorySegment) newDowncall.invoke(size, defaultValue);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        // Create downcalls for set/get/drop
        this.getDowncall = linker.downcallHandle(
                lib.find("vec_get").orElseThrow(),
                // long get(vec, long index, long defaultValue);
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );
        this.setDowncall = linker.downcallHandle(
                lib.find("vec_set").orElseThrow(),
                // void set(vec, long index, long value);
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
        );
        this.sortDowncall = linker.downcallHandle(
                lib.find("vec_sort_unstable").orElseThrow(),
                // void sort_unstable(vec, function pointer);
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        this.dropDowncall = linker.downcallHandle(
                lib.find("vec_drop").orElseThrow(),
                // void drop(vec);
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
    }

    public void set(long index, long value) {
        assert index < size;
        try {
            this.setDowncall.invoke(this.vecPtr, index, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public long get(long index) {
        assert index < size;
        try {
            return (long) this.getDowncall.invoke(this.vecPtr, index, this.defaultValue);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface LongComparator {
        int compare(long a, long b);
    }

    public void sort(LongComparator comparator) throws NoSuchMethodException, IllegalAccessException {
        FunctionDescriptor cmpDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG
        );
        MethodHandle cmpHandle = MethodHandles.lookup()
                .findVirtual(LongComparator.class, "compare", cmpDesc.toMethodType())
                .bindTo(comparator);
        MemorySegment cmpFp = Linker.nativeLinker().upcallStub(
                cmpHandle,
                cmpDesc,
                this.arena
        );
        try {
            this.sortDowncall.invoke(this.vecPtr, cmpFp);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            this.dropDowncall.invoke(vecPtr);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        this.arena.close();
    }
}
