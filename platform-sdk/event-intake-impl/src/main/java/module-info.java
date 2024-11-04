import org.hiero.event.intake.impl.EventIntakeImpl;

module org.hiero.event.intake.impl {
    requires transitive org.hiero.event.intake;

    provides org.hiero.event.intake.EventIntake with
            org.hiero.event.intake.impl.EventIntakeImpl;
}
