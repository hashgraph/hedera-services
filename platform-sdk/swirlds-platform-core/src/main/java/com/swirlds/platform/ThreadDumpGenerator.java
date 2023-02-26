/*
 * Copyright (C) 2017-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class generates augmented thread dumps which include lock information.
 */
class ThreadDumpGenerator {

    private static final Logger logger = LogManager.getLogger(ThreadDumpGenerator.class);

    /**
     * random used to append to file names in case the timestamp is the same
     */
    private static final Random random = new Random();

    /**
     * generator thread that generates a thread dump at intervals
     */
    private static volatile Runnable generator = null;

    /**
     * Generate a thread dump file at intervals. This is for debugging, and is currently never called.
     *
     * @param milliseconds
     * 		interval at which to generate thread dumps in milliseconds
     */
    public static synchronized void generateThreadDumpAtIntervals(Path dir, long milliseconds) {
        if (generator != null) {
            return;
        }
        generator = () -> {
            while (true) {
                generateThreadDumpFile(dir, null);
                try {
                    Thread.sleep(milliseconds);
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        };
        new Thread(generator).start();
    }

    /**
     * Same as generateThreadDumpFile(Path dir, String heading) but with no heading
     */
    public static void generateThreadDumpFile(final Path dir) {
        generateThreadDumpFile(dir, null);
    }

    /**
     * Generate a thread dump file
     *
     * @param heading
     * 		a heading that is placed at the top of the thread dump file, used if needed to distinguish
     * 		a certain file by some additional criteria other than time
     */
    public static void generateThreadDumpFile(final Path dir, final String heading) {
        final Path threadDumpPath = dir.resolve(Path.of(makeFileName()));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(threadDumpPath.toFile()))) {
            // write the heading
            if (heading != null) {
                writer.append(" --- ");
                writer.append(heading);
                writer.append(" --- ");
                writer.append("\n\n");
            }

            final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            final ThreadInfo[] threadInfos =
                    threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 1000); // get up to 1000 stack levels
            writer.append(lockGraph() + "\n\n");
            for (ThreadInfo threadInfo : threadInfos) {
                if (threadInfo == null) {
                    continue;
                }
                writeThreadTitle(writer, threadInfo.getThreadName(), threadInfo.getThreadId());
                final Thread.State state = threadInfo.getThreadState();
                writer.append("\n  java.lang.Thread.State: ");
                writer.append(state.toString());
                final StackTraceElement[] stackTraceElements = threadInfo.getStackTrace();
                MonitorInfo[] monitorInfos = null;
                if (threadMXBean.isObjectMonitorUsageSupported()) {
                    monitorInfos = threadInfo.getLockedMonitors();
                }
                for (int i = 0; i < stackTraceElements.length; i++) {
                    LockInfo lockInfo = threadInfo.getLockInfo();
                    writer.append("\n    at ");
                    writer.append(stackTraceElements[i].toString());
                    if (i == 0 && lockInfo != null) {
                        String lockOwnerName = threadInfo.getLockOwnerName();
                        writer.append("\n      waiting for "); // NOI18N
                        writeLock(writer, lockInfo);
                        if (lockOwnerName != null) {
                            writer.append("\n      owned by ");
                            writeThreadTitle(writer, lockOwnerName, threadInfo.getLockOwnerId());
                        }
                    }
                    if (monitorInfos != null) {
                        for (MonitorInfo monitorInfo : monitorInfos) {
                            if (monitorInfo.getLockedStackDepth() == i) {
                                writer.append("\n      locked ");
                                writeLock(writer, monitorInfo);
                            }
                        }
                    }
                }
                writer.append("\n\n");
            }
        } catch (Exception e) {
            logger.error(EXCEPTION.getMarker(), "exception in generating thread file\n", e);
        }
    }

    /**
     * Create a string representation of all locks currently held, and the threads holding or waiting for
     * them. Each thread or lock will be on its own line, which will start with the string "indent", perhaps
     * with additional indentation. There may be errors in this graph due to the threads blocking and
     * unblocking while this method is running.
     *
     * @return a string representing the graph in outline format
     */
    private static String lockGraph() {
        /** map from a thread's ID to its ThreadInfo */
        Map<Long, ThreadInfo> idToThreadInfo = new HashMap<>();
        /** map from a lock's ID to its LockInfo */
        Map<Integer, LockInfo> idToLockInfo = new HashMap<>();
        /** map from a blocked thread to the lock it is waiting for */
        Map<Long, LockInfo> threadToBlockingLock = new HashMap<>();
        /** map from a lock that is blocking a thread to the thread that owns it */
        Map<Integer, ThreadInfo> lockToOwningThread = new HashMap<>();
        /** map from a thread to the set of locks it owns that are currently blocking another thread */
        Map<Long, Set<Integer>> threadToOwnedLocks = new HashMap<>();
        /** map from a lock to the set of threads that are currently blocking while waiting for it */
        Map<Integer, Set<Long>> lockToBlockedThreads = new HashMap<>();
        /** set of threads that are tree roots (not blocked), plus one in each cycle chosen as a "root" */
        Set<Long> roots = new HashSet<>();
        /** set of threads that are known to be non-roots or roots (including the 1 fake root per cycle) */
        Set<Long> done = new HashSet<>();

        String result = ""
                + "============== LOCK DEPENDENCY GRAPH ==============\n"
                + "|| Below each thread are all the locks it holds, indented one level.\n"
                + "|| Below each lock are all the threads blocked on it, indented one level.\n"
                + "|| Each thread lists its location in the code \n"
                + "|| (latest stack frame in Swirlds code, but not in LoggingReentrantLock).\n";

        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        final ThreadInfo[] threadInfos =
                threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 1000); // get up to 1000 stack levels

        // fill in idToThreadInfo and idToLockInfo
        for (ThreadInfo thread : threadInfos) {
            if (thread != null) {
                idToThreadInfo.put(thread.getThreadId(), thread);
                LockInfo lock = thread.getLockInfo();
                if (lock != null) {
                    idToLockInfo.put(lock.getIdentityHashCode(), lock);
                }
            }
        }
        // fill in all data structures except idToThreadInfo, roots, and done
        for (ThreadInfo thread : threadInfos) {
            if (thread != null) {
                LockInfo lock = thread.getLockInfo(); // the lock that thread is blocked on and waiting for
                ThreadInfo owner = null; // the thread that owns lock
                if (lock != null) {
                    long id = thread.getLockOwnerId();
                    owner = (id == -1) ? null : idToThreadInfo.get(id);
                }
                if (owner == null) {
                    roots.add(thread.getThreadId());
                    done.add(thread.getThreadId());
                    continue; // ignore threads that aren't currently blocked. They are roots. They're done.
                }
                threadToBlockingLock.put(thread.getThreadId(), lock);
                lockToOwningThread.put(lock.getIdentityHashCode(), owner);
                Set<Long> blockedSet = lockToBlockedThreads.get(lock.getIdentityHashCode());
                Set<Integer> ownedSet = threadToOwnedLocks.get(owner.getThreadId());
                if (blockedSet == null) {
                    blockedSet = new HashSet<>();
                    lockToBlockedThreads.put(lock.getIdentityHashCode(), blockedSet);
                }
                if (ownedSet == null) {
                    ownedSet = new HashSet<>();
                    threadToOwnedLocks.put(owner.getThreadId(), ownedSet);
                }
                blockedSet.add(thread.getThreadId());
                ownedSet.add(lock.getIdentityHashCode());
            }
        }

        // add to roots a single vertex in each cycle, and fill in done
        for (ThreadInfo startThread : threadInfos) {
            if (startThread != null) {
                /** the lock that this thread is blocked on, or null if none */
                LockInfo blockingLock = threadToBlockingLock.get(startThread.getThreadId());
                if (blockingLock == null) {
                    // the path starts at a root, so mark it as such. There's no path to walk, so don't
                    roots.add(startThread.getThreadId());
                    continue;
                }
                /** the current position as we walk through the graph */
                ThreadInfo curr = startThread;
                // follow the path from thread until reaching either a true root or startThread again (a
                // cycle)
                while (!done.contains(curr.getThreadId())) {
                    done.add(curr.getThreadId()); // in the future, stop when we reach this point again.
                    /**
                     * the next thread in the graph (the one owning the lock that curr is blocked waiting on
                     */
                    ThreadInfo next = lockToOwningThread.get(
                            threadToBlockingLock.get(curr.getThreadId()).getIdentityHashCode());
                    if (next == startThread) {
                        // we walked all the way around a cycle without hitting any done threads,
                        // so arbitrarily call startThread the "root" of the cycle.
                        roots.add(startThread.getThreadId());
                        done.add(startThread.getThreadId());
                        break;
                    }
                    curr = next; // continue following the directed edges in the graph
                }
            }
        }

        // create a string containing a tree for each of the roots that were found
        for (long thread : roots) {
            result += lockGraph(
                    "|| ", "---", thread, -1, idToThreadInfo, idToLockInfo, threadToOwnedLocks, lockToBlockedThreads);
        }
        result += "===================================================\n";
        return result;
    }

    /**
     * A recursive method called only by lockGraph(threadInfos), to draw just one tree out of the graph. If
     * this is a top-level call (where stop==-1), and the root has no children, then this just returns the
     * empty string.
     *
     * @param firstIndent
     * 		a string to insert at the start of each line. Must be "" for root-level threads.
     * @param levelIndent
     * 		the extra indentation to add at each level of indentation
     * @param root
     * 		the thread to start with (to indent the least)
     * @param stop
     * 		don't display children of this (if -1, then will recurse with stop==root)
     * @param threadToOwnedLocks
     * 		map from a thread to the set of locks it owns that are currently blocking another
     * 		thread
     * @param lockToBlockedThreads
     * 		map from a lock to the set of threads that are currently blocking while waiting for
     * 		it
     * @return the string representing the tree from that single root (stopping at any leaf equal to the
     * 		root)
     */
    private static String lockGraph(
            String firstIndent,
            String levelIndent,
            long root,
            long stop,
            Map<Long, ThreadInfo> idToThreadInfo,
            Map<Integer, LockInfo> idToLockInfo,
            Map<Long, Set<Integer>> threadToOwnedLocks,
            Map<Integer, Set<Long>> lockToBlockedThreads) {
        /** the string to return */
        String result = "";
        /** all the locks owned by root */
        Set<Integer> locks;
        locks = threadToOwnedLocks.get(root);
        if (stop == -1 && (locks == null || locks.size() == 0)) {
            // skip a top-level thread if it has no children to draw under it
            return result;
        }
        StackTraceElement[] stack = idToThreadInfo.get(root).getStackTrace();
        StackTraceElement line = (stack == null || stack.length == 0) ? null : stack[0];
        if (stack != null) {
            for (StackTraceElement ste : stack) {
                // use the first line from swirlds code, not counting inside LoggingReentrantLock
                // (or leave it as stack[0] if there are no such elements in the stack frame)
                if (ste.getClassName().startsWith("com.swirlds")
                        && !ste.getClassName().startsWith("com.swirlds.platform.LoggingReentrantLock")
                        && !ste.getClassName().startsWith("com.swirlds.platform.ThreadDumpGenerator")) {
                    line = ste;
                    break;
                }
            }
        }
        if (stop == -1) {
            result += "||\n"; // skip a line before each separate tree
        }
        String loc = line == null ? "" : line.toString();
        result += firstIndent + "thread " + idToThreadInfo.get(root).getThreadName() + " at " + loc + "\n";
        if (stop == root) {
            result += firstIndent + levelIndent + "### CYCLE: the above thread has its children shown elsewhere ### \n";
            return result; // don't recurse forever. we just finished one cycle. That's enough.
        }
        if (stop == -1) {
            stop = root; // when recursing, stop when the root appears as a leaf
        }
        if (locks != null) {
            for (Integer lock : locks) {
                result += firstIndent + levelIndent + "lock "
                        + idToLockInfo.get(lock).getIdentityHashCode() + ": "
                        + idToLockInfo.get(lock).getClassName()
                        + "\n";
                Set<Long> threads = lockToBlockedThreads.get(lock);

                if (threads != null) {
                    for (long thread : threads) {

                        result += lockGraph(
                                firstIndent + levelIndent + levelIndent,
                                levelIndent,
                                thread,
                                stop,
                                idToThreadInfo,
                                idToLockInfo,
                                threadToOwnedLocks,
                                lockToBlockedThreads);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Used to write a textual identification of a thread to a writer
     */
    private static void writeThreadTitle(Writer writer, String threadName, Long threadId) throws IOException {
        writer.append('"');
        writer.append(threadName);
        writer.append("\" | ");
        writer.append("threadId = ");
        writer.append(Long.toString(threadId));
    }

    /**
     * Used to write a textual identification of a lock to a writer
     */
    private static void writeLock(Writer writer, LockInfo lockInfo) throws IOException {
        String lockId = Integer.toString(lockInfo.getIdentityHashCode());
        String lockClassName = lockInfo.getClassName();
        writer.append("lockId = ");
        writer.append(lockId);
        writer.append(" | lockClassName = ");
        writer.append(lockClassName);
    }

    /**
     * Used to generate a unique file name for a thread dump
     */
    private static String makeFileName() {
        final SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS");
        return "threadDump " + dt.format(new Date()) + "  " + random.nextInt() + ".txt";
    }

    /**
     * This debugging method spawns two new threads, which will quickly reach a deadlock, where each is
     * waiting for a lock held by the other. This doesn't actually hurt anything. The two threads won't
     * consume any cycles or hurt the scheduling of other threads, because they are eternally blocked. And
     * they will consume very little memory. But they will show up in both the deadlock watchdog thread, and
     * in any thread dump files that are created. So this method acts as a good test that both of those
     * mechanisms are working properly.
     */
    static void createDeadlock() {
        final ReentrantLock x = new ReentrantLock(false);
        final ReentrantLock y = new ReentrantLock(false);
        final Thread threadXY = new Thread(() -> {
            while (true) {
                x.lock();
                y.lock();
                y.unlock();
                x.unlock();
            }
        });

        final Thread threadYX = new Thread(() -> {
            while (true) {
                y.lock();
                x.lock();
                x.unlock();
                y.unlock();
            }
        });
        threadXY.setName("<test for deadlock 1  (XY)>");
        threadYX.setName("<test for deadlock 2  (YX)>");
        threadXY.start();
        threadYX.start();
    }
}
