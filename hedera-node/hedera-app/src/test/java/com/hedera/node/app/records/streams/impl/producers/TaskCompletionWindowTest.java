package com.hedera.node.app.records.streams.impl.producers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(Lifecycle.PER_METHOD) // Create a new instance of TaskCompletionWindowTest for each test method
@Execution(ExecutionMode.CONCURRENT) // Run test methods in parallel
class TaskCompletionWindowTest {
    @Test
    void testSingleTaskCompletion() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        window.completeTask(1);
        assertEquals(1, window.getLowestCompletedTaskId(), "Lowest completed task ID should be 1.");
    }

    @Test
    void testSequentialTaskCompletion() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        window.completeTask(1);
        window.completeTask(2);
        window.completeTask(3);
        assertEquals(
                3,
                window.getLowestCompletedTaskId(),
                "Lowest completed task ID should be 3 after sequential completion.");
    }

    @Test
    void testSequentialTaskWithGapsCompletion() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        window.completeTask(1);
        window.completeTask(2);
        window.completeTask(3);

        window.completeTask(5);
        window.completeTask(6);
        window.completeTask(7);

        window.completeTask(11);
        window.completeTask(12);

        window.completeTask(4);
        assertEquals(
                7,
                window.getLowestCompletedTaskId(),
                "Lowest completed task ID should be 7 after sequential completion.");
    }

    @Test
    void testSequentialTaskWithOldTask() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        window.completeTask(1);
        window.completeTask(2);
        window.completeTask(3);

        window.completeTask(5);
        window.completeTask(6);
        window.completeTask(7);

        window.completeTask(11);
        window.completeTask(12);

        window.completeTask(4);
        window.completeTask(1);

        assertEquals(
                7,
                window.getLowestCompletedTaskId(),
                "Lowest completed task ID should be 7 after sequential completion.");
    }

    @Test
    void testSequentialTaskWithOldTaskGap() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        window.completeTask(5);
        window.completeTask(6);
        window.completeTask(7);

        window.completeTask(11);
        window.completeTask(12);

        window.completeTask(4);
        window.completeTask(1);

        assertEquals(
                1,
                window.getLowestCompletedTaskId(),
                "Lowest completed task ID should be 1 after sequential completion.");
    }

    @Test
    void testNonSequentialTaskCompletion() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        window.completeTask(3);
        window.completeTask(1);
        window.completeTask(2);
        assertEquals(
                3,
                window.getLowestCompletedTaskId(),
                "Lowest completed task ID should be 3 after non-sequential completion.");
    }

    @Test
    void testWindowAdjustmentWithGap() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        window.completeTask(0);
        window.completeTask(100); // Introduces a gap larger than the max window size.
        assertTrue(window.getLowestCompletedTaskId() > 1, "Window should adjust to maintain MAX_WINDOW_SIZE.");
    }

    @Test
    void testWindowAdjustmentWithContiguousTasks() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        for (long i = 1; i <= window.getMaxWindowSize(); i++) {
            window.completeTask(i);
        }
        window.completeTask(window.getMaxWindowSize() + 1);
        assertEquals(
                window.getMaxWindowSize() + 1,
                window.getLowestCompletedTaskId(),
                "Lowest completed task ID should adjust with window.");
    }

    @Test
    void testEmptyWindow() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        assertEquals(-1, window.getLowestCompletedTaskId(), "Empty window should return -1.");
    }

    @Test
    void testLargeTaskIdJump() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        window.completeTask(1);
        window.completeTask(2);
        window.completeTask(5000); // A large jump in task ID
        assertEquals(5000, window.getLowestCompletedTaskId(), "Window should adjust to the large task ID jump.");
    }

    @Test
    void testMaxWindowSizeConstraint() {
        TaskCompletionWindow window = new TaskCompletionWindow(100);
        for (long i = 1; i <= window.getMaxWindowSize(); i++) {
            window.completeTask(i);
        }
        window.completeTask(window.getMaxWindowSize() + 2); // This should cause the first range to adjust or be removed
        assertNotEquals(
                1,
                window.getLowestCompletedTaskId(),
                "First task ID should no longer be the lowest after exceeding MAX_WINDOW_SIZE.");
    }
}
