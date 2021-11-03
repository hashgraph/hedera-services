package jasperdb;

import com.swirlds.jasperdb.collections.LongListOffHeap;
import jnr.ffi.annotations.In;
import org.agrona.ExpandableDirectByteBuffer;

import java.util.stream.LongStream;

public class AgronaCopyBench {
    public static void main(String[] args) throws InterruptedException {
        ExpandableDirectByteBuffer expandableDirectByteBuffer = new ExpandableDirectByteBuffer(Integer.MAX_VALUE);
        // create 16Gb data, 2 billion longs
        System.out.println("Starting making data");
        for (int i = 0; i < (Integer.MAX_VALUE/Long.BYTES); i += 1000) {
            expandableDirectByteBuffer.putLong(i*Long.BYTES,i);
            if (i > 0 && (i % 10_000_000L) == 0) {
                System.out.println("        created = " + i);
            }
        }
        System.out.println("Starting making copy");
        long start = System.nanoTime();
        ExpandableDirectByteBuffer expandableDirectByteBuffer2 = new ExpandableDirectByteBuffer(Integer.MAX_VALUE);
        expandableDirectByteBuffer.getBytes(0,expandableDirectByteBuffer2, 0, Integer.MAX_VALUE);
        long end = System.nanoTime();
        long took = end-start;
        System.out.println("took = " + took);
        System.out.println("took = " + ((double)took/1_000_000_000D));

//        Thread.sleep(1000*60*60);
    }
}
