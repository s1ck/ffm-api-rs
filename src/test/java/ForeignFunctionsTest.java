import org.junit.jupiter.api.Test;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

class ForeignFunctionsTest {

    @Test
    void printThreadId() throws InterruptedException {
        var linker = Linker.nativeLinker();
        var concurrency = 10;
        var pool = Executors.newFixedThreadPool(concurrency);
        var arena = Arena.ofAuto();
        var lib = SymbolLookup.libraryLookup(Main.NATIVE_LIB.path(), arena);

        for (int i = 0; i < concurrency; i++) {
            pool.submit(() -> {
                try {
                    MemorySegment nativeFunc = lib.find("print_thread_id").orElseThrow();
                    FunctionDescriptor nativeFuncDesc = FunctionDescriptor.ofVoid();
                    MethodHandle handle = linker.downcallHandle(nativeFunc, nativeFuncDesc);
                    handle.invoke();
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            });
        }

        pool.awaitTermination(1, SECONDS);
    }
}
