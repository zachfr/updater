package com.songoda.serverjars;

import com.serverjars.api.JarDetails;
import com.serverjars.api.Response;
import com.serverjars.api.request.AllRequest;
import com.serverjars.api.request.JarRequest;
import com.serverjars.api.request.LatestRequest;
import com.serverjars.api.request.TypesRequest;
import com.serverjars.api.response.AllResponse;
import com.serverjars.api.response.LatestResponse;
import com.serverjars.api.response.TypesResponse;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

public final class ServerJars {

    public static void main(final String[] args) {
        final Method mainMethod;

        System.out.println("   _____                               __               \n" +
                "  / ___/___  ______   _____  _____    / /___ ___________\n" +
                "  \\__ \\/ _ \\/ ___/ | / / _ \\/ ___/_  / / __ `/ ___/ ___/\n" +
                " ___/ /  __/ /   | |/ /  __/ /  / /_/ / /_/ / /  (__  ) \n" +
                "/____/\\___/_/    |___/\\___/_/   \\____/\\__,_/_/  /____/  \n" +
                "ServerJars.com | Made with love by Songoda <3 | Edited by Zach_FR");

        System.out.println("\nServerJars is starting...");


        Path jar = setupEnv(args[0]);

        if (jar == null) {
            System.out.println("\nServerJars could not be reached...");
            System.out.println("\nAttempting to load last working Jar.");
            jar = findExistingJar();
            if (jar == null) {
                System.out.println("\nAll attempts to run failed...");
                System.exit(1);
            }
            System.out.println("\nThe attempt was successful!");
        }
        final String main = getMainClass(jar);
        mainMethod = getMainMethod(jar, main);

        try {
            mainMethod.invoke(null, new Object[]{Arrays.copyOfRange(args, 1, args.length)});
        } catch (final IllegalAccessException | InvocationTargetException e) {
            System.err.println("\nError while running patched jar");
            e.printStackTrace();
            System.exit(1);
        }
    }


    private static Path setupEnv(String version) {
        Properties properties = new Properties();
        Path cache = Paths.get("jar");

        JarDetails jarDetails = null;
        if (version.equals("latest")) {
            LatestResponse latestResponse = new LatestRequest("spigot").send();
            jarDetails = latestResponse.latestJar;
        } else {
            AllResponse allResponse = new AllRequest("spigot").send();
            for (JarDetails jar : allResponse.getJars()) {
                if (jar.getVersion().equalsIgnoreCase(version)) {
                    jarDetails = jar;
                }
            }
        }

        final Path jar = Paths.get(cache.normalize().toString() + File.separator + jarDetails.getFile());

        String hash = jar.toFile().exists() ? md5File(jar) : "";
        if (hash.isEmpty() || !hash.equals(jarDetails.getHash())) {
            System.out.println(hash.isEmpty() ? "\nDownloading jar..." : "\nUpdate found, downloading...");
            for (File f : cache.toFile().listFiles())
                if (f.getName().endsWith(".jar"))
                    f.delete();


            Response response = new JarRequest("spigot", version.equalsIgnoreCase("latest") ? null : version, jar.toFile()).send();
            if (!response.isSuccess()) {
                System.out.println("\nThe jar version \"" + version + "\" was not found in our database...");
                return null;
            }
            System.out.println("\nJar updated successfully.");
        } else {
            System.out.println("\nThe jar is up to date.");
        }
        String launching = "\nLaunching " + jarDetails.getFile() + "...";
        System.out.println(launching + "\n" + launching.replaceAll("[^.]", ".") + "\n");
        return jar;
    }

    private static ClassLoader addClasspath(Path jar) {
        try {
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            if (classLoader instanceof JarLoader) {
                JarLoader loader = (JarLoader) classLoader;
                loader.add(jar.toUri().toURL());
                return loader;
            } else {
                URLClassLoader sysLoader = new URLClassLoader(new URL[0]);
                Method sysMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                sysMethod.setAccessible(true);
                sysMethod.invoke(sysLoader, jar.toUri().toURL());
                if (getJavaVersion() > 8)
                    System.err.print("\n===========================\n\nNotice: Java has warned you that you are using a workaround if you want to get around this you can add the following arguments: '-Djava.system.class.loader=com.serverjars.updater.ServerJarsLoader'\ne.g 'java -Djava.system.class.loader=com.serverjars.updater.ServerJarsLoader -jar serverjars.jar\n'");
                return sysLoader;
            }
        } catch (final MalformedURLException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            System.err.println("Unable to add the Jar to System ClassLoader.");
            e.printStackTrace();
            System.exit(1);
            throw new InternalError();
        }
    }

    private static String getMainClass(final Path jar) {
        try (
                final InputStream is = new BufferedInputStream(Files.newInputStream(jar));
                final JarInputStream js = new JarInputStream(is)
        ) {
            return js.getManifest().getMainAttributes().getValue("Main-Class");
        } catch (final IOException e) {
            System.err.println("Error reading from patched jar");
            e.printStackTrace();
            System.exit(1);
            throw new InternalError();
        }
    }

    private static Method getMainMethod(final Path jar, final String mainClass) {
        try {
            final Class<?> cls = Class.forName(mainClass, true, addClasspath(jar));
            return cls.getMethod("main", String[].class);
        } catch (final NoSuchMethodException | ClassNotFoundException e) {
            System.err.println("Failed to find main method in patched jar");
            e.printStackTrace();
            System.exit(1);
            throw new InternalError();
        }
    }

    private static Path findExistingJar() {
        try {
            Path cache = Paths.get("jar");
            if (Files.isDirectory(cache)) {
                List<Path> list = Files.list(cache).filter(f -> f.toString().endsWith(".jar")).collect(Collectors.toList());
                if (list.isEmpty())
                    return null;
                return list.get(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static byte[] readFully(final InputStream in, final int size) throws IOException {
        try {
            final int bufSize;
            if (size == -1) {
                bufSize = 16 * 1024;
            } else {
                bufSize = size;
            }

            byte[] buffer = new byte[bufSize];
            int off = 0;
            int read;
            while ((read = in.read(buffer, off, buffer.length - off)) != -1) {
                off += read;
                if (off == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
            }
            return Arrays.copyOfRange(buffer, 0, off);
        } finally {
            in.close();
        }
    }

    private static byte[] readBytes(final Path file) {
        try {
            return readFully(Files.newInputStream(file), (int) Files.size(file));
        } catch (final IOException e) {
            System.err.println("Failed to read all of the data from " + file.toAbsolutePath());
            e.printStackTrace();
            System.exit(1);
            throw new InternalError();
        }
    }

    private static String awaitInput(Predicate<String> predicate, String errorMessage) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (predicate.test(line)) {
                    return line;
                } else {
                    System.err.println("\n" + String.format(errorMessage, line));
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String md5File(Path path) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(readBytes(path));
            BigInteger bigInt = new BigInteger(1, digest);
            StringBuilder hash = new StringBuilder(bigInt.toString(16));
            while (hash.length() < 32) {
                hash.insert(0, "0");
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }
}
