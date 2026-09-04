package com.sterul.opencookbookapiserver.entities.ml;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mirrors the subsystem's own job states, so a poll is a copy rather than a translation. */
public enum MlJobStatus {

    // The strings are the subsystem's Job.Status values, which are a fixed set stored in its
    // database and serialised as they are. Spelling them out here means the wire format is
    // stated rather than inferred from these names, so renaming a constant cannot silently
    // change what we accept.
    QUEUED("queued"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private static final Logger log = LoggerFactory.getLogger(MlJobStatus.class);

    private static final Map<String, MlJobStatus> BY_SUBSYSTEM_VALUE = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(status -> status.subsystemValue,
                    Function.identity()));

    private final String subsystemValue;

    MlJobStatus(String subsystemValue) {
        this.subsystemValue = subsystemValue;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public static MlJobStatus fromSubsystem(String status) {
        if (status == null) {
            return QUEUED;
        }
        var known = BY_SUBSYSTEM_VALUE.get(status);
        if (known != null) {
            return known;
        }
        // A state from a newer subsystem, or a contract that has drifted. Treated as still
        // running so the poller asks again and the job timeout is the backstop - but said out
        // loud, because the alternative is a job that only ever ends in ML_TIMEOUT.
        log.warn("The machine learning subsystem reported the unknown job state '{}'", status);
        return PROCESSING;
    }
}
