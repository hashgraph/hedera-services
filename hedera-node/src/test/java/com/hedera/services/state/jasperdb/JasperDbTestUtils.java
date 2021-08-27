package com.hedera.services.state.jasperdb;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import com.swirlds.common.crypto.Hash;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

public class JasperDbTestUtils {

    public static ContractKey newContractKey(long num) {
//        return new ContractKey(new Id(num,num,num),new ContractUint256(num));
        return new ContractKey(new Id(0,0,num),new ContractUint256(num));
    }

    public static void deleteDirectoryAndContents(Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                Files.deleteIfExists(dir);
            } catch (Exception e) {
                System.err.println("Failed to delete test directory ["+dir.toFile().getAbsolutePath()+"]");
                e.printStackTrace();
            }
            System.out.println("Deleted data files");
        }
    }

    public static String toLongsString(Hash hash) {
        final var bytes = hash.getValue();
        LongBuffer longBuf =
                ByteBuffer.wrap(bytes)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asLongBuffer();
        long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        return Arrays.toString(array);
    }

    public static String toIntsString(ByteBuffer buf) {
        buf.rewind();
        IntBuffer intBuf = buf.asIntBuffer();
        int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        buf.rewind();
        return Arrays.toString(array);
    }

    public static String toLongsString(ByteBuffer buf) {
        buf.rewind();
        LongBuffer longBuf = buf.asLongBuffer();
        long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        buf.rewind();
        return Arrays.toString(array);
    }

    /**
     * Creates a hash containing a int repeated 6 times as longs
     *
     * @return byte array of 6 longs
     */
    public static Hash hash(int value) {
        byte b0 = (byte)(value >>> 24);
        byte b1 = (byte)(value >>> 16);
        byte b2 = (byte)(value >>> 8);
        byte b3 = (byte)value;
        return new HashTools.NoCopyHash(new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        });
    }

    public static void hexDump(PrintStream out, Path file) throws IOException {
        InputStream is = new FileInputStream(file.toFile());
        int i = 0;

        while (is.available() > 0) {
            StringBuilder sb1 = new StringBuilder();
            StringBuilder sb2 = new StringBuilder("   ");
            out.printf("%04X  ", i * 16);
            for (int j = 0; j < 16; j++) {
                if (is.available() > 0) {
                    int value = (int) is.read();
                    sb1.append(String.format("%02X ", value));
                    if (!Character.isISOControl(value)) {
                        sb2.append((char)value);
                    }
                    else {
                        sb2.append(".");
                    }
                }
                else {
                    for (;j < 16;j++) {
                        sb1.append("   ");
                    }
                }
            }
            out.print(sb1);
            out.println(sb2);
            i++;
        }
        is.close();
    }

}
