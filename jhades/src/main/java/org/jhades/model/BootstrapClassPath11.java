package org.jhades.model;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.lang.module.*;

public final class BootstrapClassPath11 {

    private BootstrapClassPath11() {}

    /**
     * Return a list of URLs that approximate the old bootstrap class path.
     * For Java 9+ this includes:
     *  - module locations from the system/module runtime image (ModuleFinder.ofSystem)
     *  - any URLs exposed by the platform class loader (if available)
     */
    {}

    /**
     * Return an array of URLs that approximate the old bootstrap class path.
     * For Java 9+ this includes:
     *  - module locations from the system/module runtime image (ModuleFinder.ofSystem)
     *  - any entries exposed by the platform class loader when it is a URLClassLoader
     */
    public static URL[] getBootstrapUrls() throws IOException {
        List<URL> urls = new ArrayList<>();

        ModuleFinder finder = ModuleFinder.ofSystem();
        for (ModuleReference mr : finder.findAll()) {
            mr.location().ifPresent(loc -> {
                try {
                    urls.add(loc.toURL());
                } catch (Exception ignored) {
                }
            });
        }

        try {
            FileSystem jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path modulesRoot = jrtFs.getPath("/modules");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modulesRoot)) {
                for (Path p : stream) {
                    try {
                        URI modUri = URI.create("jrt:/modules/" + p.getFileName().toString());
                        urls.add(modUri.toURL());
                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException ignored) {
            }
        } catch (FileSystemNotFoundException | ProviderNotFoundException ignored) {
        }

        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        if (platform instanceof java.net.URLClassLoader) {
            for (URL u : ((java.net.URLClassLoader) platform).getURLs()) {
                urls.add(u);
            }
        }

        // Deduplicate while preserving order and return as an array
        List<URL> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (URL u : urls) {
            String s = u.toString();
            if (seen.add(s)) deduped.add(u);
        }
        return deduped.toArray(new URL[0]);
    }

    // Simple demo main
    public static void main(String[] args) throws Exception {
        URL[] bs = getBootstrapUrls();
        System.out.println("Bootstrap-like entries (" + bs.length + "):");
        for (URL u : bs) System.out.println("  " + u);
    }
}
