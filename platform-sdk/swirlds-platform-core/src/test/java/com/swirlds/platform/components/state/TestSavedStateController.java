package com.swirlds.platform.components.state;

import static org.mockito.ArgumentMatchers.any;

import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Deque;
import java.util.LinkedList;
import org.mockito.Mockito;

public class TestSavedStateController {
    private final Deque<SignedState> queue = new LinkedList<>();

    public SavedStateController createMock(){
        final SavedStateController mock = Mockito.mock(SavedStateController.class);
        Mockito.doAnswer(
                invocation -> {
                    final SignedState signedState = invocation.getArgument(0);
                    System.out.println("aaaaaaa");
                    queue.add(signedState);
                    return null;
                }
        ).when(mock).maybeSaveState(any());
        return mock;
    }

    public @NonNull Deque<SignedState> getAttemptQueue() {
        return queue;
    }

}
