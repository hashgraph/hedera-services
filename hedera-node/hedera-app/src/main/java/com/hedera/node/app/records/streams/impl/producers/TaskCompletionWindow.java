package com.hedera.node.app.records.streams.impl.producers;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.TreeSet;

/**
 * Manages a sliding window of completed task IDs in a performant and memory-efficient way. This class tracks
 * the completion of tasks, where each task is identified by a unique long task ID. Tasks can be completed in any order,
 * and the class provides functionality to query the lowest completed task ID. To enhance performance and reduce memory
 * usage, contiguous sequences of completed task IDs are compressed into ranges rather than storing each task ID individually.
 *
 * <p>The window enforces a maximum size constraint, specified by {@code maxWindowSize}, to ensure that the range of
 * task IDs in the window does not exceed a defined limit. This is particularly useful for avoiding large gaps between
 * completed task IDs and maintaining a compact representation of the window.
 *
 * <p>The window is represented as a set of {@link TaskRange} objects, each denoting a contiguous sequence of
 * completed task IDs. This approach allows the class to efficiently manage and query the completion state of tasks
 * while minimizing memory usage by compressing contiguous sequences into single elements.</p>
 *
 * <p>This class is particularly useful in scenarios where tasks are completed asynchronously and possibly out of
 * order, but there is a need to query the lowest task ID that has been completed and not part of a contiguous
 * sequence from the start.</p>
 *
 * <h2>Usage Example</h2>
 * <p>This example demonstrates how to use the {@code TaskCompletionWindow} to track task completions and query
 * the lowest completed task ID not part of a contiguous sequence from the start.</p>
 *
 * <pre>{@code
 * public class Example {
 *     public static void main(String[] args) {
 *         TaskCompletionWindow window = new TaskCompletionWindow();
 *
 *         // Mark tasks as completed. Tasks can be completed in any order.
 *         window.completeTask(1); // Completes task with ID 1
 *         window.completeTask(3); // Completes task with ID 3
 *         window.completeTask(2); // Completes task with ID 2, forming a contiguous sequence from 1 to 3
 *
 *         // Query the lowest completed task ID not part of a contiguous sequence from the start.
 *         // Since tasks 1 to 3 form a contiguous sequence and there are no other completed tasks,
 *         // the method should return the end of this sequence, which is 3.
 *         long lowestCompletedTaskId = window.getLowestCompletedTaskId();
 *         System.out.println("Lowest Completed Task ID: " + lowestCompletedTaskId); // Outputs: 3
 *     }
 * }
 * }</pre>
 *
 * <p>Note: This class is not thread-safe. External synchronization is required when accessing this class from multiple
 * threads.
 */
public class TaskCompletionWindow {
    /**
     * The maximum size of the window. This constant defines the maximum allowed difference between
     * the highest and lowest task IDs within the window.
     */
    private final long maxWindowSize;

    /**
     * Creates a new {@code TaskCompletionWindow} with the specified maximum window size.
     *
     * @param maxWindowSize The maximum size of the window. This constant defines the maximum allowed
     *                      difference between the highest and lowest task IDs within the window.
     */
    public TaskCompletionWindow(final long maxWindowSize) {
        this.maxWindowSize = maxWindowSize;
    }

    /**
     * Getter for maxWindowSize.
     * @return The maximum size of the window.
     */
    public long getMaxWindowSize() {
        return maxWindowSize;
    }

    /**
     * A TreeSet to store completed task ranges. This set is sorted and ensures that each range is
     * non-overlapping and contiguous with adjacent ranges where possible.
     */
    private final TreeSet<TaskRange> completedTasks = new TreeSet<>();

    private static class TaskRange implements Comparable<TaskRange> {
        long start, end;

        TaskRange(final long start, final long end) {
            this.start = start;
            this.end = end;
        }

        // Does this intersect with the other.
        boolean isContiguousOrOverlapping(@NonNull final TaskRange other) {
            return rangesIntersect(other) || rangesTouch(other);
        }

        private boolean rangesIntersect(@NonNull final TaskRange other) {
            return this.end >= other.start && other.end >= this.start;
        }

        private boolean rangesTouch(@NonNull final TaskRange other) {
            return this.end + 1 == other.start || other.end + 1 == this.start;
        }

        // Merge this range with another.
        void merge(@NonNull final TaskRange other) {
            this.start = Math.min(this.start, other.start);
            this.end = Math.max(this.end, other.end);
        }

        @Override
        public int compareTo(@NonNull final TaskRange o) {
            if (this.end < o.start) return -1;
            if (this.start > o.end) return 1;
            return 0;
        }
    }

    /**
     * Marks a task as completed by adding it to the sliding window. If the task ID creates or extends
     * a contiguous sequence of completed tasks, the relevant ranges are merged to maintain compression.
     * The window is then adjusted if necessary to ensure that its size does not exceed the maximum
     * window size constraint.
     *
     * @param taskId The ID of the task to mark as completed.
     */
    public void completeTask(long taskId) {
        TaskRange newTaskRange = new TaskRange(taskId, taskId);
        // Add the task.
        completedTasks.add(newTaskRange);
        // Merge left then Merge right
        mergeRight(mergeLeft(newTaskRange));
        adjustWindow();
    }

    private TaskRange mergeLeft(TaskRange newTaskRange) {
        // Floor will return an element that equal to or less than the given element.
        TaskRange left = completedTasks.floor(newTaskRange);
        boolean added = false;
        while (left != null && left.isContiguousOrOverlapping(newTaskRange)) {
            added = true;
            completedTasks.remove(left);
            newTaskRange.merge(left);
            left = completedTasks.floor(newTaskRange);
        }
        if (added) completedTasks.add(newTaskRange);
        return newTaskRange;
    }

    private void mergeRight(TaskRange newTaskRange) {
        // Floor will return an element that equal to or greater than the given element.
        TaskRange right = completedTasks.ceiling(newTaskRange);
        boolean added = false;
        while (right != null && right.isContiguousOrOverlapping(newTaskRange)) {
            added = true;
            completedTasks.remove(right);
            newTaskRange.merge(right);
            right = completedTasks.ceiling(newTaskRange);
        }
        if (added) completedTasks.add(newTaskRange);
    }

    /**
     * Adjusts the sliding window to maintain the maximum window size constraint. This method ensures
     * that the difference between the highest and lowest task IDs within the window does not exceed
     * {@link #maxWindowSize}. It may adjust the start of the first range or remove ranges to maintain
     * this constraint.
     */
    private void adjustWindow() {
        if (completedTasks.isEmpty()) return;

        TaskRange last = completedTasks.last();

        long windowEnd = last.end;
        long windowStart = windowEnd - maxWindowSize + 1; // Calculate desired start to maintain the window size.

        for (; ; ) {
            TaskRange first = completedTasks.first();

            if (first.start < windowStart) {
                completedTasks.remove(first);
                if (first.end >= windowStart) {
                    // If the first range extends into the new window, adjust its start.
                    completedTasks.add(new TaskRange(windowStart, first.end));
                }
                // If the entire first range is outside the window, it is dropped and no new range is added.
            } else {
                // No more ranges to adjust.
                return;
            }
        }
    }

    /**
     * Retrieves the highest completed task ID in the first TaskRange.
     * If there are no completed tasks, a special value (-1) is returned to indicate this state.
     *
     * @return the highest completed task ID in the first TaskRange.
     */
    public long getLowestCompletedTaskId() {
        // If there are no completed tasks, indicate it with a special value.
        if (completedTasks.isEmpty()) return -1;
        // Return the end of the first range, as required.
        return completedTasks.first().end;
    }
}
