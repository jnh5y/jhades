package org.jhades.model;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class JrtClasses {

    private JrtClasses() {}

    public static List<String> listAllClasses() throws IOException {
        try (FileSystem jrt = FileSystems.newFileSystem(URI.create("jrt:/"), java.util.Collections.emptyMap())) {
            Path modules = jrt.getPath("/modules");
            List<String> classes = new ArrayList<>();
            try (DirectoryStream<Path> mods = Files.newDirectoryStream(modules)) {
                for (Path mod : mods) {
                    Path modRoot = modules.resolve(mod.getFileName().toString());
                    classes.addAll(listClassesUnderModule(modRoot));
                }
            }
            return classes;
        }
    }

    private static List<String> listClassesUnderModule(Path moduleRoot) throws IOException {
        List<String> result = new ArrayList<>();
        if (!Files.exists(moduleRoot)) return result;


        try (Stream<Path> walk = Files.walk(moduleRoot)) {
            walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".class"))
                    .forEach(p -> {
                        Path rel = moduleRoot.relativize(p);
                        String unix = rel.toString().replace('\\', '/');
                        if (unix.startsWith("classes/")) unix = unix.substring("classes/".length());
                        if (unix.endsWith(".class")) {
                            String cls = unix.substring(0, unix.length() - ".class".length()).replace('/', '.');
                            result.add(cls);
                        }
                    });
        }
        System.out.println("Looking in path: " + moduleRoot + " found " + result.size() + " results.");
        return result;
    }

    public static void main(String[] args) throws Exception {
        List<String> c = listAllClasses();
        System.out.println("Total classes found: " + c.size());
        System.out.println("Contains java.lang.String: " + c.contains("java.lang.String"));
        // Example direct read of java.lang.String bytes
        try (FileSystem jrt = FileSystems.newFileSystem(URI.create("jrt:/"), java.util.Collections.emptyMap())) {
            Path p = jrt.getPath("/modules", "java.base", "java", "lang", "String.class");
            System.out.println("String.class exists: " + Files.exists(p) + ", bytes: " + Files.readAllBytes(p).length);
        }
    }
}
