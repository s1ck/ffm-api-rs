import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

public class ForeignFunctions {

    static void downcallStruct() throws Throwable {
        // struct Point {
        //      long x;
        //      long y;
        // }
        var pointLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("x"),
            ValueLayout.JAVA_LONG.withName("y")
        );
        var xHandle = pointLayout.varHandle(groupElement("x"));
        var yHandle = pointLayout.varHandle(groupElement("y"));

        var linker = Linker.nativeLinker();

        try (Arena arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);

            MemorySegment nativeFunc = lib.find("Point_manhattan").orElseThrow();
            FunctionDescriptor nativeFuncDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, pointLayout, pointLayout);
            MethodHandle handle = linker.downcallHandle(nativeFunc, nativeFuncDesc);

            var point1 = arena.allocate(pointLayout);
            xHandle.set(point1, 0L, 42L);
            yHandle.set(point1, 0L, 1337L);

            var point2 = arena.allocate(pointLayout);
            xHandle.set(point2, 0L, 1337L);
            yHandle.set(point2, 0L, 1338L);

            var distance = (long) handle.invoke(point1, point2);

            if (distance == -1) {
                var lastError = lib.find("last_error").orElseThrow();
                var lastErrorHandle = linker.downcallHandle(lastError, FunctionDescriptor.of(ValueLayout.ADDRESS));

                var dropError = lib.find("drop_error").orElseThrow();
                var dropErrorHandle = linker.downcallHandle(dropError, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

                var errorPtr = (MemorySegment) lastErrorHandle.invoke();
                var error = errorPtr.reinterpret(Integer.MAX_VALUE).getString(0);

                dropErrorHandle.invoke(errorPtr);

                throw new IllegalArgumentException(error);
            }

            System.out.println("manhattan distance = " + distance);
        }
    }

    static void downcallPrintString() throws Throwable {
        var linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);
            var nativeFunc = lib.find("print_string").orElseThrow();
            var downcall = linker.downcallHandle(nativeFunc, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

            var string = arena.allocateFrom("Java <3 Rust");
            System.out.println("string = " + string);
            downcall.invoke(string);
        }
    }

    static void downcallEditString() throws Throwable {
        var linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);
            var nativeFunc = lib.find("edit_string").orElseThrow();
            var nativeFuncDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
            var downcall = linker.downcallHandle(nativeFunc, nativeFuncDesc);

            var str = "Java <3 Rust";
            var string = arena.allocateFrom(str);
            downcall.invoke(string, str.length());
            System.out.println("str = " + string.getString(0));
        }
    }

    static void downcallExternalAllocation() throws Throwable {
        // struct Point {
        //      long x;
        //      long y;
        // }
        var pointLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("x"),
            ValueLayout.JAVA_LONG.withName("y")
        );
        var xHandle = pointLayout.varHandle(groupElement("x"));
        var yHandle = pointLayout.varHandle(groupElement("y"));

        var linker = Linker.nativeLinker();

        try (Arena arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);

            var newFunc = lib.find("Point_new").orElseThrow();
            var newFuncDesc = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
            var newHandle = linker.downcallHandle(newFunc, newFuncDesc);

            var dropFunc = lib.find("Point_drop").orElseThrow();
            var dropFuncDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
            var dropHandle = linker.downcallHandle(dropFunc, dropFuncDesc);

            var ptr = (MemorySegment) newHandle.invoke(42L, 1337L);

            ptr = ptr.reinterpret(pointLayout.byteSize());

            System.out.println("x = " + xHandle.get(ptr, 0L));
            System.out.println("y = " + yHandle.get(ptr, 0L));

            dropHandle.invoke(ptr);
        }
    }

    static void downcall() throws Throwable {
        var linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);
            var nativeFunc = lib.find("leet").orElseThrow();
            var nativeFuncDesc = FunctionDescriptor.of(ValueLayout.JAVA_INT);
            var downcall = linker.downcallHandle(nativeFunc, nativeFuncDesc);
            var res = (int) downcall.invoke();
            System.out.println("res = " + res);
        }
    }

    static long add(long a, long b) {
        System.out.println("adding " + a + " and " + b);
        return a + b;
    }

    static void staticUpcall() throws Throwable {
        var linker = Linker.nativeLinker();
        try (var arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);
            var nativeFunc = lib.find("callback").orElseThrow();
            var staticFunc = MethodHandles.lookup().findStatic(
                ForeignFunctions.class,
                "add",
                MethodType.methodType(long.class, long.class, long.class)
            );
            var staticFuncDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG
            );
            var upcall = linker.upcallStub(
                staticFunc,
                staticFuncDesc,
                arena
            );
            var downcall = linker.downcallHandle(
                nativeFunc,
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
            );
            var res = (long) downcall.invoke(upcall);
            System.out.println("res = " + res);
        }
    }

    @FunctionalInterface
    interface Callback {
        long call(long a, long b);
    }

    static void virtualUpcall() throws Throwable {
        var linker = Linker.nativeLinker();
        try (var arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);
            var nativeFunc = lib.find("callback").orElseThrow();
            var virtualFunc = MethodHandles.lookup()
                .findVirtual(Callback.class, "call", MethodType.methodType(long.class, long.class, long.class))
                .bindTo((Callback) ForeignFunctions::add);
            var upcall = linker.upcallStub(
                virtualFunc,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG
                ),
                arena
            );
            var nativeFuncDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
            var downcall = linker.downcallHandle(nativeFunc, nativeFuncDesc);
            var res = (long) downcall.invoke(upcall);
            System.out.println("res = " + res);
        }
    }

    interface Operation {
        long operate(MemorySegment p1, MemorySegment p2);
    }

    static void upcallManhattan() throws Throwable {
        // struct Point {
        //      long x;
        //      long y;
        // }
        var pointLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("x"),
            ValueLayout.JAVA_LONG.withName("y")
        );
        var xHandle = pointLayout.varHandle(groupElement("x"));
        var yHandle = pointLayout.varHandle(groupElement("y"));

        var linker = Linker.nativeLinker();

        try (Arena arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);

            FunctionDescriptor nativeFuncDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG, pointLayout, pointLayout);
            var virtualFunc = MethodHandles.lookup()
                .findVirtual(
                    Operation.class,
                    "operate",
                    MethodType.methodType(long.class, MemorySegment.class, MemorySegment.class)
                )
                .bindTo((Operation) (p1, p2) -> {
                    var x1 = (long) xHandle.get(p1, 0L);
                    var y1 = (long) yHandle.get(p1, 0L);
                    var x2 = (long) xHandle.get(p2, 0L);
                    var y2 = (long) yHandle.get(p2, 0L);
//                    throw  new IllegalArgumentException("test");
                    return Math.abs(x1 - x2) + Math.abs(y1 - y2);
                });
            virtualFunc = wrapExceptionHandler(virtualFunc);
            var upcall = linker.upcallStub(virtualFunc, nativeFuncDesc, arena);

            MemorySegment nativeFunc = lib.find("Point_operate").orElseThrow();
            var downcall = linker.downcallHandle(
                nativeFunc,
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
            );
            var distance = (long) downcall.invoke(upcall);
            System.out.println("manhattan distance = " + distance);
        }
    }

    static MethodHandle wrapExceptionHandler(MethodHandle target) throws NoSuchMethodException, IllegalAccessException {
        var handler = MethodHandles.lookup().findStatic(
            ForeignFunctions.class,
            "handleException",
            MethodType.methodType(long.class, Throwable.class)
        );
        return MethodHandles.catchException(target, Throwable.class, handler);
    }

    static long handleException(Throwable e) {
        System.out.println(STR."Exception: \{e.getMessage()}");
        return -1L;
    }

    static void vec() throws Throwable {
        var linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);
            var f_new = lib.find("vec_new").orElseThrow();
            var downcall_new = linker.downcallHandle(f_new, FunctionDescriptor.of(ValueLayout.ADDRESS));
            var vec = (MemorySegment) downcall_new.invoke();

            var f_push = lib.find("vec_push").orElseThrow();
            var downcall_push = linker.downcallHandle(f_push, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            downcall_push.invoke(vec);

            var f_print = lib.find("vec_print").orElseThrow();
            var downcall_print = linker.downcallHandle(f_print, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            downcall_print.invoke(vec);

            var f_drop = lib.find("vec_drop").orElseThrow();
            var downcall_drop = linker.downcallHandle(f_drop, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            downcall_drop.invoke(vec);
        }
    }

}
