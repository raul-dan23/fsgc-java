package ro.uvt.fsgc.orar.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.dto.ActivityView;
import ro.uvt.fsgc.orar.dto.SectionView;

/** Maps a {@link ScheduledActivity} to the flattened {@link ActivityView} used by the API/UI. */
public final class ActivityMapper {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI"};

    private ActivityMapper() {
    }

    public static ActivityView toView(ScheduledActivity a) {
        TimeSlot ts = a.getTimeSlot();
        Room room = a.getRoom();
        List<String> groups = a.getStudentGroups().stream()
                .map(StudentGroup::getName).sorted().collect(Collectors.toList());
        List<SectionView> sections = a.getStudentGroups().stream()
                .map(ActivityMapper::sectionOf)
                .distinct()
                .sorted((x, y) -> {
                    int c = x.program().compareTo(y.program());
                    if (c != 0) return c;
                    c = Integer.compare(x.year(), y.year());
                    if (c != 0) return c;
                    return x.specialization().compareToIgnoreCase(y.specialization());
                })
                .collect(Collectors.toList());
        boolean assigned = ts != null && room != null;
        return new ActivityView(
                a.getId(),
                a.getSubject().getCode(),
                a.getSubject().getName(),
                a.getProfessor() == null ? null : a.getProfessor().getName(),
                a.getActivityType().name(),
                a.getRawType(),
                a.getWeekParity().name(),
                a.getSpecialCategory().name(),
                groups,
                sections,
                a.totalStudentCount(),
                ts == null ? null : ts.getId(),
                ts == null ? null : ts.getDayOfWeek().name(),
                ts == null ? null : ts.getSlotIndex(),
                ts == null ? null : fmt(ts.getStartTime()),
                ts == null ? null : fmt(ts.getEndTime()),
                room == null ? null : room.getName(),
                assigned);
    }

    private static SectionView sectionOf(StudentGroup g) {
        String prog = g.getStudyProgram() == StudyProgram.MASTER ? "MASTER" : "LICENȚĂ";
        String r = g.getYear() >= 0 && g.getYear() < ROMAN.length
                ? ROMAN[g.getYear()] : Integer.toString(g.getYear());
        return new SectionView(
                g.getStudyProgram() == null ? null : g.getStudyProgram().name(),
                g.getYear(),
                g.getSpecialization(),
                prog + "/ANUL " + r);
    }

    private static String fmt(LocalTime t) {
        return t == null ? null : t.format(HM);
    }
}
