package org.jhades.model;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class JrtClassLister {

    private JrtClassLister() {}

    /**
     * Given a jrt URI string that is one of:
     *  - "jrt:/"                       -> list classes from all modules
     *  - "jrt:/modules/<module>"       -> list classes in that module
     *  - "jrt:/modules/<module>/<path>"-> list classes under that module path
     *
     * Returns fully qualified binary class names (e.g. java.lang.Object or
     * com.example.MyClass$InnerClass) for all .class files found.
     */
    public static List<String> listClassesFromJrt(URI uri) throws IOException {
        //URI uri = URI.create(jrtUri);
        try (FileSystem jrtFs = FileSystems.newFileSystem(new URI("jrt:/"), java.util.Collections.emptyMap())) {
            Path given = jrtFs.getPath(uri.getPath());
            if (given == null) given = jrtFs.getPath("/");

            List<String> classes = new ArrayList<>();

            // If given path is root or /modules, enumerate every module
            if (isRootOrModules(given)) {
                Path modulesRoot = jrtFs.getPath("/modules");
                try (DirectoryStream<Path> mods = Files.newDirectoryStream(modulesRoot)) {
                    for (Path mod : mods) {
                        Path modRoot = modulesRoot.resolve(mod.getFileName().toString());
                        classes.addAll(listClassesUnderModule(modRoot));
                    }
                }
                return classes;
            }

            // If given path starts with /modules/<module>, treat as module-root or subpath
            if (isUnderModules(given)) {
                // find module root: /modules/<module>
                Path modulesRoot = jrtFs.getPath("/modules");
                Path moduleName = modulesRoot.relativize(getModuleRootPath(given, modulesRoot));
                Path moduleRoot = modulesRoot.resolve(moduleName);
                // if given points at module root exactly, list entire module
                if (given.equals(moduleRoot)) {
                    classes.addAll(listClassesUnderModule(moduleRoot));
                } else {
                    // given is a subpath under the module, list under that subpath
                    classes.addAll(listClassesUnder(given, moduleRoot));
                }
                return classes;
            }

            // Otherwise treat given as an arbitrary path in the jrt fs and walk from there,
            // attempting to convert found .class files into binary names relative to the nearest module root.
            // Find nearest modules root parent if possible.
            Path modulesRoot = jrtFs.getPath("/modules");
            if (given.startsWith(modulesRoot)) {
                // find the module root for this path and delegate
                Path moduleRoot = getModuleRootPath(given, modulesRoot);
                if (Files.exists(moduleRoot)) {
                    if (given.equals(moduleRoot)) {
                        classes.addAll(listClassesUnderModule(moduleRoot));
                    } else {
                        classes.addAll(listClassesUnder(given, moduleRoot));
                    }
                    return classes;
                }
            }

            // Fallback: walk the given path and produce dotted names from path segments under start
            classes.addAll(listClassesUnderFallback(given));
            return classes;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // List all classes under a module root (/modules/<module>)
    private static List<String> listClassesUnderModule(Path moduleRoot) throws IOException {
        return listClassesUnder(moduleRoot, moduleRoot);
    }

    // Walks start and converts .class files into binary names relative to moduleRoot.
    private static List<String> listClassesUnder(Path start, Path moduleRoot) throws IOException {
        List<String> result = new ArrayList<>();
        if (!Files.exists(start)) return result;

        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                    .forEach(p -> {
                        Path rel = moduleRoot.relativize(p);
                        String unixPath = rel.toString().replace('\\', '/');
                        // If classes are buried under "classes/" or similar directories inside module, strip leading segments
                        // We only care about entries that look like <package>/.../Name.class
                        int idx = unixPath.indexOf('/');
                        // If the first segment is "package" (no special handling required) leave as is.
                        // Remove any leading "classes/" or "jmod/" if present (common jmod layouts)
                        if (unixPath.startsWith("classes/")) {
                            unixPath = unixPath.substring("classes/".length());
                        } else if (unixPath.startsWith("jmod/")) {
                            unixPath = unixPath.substring("jmod/".length());
                        }
                        if (unixPath.endsWith(".class")) {
                            String classPath = unixPath.substring(0, unixPath.length() - ".class".length());
                            String className = classPath.replace('/', '.');
                            result.add(className);
                        }
                    });
        }
        return result;
    }

    // Fallback that relativizes against start itself (used when module root is unknown)
    private static List<String> listClassesUnderFallback(Path start) throws IOException {
        List<String> result = new ArrayList<>();
        if (!Files.exists(start)) return result;

        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                    .forEach(p -> {
                        Path rel = start.relativize(p);
                        String unixPath = rel.toString().replace('\\', '/');
                        if (unixPath.endsWith(".class")) {
                            String classPath = unixPath.substring(0, unixPath.length() - ".class".length());
                            String className = classPath.replace('/', '.');
                            result.add(className);
                        }
                    });
        }
        return result;
    }

    // Helpers

    private static boolean isRootOrModules(Path p) {
        String s = normalize(p);
        return "/".equals(s) || "/modules".equals(s) || "".equals(s);
    }

    private static boolean isUnderModules(Path p) {
        String s = normalize(p);
        return s.startsWith("/modules/");
    }

    // Given a path under /modules, return /modules/<module> (module root)
    private static Path getModuleRootPath(Path p, Path modulesRoot) {
        Path rel = modulesRoot.relativize(p);
        if (rel.getNameCount() == 0) return modulesRoot; // should not happen
        Path moduleName = rel.getName(0);
        return modulesRoot.resolve(moduleName);
    }

    private static String normalize(Path p) {
        String s = p.toString().replace('\\', '/');
        if (!s.startsWith("/")) s = "/" + s;
        return s;
    }

    // Demo
    public static void main(String[] args) throws Exception {
        String target = (args.length == 0) ? "jrt:/" : args[0];
        List<String> classes = listClassesFromJrt(new URI(target));
        System.out.println("Found " + classes.size() + " classes for " + target);
        for (String c : classes) System.out.println(c);
    }
}
