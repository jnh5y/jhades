package org.jhades.standalone;

import com.github.zafarkhaja.semver.Version;
import org.jhades.model.*;
import org.jhades.reports.DuplicatesReport;
import org.jhades.service.ClasspathScanner;
import org.jhades.service.ClasspathScannerListener;
import org.jhades.utils.FileUtils;
import org.jhades.utils.StdOutLogger;
import org.jhades.utils.ZipUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.FileVisitResult.CONTINUE;

public class JHadesStandaloneReport {

    private static final StdOutLogger logger = StdOutLogger.getLogger();
    private ClasspathScanner scanner = new ClasspathScanner();
    private static final Pattern JAR_NAME = Pattern.compile("^.*/(.*jar)$");
    private static final String SEP = System.getProperty("file.separator");
    private final String warFilePath;
    private final String tmpPath;

    public JHadesStandaloneReport(String warFilePath, String tmpPath) {
        this.warFilePath = warFilePath;
        this.tmpPath = tmpPath;
    }

    public static void printUsage() {
        System.out.println("\njHades standalone war scanner utility - the following arguments are needed:\n");
        System.out.println("    warFilePath - the path to your war file");
        System.out.println("    tmpPath (optional) - the path to a temporary directory, needed to unzip files");
        System.out.println();
        System.out.println("Options:");
        System.out.println();
        System.out.println("    -Ddetail=true -> displays classes with duplicates and their locations");
        System.out.println("    -Dexclude.same.size.dups=true -> don't count as classpath duplicates the classes that have multiple class files, but they all have the same size");
        System.out.println("    -Dsearch.by.file.name=\"<search regex>\" -> searches the WAR for a resource file using a Java regular expression");
        System.out.println();
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        if (args.length == 0 || args.length > 2) {
            printUsage();
            System.exit(-1);
        }

        logger.setDebug(false);

        String warFilePath = args[0];

        String tmpPath;
        if (args.length == 2) {
            tmpPath = args[1];
        } else {
            tmpPath = System.getProperty("java.io.tmpdir") + "/jhades";
            Files.createDirectories(Paths.get(tmpPath));
        }

        logger.info("warFilePath/directory to analyze = " + warFilePath);
        logger.info("tmpPath = " + tmpPath);

        JHadesStandaloneReport warScanner = new JHadesStandaloneReport(warFilePath, tmpPath);

        warScanner.scan();

    }

    public void scan() throws IOException, URISyntaxException {
        if(isDir()) {
            scanDir();
        } else if(isWar()) {
            scanWar();
        } else {
            throw new IllegalArgumentException("Can only scan wars and jar dirs.");
        }
    }

    private boolean isWar() {
        return warFilePath.endsWith(".war");
    }

    private boolean isDir() {
        return FileSystems.getDefault().getPath(warFilePath).toFile().isDirectory();
    }

    private void scanDir() throws IOException, URISyntaxException {
        Path start = Paths.get(warFilePath);
        final List<ClasspathEntry> classpathEntries = new ArrayList<>();

        /* NPHAIR - Walk through  every jar in the web-inf/lib and add that to our files to scan. */
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String filePath = file.toString();
                if(filePath.endsWith(".jar")) {
                    Matcher matcher = JAR_NAME.matcher(filePath);
                    if(matcher.matches()) {
                        updateStatus("Processing jar " + matcher.group(1));
                    }
                    logger.debug("Adding jar: " + filePath);

                    filePath = "file:///" + filePath.replaceAll("\\\\", "/");
                    logger.debug("jar URL: " + filePath);
                    classpathEntries.add(new ClasspathEntry(null, filePath));
                }
                return CONTINUE;
            }
        });

        /* NPHAIR - build a scanner listener that just notifies on start and finish of a jar being scanned. */
        ClasspathScannerListener listener = (new ClasspathScannerListener() {
            @Override
            public void onEntryScanStart(ClasspathEntry entry) {
                String filePath = entry.getUrl().toString();
                Matcher matcher = JAR_NAME.matcher(filePath);
                if (matcher.matches()) {
                    updateStatus("Processing jar " + matcher.group(1));
                }
            }

            @Override
            public void onEntryScanEnd(ClasspathEntry entry) {
                String filePath = entry.getUrl().toString();
                Matcher matcher = JAR_NAME.matcher(filePath);
                if (matcher.matches()) {
                    updateStatus("Finished processing jar " + matcher.group(1));
                }
            }
        });

        List<ClasspathResource> classpathResources = ClasspathEntries.findClasspathResourcesInEntries(classpathEntries, logger, listener);

        processClasspathResources(classpathResources);
    }

    private void scanWar() throws IOException, URISyntaxException {
        logger.debug("Extracting war " + warFilePath + "...");

        updateStatus("Deleting temporary directory");
        FileUtils.deleteDirectory(tmpPath);

        /* NPHAIR - this just prints the names of jars as they are unzipped */
        updateStatus("Unziping WAR");
        ZipUtils.unzip(warFilePath, tmpPath, new ZipUtils.UnzipProgressListener() {
            @Override
            public void onBeginFileExtract(String fileName) {
                Matcher matcher = JAR_NAME.matcher(fileName);
                if (matcher.matches()) {
                    updateStatus("Extracting jar " + matcher.group(1));
                }
            }
        });

        /* NPHAIR - Now that we have unziped the war into the temp directory, add the web-inf/classes folder to our entries to scan. */
        final List<ClasspathEntry> classpathEntries = new ArrayList<>();

        // add classes folder
        String classesFolderPath = tmpPath + SEP + "WEB-INF" + SEP + "classes";
        Path classesFolder = Paths.get(classesFolderPath);
        if (Files.exists(classesFolder)) {
            classpathEntries.add(new ClasspathEntry(null, classesFolderPath));
        }

        Path start = Paths.get(tmpPath);

        updateStatus("Scanning WAR");

        /* NPHAIR - Walk through  every jar in the web-inf/lib and add that to our files to scan. */
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String filePath = file.toString();
                if (filePath.contains("WEB-INF" + SEP + "lib") && filePath.endsWith(".jar")) {
                    Matcher matcher = JAR_NAME.matcher(filePath);
                    if (matcher.matches()) {
                        updateStatus("Processing jar " + matcher.group(1));
                    }
                    logger.debug("Adding jar: " + filePath);

                    filePath = "file:///" + filePath.replaceAll("\\\\", "/");
                    logger.debug("jar URL: " + filePath);
                    classpathEntries.add(new ClasspathEntry(null, filePath));
                }
                return CONTINUE;
            }
        });

        /* NPHAIR - build a scanner listener that just notifies on start and finish of a jar being scanned. */
        ClasspathScannerListener listener = (new ClasspathScannerListener() {
            @Override
            public void onEntryScanStart(ClasspathEntry entry) {
                String filePath = entry.getUrl().toString();
                Matcher matcher = JAR_NAME.matcher(filePath);
                if (matcher.matches()) {
                    updateStatus("Processing jar " + matcher.group(1));
                }
            }

            @Override
            public void onEntryScanEnd(ClasspathEntry entry) {
                String filePath = entry.getUrl().toString();
                Matcher matcher = JAR_NAME.matcher(filePath);
                if (matcher.matches()) {
                    updateStatus("Finished processing jar " + matcher.group(1));
                }
            }
        });

        List<ClasspathResource> classpathResources = ClasspathEntries.findClasspathResourcesInEntries(classpathEntries, logger, listener);

        processClasspathResources(classpathResources);
    }

    private void processClasspathResources(List<ClasspathResource> classpathResources) throws IOException, URISyntaxException {

        boolean isDetailedMode = "true".equals(System.getProperty("detail"));
        boolean isExcludeSameSizeDups = "true".equals(System.getProperty("exclude.same.size.dups"));

        List<JarPair> overlapReportLines = scanner.findOverlappingJars(classpathResources, isExcludeSameSizeDups);

        long totalDupClasses = 0;

        System.out.println("\n>>>> Jar overlap report: \n");

        for (JarPair jarOverlapReportLine : overlapReportLines) {
            // Calculate percent overlap
            int count1 = jarOverlapReportLine.getJar1().getResourceVersions().size();
            int count2 = jarOverlapReportLine.getJar2().getResourceVersions().size();
            Long duplicateCount = jarOverlapReportLine.getDupClassesTotal();

            String reportLine = getJarName(jarOverlapReportLine.getJar1().getUrl()) + " overlaps with "
                    + getJarName(jarOverlapReportLine.getJar2().getUrl())
                    + " - total overlapping classes: " + jarOverlapReportLine.getDupClassesTotal()
                    + " (percent overlap: "  + String.format("%.2f",jarOverlapReportLine.percentOverlap()) + ")";
            System.out.println(reportLine);
            totalDupClasses += jarOverlapReportLine.getDupClassesTotal();

            reportDuplicateNameJars(jarOverlapReportLine);
        }

        System.out.println("\nTotal number of classes with more than one version: " + totalDupClasses + "\n");

        if (!isExcludeSameSizeDups) {
            System.out.println("\nUse -Dexclude.same.size.dups=true for considering as a duplicate only classes with multiple class files of different sizes.\n");
        }


        if (isDetailedMode) {
            List<ClasspathResource> resourcesWithDifferentSizeDups = scanner.findClassFileDuplicates(classpathResources, isExcludeSameSizeDups);
            DuplicatesReport report = new DuplicatesReport(resourcesWithDifferentSizeDups, new StandaloneReportUrlFormatter());
            report.print();
        }

        // NPHAIR - don't care about this yet ...
        String searchByFileName = System.getProperty("search.by.file.name");

        if (searchByFileName != null) {
            List<ClasspathResource> searchResults = scanner.findByRegex(searchByFileName);
            if (searchResults != null && !searchResults.isEmpty()) {
                System.out.println("\nSearch results using regular expression: " + searchByFileName + "\n");
                for (ClasspathResource match : searchResults) {
                    System.out.println(match.getName() + "\n");
                    for (ClasspathResourceVersion version : match.getResourceFileVersions()) {
                        System.out.println("    " + version.getClasspathEntry().getUrl());
                    }
                    System.out.println("");
                }
            }
        }

    }

    private void reportDuplicateNameJars(JarPair jarOverlapReportLine) throws IOException, URISyntaxException {
        String jar1 = getJarName(jarOverlapReportLine.getJar1().getUrl());
        int split1 = jar1.lastIndexOf("-");
        String artifact1 = jar1.substring(0, split1);
        String version1  = jar1.substring(split1+1, jar1.length()-4);

        String jar2 = getJarName(jarOverlapReportLine.getJar2().getUrl());
        int split2 = jar2.lastIndexOf("-");
        String artifact2 = jar2.substring(0, split2);
        String version2  = jar2.substring(split2+1, jar2.length()-4);

        if (artifact1.equals(artifact2)) {
            System.out.println("** WARNING: Possible duplicate jars: " + jar1 + " " + jar2);

            reportOlderVersion(jar1, version1, jar2, version2);
        }
//
//        int count1 = jarOverlapReportLine.getJar1().getResourceVersions().size();
//        int count2 = jarOverlapReportLine.getJar2().getResourceVersions().size();
//        Long duplicateCount = jarOverlapReportLine.getDupClassesTotal();
//        System.out.println(jar1 + " has " + count1 + " classes.  " + jar2 + " has " + count2 + " classes.  Duplicate Count: " + duplicateCount);
    }

    private void reportOlderVersion(String jar1, String version1, String jar2, String version2) {
        try {
            String olderVersion = "";
            if (Version.valueOf(version1).lessThan(Version.valueOf(version2))) {
                olderVersion = jar1;
            } else {
                olderVersion = jar2;
            }
            System.out.println("** WARNING: Consider removing the older version: " + olderVersion);
        } catch (Exception e) {
            System.out.println("** WARNING: Could not determine which jar is older.  Version numbering may not follow SemVer.");
        }
    }

    private String getJarName(String url) {
        String jarName = "";
        if (url != null) {
            Matcher matcher = JAR_NAME.matcher(url);
            if (matcher.matches()) {
                jarName = matcher.group(1);
            }
        }

        return jarName;
    }

    protected void updateStatus(String statusUpdate) {
        System.out.println(statusUpdate);
    }
}
