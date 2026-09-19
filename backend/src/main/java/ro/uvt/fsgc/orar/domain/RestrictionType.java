package ro.uvt.fsgc.orar.domain;

/** Whether a professor-room restriction whitelists a room or forbids it. */
public enum RestrictionType {
    /** The professor may teach ONLY in rooms listed with this type. */
    ONLY_THIS,
    /** The professor may NOT teach in this room. */
    FORBIDDEN
}