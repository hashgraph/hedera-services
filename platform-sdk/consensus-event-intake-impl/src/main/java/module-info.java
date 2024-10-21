module com.consensus.event.intake.impl {
    requires transitive com.consensus.event.intake;

    provides com.consensus.event.intake.EventIntakeService with
            com.consensus.event.intake.impl.EventIntakeServiceImpl;
}