package me.deecaad.core.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * This final utility class outlines static methods that work with files,
 * resources, and streams.
 */
public final class FileUtil {

    // Don't let anyone instantiate this class
    private FileUtil() {
    }

    /**
     * Copies the resource defined by <code>resource</code> in the project's
     * resource folder to an <code>output</code> directory. If the copied
     * resource is a folder (If the file name does not contain a '.'), then
     * this method will be called recursively until all subdirectories are
     * copied.
     *
     * @param clazz    The non-null loading plugin class.
     * @param loader   The non-null loading plugin's class loader. Use
     *                 {@link JavaPlugin#getClassLoader()}.
     * @param resource The non-null name of the resource to copy.
     * @param output   The non-null directory. It is important that
     *                 {@link File#isDirectory()} returns true for this file.
     * @throws InternalError            If this method fails for an unknown
     *                                  reason.
     * @throws IllegalArgumentException If the resource does not exist.
     * @throws IllegalStateException    If this method is called from outside a
     *                                  jar file.
     */
    public static void copyResourcesTo(Class<?> clazz, ClassLoader loader, String resource, File output) {

        // The the output folder doesn't exist, create it's parent directories
        // then the directory defined by folder.
        if (!output.mkdirs() && !output.exists()) {
            throw new InternalError("Failed to create directory: " + output);
        }

        // Determine if the resource exists
        URL url = loader.getResource(resource);
        if (url == null) {
            throw new IllegalArgumentException("Invalid resource: " + resource);
        }

        // URIs can point to resources, even if the resources are in a jar file.
        URL jar;
        Path pathToJar;
        try {
            URI uri = url.toURI();

            // This method does not support being run from outside of a jar
            // file. Does this method really need to support plugins running
            // from an IDE?
            if (!uri.getScheme().equals("jar")) {
                throw new IllegalStateException("This method only supports copying files from a JAR file");
            }

            jar = clazz.getProtectionDomain().getCodeSource().getLocation();
            pathToJar = Paths.get(jar.toURI());

        } catch (URISyntaxException e) {
            throw new InternalError(e);
        }

        // The "working" part of the method. This creates a filesystem in the
        // jar, and copies files immediately in that file system. For any
        // folders in the file system, this method will be called recursively.
        try (
                FileSystem fs = FileSystems.newFileSystem(pathToJar, null);
                DirectoryStream<Path> directories = Files.newDirectoryStream(fs.getPath(resource));
        ) {
            for (Path p : directories) {
                String path = p.toString();
                path = path.substring(path.lastIndexOf('/'));

                // Handle nested folders
                File file = new File(output, path);
                path = p.toString().substring(1);
                if (path.indexOf('.') == -1) {
                    copyResourcesTo(clazz, loader, path, file);
                    continue;
                }

                URL streamHolder = loader.getResource(path);
                if (streamHolder == null) {
                    // This should never occur
                    throw new InternalError("Unknown resource: " + p);
                }

                // Handle hard files
                try (
                        InputStream in = streamHolder.openStream();
                        FileOutputStream out = new FileOutputStream(file)
                ) {
                    int data;
                    while ((data = in.read()) != -1) {
                        out.write(data);
                    }
                } catch (IOException e) {
                    throw new InternalError(e);
                }
            }
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    public static boolean ensureDefaults(ClassLoader loader, String resource, File file) {
        Yaml yaml = new Yaml();
        boolean isSetValue = false;

        InputStream input;
        try {
            URL url = loader.getResource(resource);
            if (url == null) {
                throw new InternalError("Unknown resource: " + resource);
            }

            input = url.openStream();
        } catch (IOException e) {
            throw new InternalError(e);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> defaults = yaml.load(input);

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!config.contains(key)) {
                isSetValue = true;

                config.set(key, value);
            }
        }

        return isSetValue;
    }
}
