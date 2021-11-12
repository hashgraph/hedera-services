package disruptor;

import java.util.concurrent.CountDownLatch;

public class Latch {
    CountDownLatch latch = new CountDownLatch(1);

    public void countdown() {
        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
        latch = new CountDownLatch(1);
    }
}
