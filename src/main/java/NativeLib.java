import java.nio.file.Path;

public class NativeLib {

    public static NativeLib of(String name) {
        return new NativeLib(name, Mode.Release);
    }

    public static NativeLib of(String name, Mode mode) {
        return new NativeLib(name, mode);
    }

    public enum Mode {
        Debug,
        Release,
    }

    private final Path libPath;

    private NativeLib(String name, Mode mode) {
        this.libPath = Path.of(
                "rust",
                name,
                "target",
                mode.name().toLowerCase(),
                dll(name)
        );
    }

    public Path path() {
        return libPath;
    }

    private static String dll(String name) {
        var os = System.getProperty("os.name").toLowerCase();
        var normalizedName = name.replaceAll("-", "_");

        if (os.contains("win")) {
            return normalizedName + ".dll";
        }
        if (os.contains("mac")) {
            return "lib" + normalizedName + ".dylib";
        }
        if (os.contains("linux")) {
            return "lib" + normalizedName + ".so";
        }
        throw new IllegalArgumentException("Unsupported OS: " + os);
    }
}
