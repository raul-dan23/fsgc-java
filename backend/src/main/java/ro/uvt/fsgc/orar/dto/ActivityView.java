package ro.uvt.fsgc.orar.dto;

import java.util.List;

/** A flattened, UI-friendly view of one scheduled activity in the result. */
public record ActivityView(
        Long id,
        String subjectCode,
        String subject,
        String professor,
        String activityType,
        String rawType,
        String weekParity,
        String specialCategory,
        List<String> groups,
        List<SectionView> sections,
        int students,
        Long timeSlotId,
        String day,
        Integer slotIndex,
        String startTime,
        String endTime,
        String room,
        boolean assigned) {
}
