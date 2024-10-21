module com.consensus.event.intake.impl {
    requires transitive com.consensus.event.intake;

    provides com.consensus.event.intake.EventIntake with
            com.consensus.event.intake.impl.EventIntakeImpl;
}
