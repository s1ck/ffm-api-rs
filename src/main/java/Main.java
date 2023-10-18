public class Main {

    public static final NativeLib NATIVE_LIB = NativeLib.of("leet-rs");

    public static void main(String[] args) throws Throwable {
        // Foreign memory demos
        MemorySegments.directBuffer();
        MemorySegments.memorySegments();
        MemorySegments.confinedArena();
        MemorySegments.memoryLayout();

        // Foreign Functions demos
        ForeignFunctions.downcallStruct();
        ForeignFunctions.downcallPrintString();
        ForeignFunctions.downcallEditString();
        ForeignFunctions.downcallExternalAllocation();
        ForeignFunctions.downcall();
        ForeignFunctions.staticUpcall();
        ForeignFunctions.virtualUpcall();
        ForeignFunctions.vec();
        ForeignFunctions.upcallManhattan();
    }
}
