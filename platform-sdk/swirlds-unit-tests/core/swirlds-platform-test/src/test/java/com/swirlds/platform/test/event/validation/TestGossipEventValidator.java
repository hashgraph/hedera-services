package com.swirlds.platform.test.event.validation;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.validation.GossipEventValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

public class TestGossipEventValidator implements GossipEventValidator {
    private final boolean validation;
    private final String name;

    public TestGossipEventValidator(final boolean validation, @NonNull final String name) {
        this.validation = validation;
        this.name = name;
    }

    @Override
    public boolean isEventValid(@NonNull GossipEvent event) {
        return validation;
    }

    @NonNull
    @Override
    public String validatorName() {
        return name;
    }
}
