package com.hedera.services.state.merkle.v2.persistance;

import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class PersistenceUtils {
//    private static

    /**
     * Create a fully allocated non-sparse file
     *
     * @param path the path to the file to create
     * @param lengthBytes the size to allocate for file in bytes
     */
    public static void createFile(Path path, int lengthBytes) {
        boolean isLinux = System.getProperty("os.name").toLowerCase().contains("nux");
        if (isLinux) {
            String absolutePath = path.toFile().getAbsolutePath();

            ProcessBuilder builder = new ProcessBuilder()
                    .command("fallocate", "-l",Integer.toString(lengthBytes),absolutePath)
                    .directory(new File(System.getProperty("user.home")));
            try {
                Process process = builder.start();
                String output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining())
                        .trim();
                return;
            } catch (IOException ioException) {
                System.out.println("Failed to create file with \"fallocate\" command");
                ioException.printStackTrace();
            }
        }
        // fall back to java api
        try {
            RandomAccessFile file = new RandomAccessFile(path.toFile(),"rw");
            file.setLength(lengthBytes);
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * We assume try and get the page size and us default of 4k if we fail as that is standard on linux
     */
    private static final int PAGE_SIZE_BYTES;
    static {
        int pageSize = 4096; // 4k is default on linux
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Unsafe unsafe = (Unsafe)f.get(null);
            pageSize = unsafe.pageSize();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.out.println("Failed to get page size via misc.unsafe");
            // try and get from system command
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (!isWindows) {
                ProcessBuilder builder = new ProcessBuilder()
                        .command("getconf", "PAGE_SIZE")
                        .directory(new File(System.getProperty("user.home")));
                try {
                    Process process = builder.start();
                    String output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining())
                            .trim();
                    try {
                        pageSize = Integer.parseInt(output);
                    } catch(NumberFormatException numberFormatException) {
                        System.out.println("Failed to get page size via running \"getconf\" command. so using default 4k\n"+
                                "\"getconf\" output was \""+output+"\"");
                    }
                } catch (IOException ioException) {
                    System.out.println("Failed to get page size via running \"getconf\" command. so using default 4k");
                    ioException.printStackTrace();
                }
            }
        }
        System.out.println("Page size: " + pageSize);
        PAGE_SIZE_BYTES = pageSize;
    }
}
