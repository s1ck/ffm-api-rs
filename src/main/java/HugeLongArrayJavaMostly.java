import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class HugeLongArrayJavaMostly implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment vecPtr;
    private final MethodHandle sortDowncall;
    private final FunctionDescriptor cmpDesc;
    private final MethodHandle cmpHandle;

    public HugeLongArrayJavaMostly(long size, long defaultValue, NativeLib nativeLib) throws
        NoSuchMethodException,
        IllegalAccessException {
        assert size > 0;

        this.arena = Arena.ofConfined();
        var linker = Linker.nativeLinker();
        var lib = SymbolLookup.libraryLookup(nativeLib.path(), arena);

        // allocate Vec<i64> with given size and filled with default value
        this.vecPtr = this.arena.allocate(ValueLayout.JAVA_LONG, size);

        for (long i = 0; i < size; i++) {
            this.vecPtr.setAtIndex(ValueLayout.JAVA_LONG, i, defaultValue);
        }

        this.sortDowncall = MethodHandles.insertArguments(linker.downcallHandle(
                lib.find("ptr_sort_unstable").orElseThrow(),
                // void vec_sort_unstable(vec, function pointer);
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
        ), 0, this.vecPtr, size);

        this.cmpDesc = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG
        );
        this.cmpHandle = MethodHandles.lookup()
            .findVirtual(LongComparator.class, "compare", cmpDesc.toMethodType());
    }

    public void set(long index, long value) {
        this.vecPtr.setAtIndex(ValueLayout.JAVA_LONG, index, value);
    }

    public long get(long index) {
        return this.vecPtr.getAtIndex(ValueLayout.JAVA_LONG, index);
    }

    @FunctionalInterface
    public interface LongComparator {
        int compare(long a, long b);
    }

    public void sort(LongComparator comparator) {
        MethodHandle cmpHandle = this.cmpHandle.bindTo(comparator);
        MemorySegment cmpFp = Linker.nativeLinker().upcallStub( cmpHandle, this.cmpDesc, this.arena );
        try {
            this.sortDowncall.invoke(cmpFp);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        this.arena.close();
    }
}
