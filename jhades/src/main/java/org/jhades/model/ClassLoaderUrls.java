package org.jhades.model;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.*;

public final class ClassLoaderUrls {

    private ClassLoaderUrls() {}

    /**
     * Return URLs that approximate the class/resource locations visible to the given loader.
     * This is best-effort and will attempt multiple strategies:
     *  - If loader is a URLClassLoader use getURLs()
     *  - If loader is the platform/system loader, include java.class.path entries
     *  - Add any module/jrt entries visible via the jrt filesystem (if present)
     *  - Enumerate top-level resources "" via loader.getResources("") as a last resort
     */
    public static URL[] urlsFor(ClassLoader loader) {
        if (loader == null) loader = ClassLoader.getSystemClassLoader();

        List<URL> urls = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. If it's a URLClassLoader, grab its URLs directly
        if (loader instanceof URLClassLoader) {
            for (URL u : ((URLClassLoader) loader).getURLs()) {
                addUnique(urls, seen, u);
            }
        }

        // 2. If loader is system or platform loader, include java.class.path entries
        //    This helps when loaders don't expose URLs (modules or encapsulated loaders)
        if (loader == ClassLoader.getSystemClassLoader() || loader == ClassLoader.getPlatformClassLoader()) {
            String cp = System.getProperty("java.class.path", "");
            if (!cp.isEmpty()) {
                for (String part : cp.split(File.pathSeparator)) {
                    try {
                        URL u = Paths.get(part).toUri().toURL();
                        addUnique(urls, seen, u);
                    } catch (Exception ignored) {}
                }
            }
        }

        // 3. Try jrt filesystem modules (Java 9+). Add jrt module URIs if present
        try {
            FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path modules = jrt.getPath("/modules");
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(modules)) {
                for (Path m : ds) {
                    try {
                        URI modUri = URI.create("jrt:/modules/" + m.getFileName().toString());
                        addUnique(urls, seen, modUri.toURL());
                    } catch (Exception ignored) {}
                }
            } catch (IOException ignored) {}
        } catch (ProviderNotFoundException | FileSystemNotFoundException ignored) {}

        // 4. Enumerate top-level resources via loader as a fallback
        try {
            Enumeration<URL> roots = loader.getResources("");
            while (roots.hasMoreElements()) {
                addUnique(urls, seen, roots.nextElement());
            }
        } catch (IOException ignored) {}

        // 5. Last resort: if still empty, try to derive from the loader's class location
        if (urls.isEmpty()) {
            try {
                ProtectionDomain pd = loader.getClass().getProtectionDomain();
                if (pd != null && pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
                    addUnique(urls, seen, pd.getCodeSource().getLocation());
                }
            } catch (Exception ignored) {}
        }

        return urls.toArray(new URL[0]);
    }

    private static void addUnique(List<URL> dest, Set<String> seen, URL u) {
        if (u == null) return;
        String s = u.toString();
        if (seen.add(s)) dest.add(u);
    }

    // Demo main
    public static void main(String[] args) throws Exception {
        ClassLoader cl = (args.length > 0 && "platform".equals(args[0]))
                ? ClassLoader.getPlatformClassLoader()
                : Thread.currentThread().getContextClassLoader();

        URL[] urls = urlsFor(cl);
        System.out.printf("Found %d URLs for loader %s%n", urls.length, cl);
        for (URL u : urls) System.out.println("  " + u);
    }
}
