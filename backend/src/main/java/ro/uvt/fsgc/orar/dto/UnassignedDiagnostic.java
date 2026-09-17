package ro.uvt.fsgc.orar.dto;

import java.util.List;

/** Why one activity could not be placed, plus the closest placements and what each would break. */
public record UnassignedDiagnostic(Long activityId, String subjectCode, String subject,
                                   String activityType, String professor, List<String> groups,
                                   int students, String reason, boolean structural,
                                   List<PlacementOption> options) {

    /** A candidate slot+room and the hard rules it would violate (empty = violates nothing). */
    public record PlacementOption(String day, int slotIndex, String time, String room,
                                  List<String> violations) {
    }
}
