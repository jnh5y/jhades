package org.jhades.model;

//import jdk.internal.loader.URLClassPath;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.jhades.service.ClasspathScanner.BOOTSTRAP_CLASS_LOADER;
//import sun.misc.Launcher;
//import sun.misc.URLClassPath;

/**
 *
 * Factory class that identifies a class loader and builds the corresponding object model.
 *
 * Class loaders should be detected via reflection to avoid code dependencies towards specific implementations.
 *
 * For the moment only the Url classloader is supported, this already covers jetty, tomcat, jboss and standalone
 * applications.
 *
 */
public class ClazzLoaderFactory {

    public static ClazzLoader createClazzLoader(ClassLoader classLoader) {
        ClazzLoader cl = null;
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
            if (urlClassLoader.getURLs() != null) {
                cl = new UrlClazzLoader(classLoader.getClass().getName(), classLoader.toString(), urlClassLoader.getURLs());
            }
        } else {
            URL[] urls = ClassLoaderUrls.urlsFor(classLoader);
            for (URL url : urls) {
                System.out.println(classLoader + " " + url);
            }
            cl = new UrlClazzLoader(classLoader.getClass().getName(), classLoader.toString(), urls);
            System.out.println("WARNING: this classloader is not supported: " + classLoader.getClass().getName());
        }
        return cl;
    }

    public static ClazzLoader createBootstrapClassLoader() {
//        //URLClassPath cp = Launcher.getBootstrapClassPath();
//        ClassLoader platform = ClassLoader.getPlatformClassLoader();
//        return new UrlClazzLoader(BOOTSTRAP_CLASS_LOADER, "N/A", null); //cp.getURLs());
        //URLClassPath cp = Launcher.getBootstrapClassPath();
        URL[] urls = new URL[0];
        try {
            urls = BootstrapClassPath11.getBootstrapUrls();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new UrlClazzLoader(BOOTSTRAP_CLASS_LOADER, "N/A", urls);
    }
}
