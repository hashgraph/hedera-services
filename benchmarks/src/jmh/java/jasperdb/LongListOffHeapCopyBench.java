package jasperdb;

import com.swirlds.jasperdb.collections.LongListOffHeap;

import java.util.stream.LongStream;

public class LongListOffHeapCopyBench {
    public static void main(String[] args) throws InterruptedException {
////        System.out.println("Waiting 15");
////        Thread.sleep(1000*15);
//        int chunkSize = Integer.getInteger("chunk",8);
//        System.out.println("chunkSize = " + chunkSize);
//        LongListOffHeap longListOffHeap = LongListOffHeap.newInstanceWithChunksOfSizeInMb(chunkSize);
//        // create 16Gb data, 2 billion longs
//        System.out.println("Starting making data");
////        LongStream.range(0,2_000_000_000L).parallel().forEach(i -> {
////            longListOffHeap.put(i,i+1);
////            if (i > 0 && (i % 10_000_000L) == 0) {
////                System.out.println("        created = " + i);
////            }
////        });
//        LongStream.range(0,2_000).parallel().forEach(j -> {
//            for (long k = 0; k < 1_000_000; k += 1000) {
//                long i = j*k;
//                longListOffHeap.put(i,i+1);
//                if (i > 0 && (i % 10_000_000L) == 0) {
//                    System.out.println("        created = " + i);
//                }
//            }
//        });
//        System.out.println("Starting making copy");
//        long start = System.nanoTime();
//        LongListOffHeap copy = new LongListOffHeap(longListOffHeap);
//        long end = System.nanoTime();
//        long took = end-start;
//        System.out.println("took = " + took);
//        System.out.println("took = " + ((double)took/1_000_000_000D));

//        Thread.sleep(1000*60*60);
    }
}
