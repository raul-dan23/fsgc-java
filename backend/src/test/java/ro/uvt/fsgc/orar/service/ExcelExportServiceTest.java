package ro.uvt.fsgc.orar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Professor;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;
import ro.uvt.fsgc.orar.domain.TimeSlot;
import ro.uvt.fsgc.orar.repository.ScheduledActivityRepository;
import ro.uvt.fsgc.orar.repository.TimeSlotRepository;

/** Verifies the FSGC "Orar" sheet layout: section columns, day blocks, subject row + room row. */
@ExtendWith(MockitoExtension.class)
class ExcelExportServiceTest {

    @Mock ScheduledActivityRepository activityRepo;
    @Mock TimeSlotRepository timeSlotRepo;
    @InjectMocks ExcelExportService service;

    @Test
    void writesFsgcGridWithSectionColumnsAndRoomRow() throws Exception {
        // full Mon-Fri x 8 modules; Monday module 2 -> activity row = 2 + (2-1)*2 = 4
        List<TimeSlot> slots = buildWeek();
        TimeSlot mon2 = slots.stream()
                .filter(t -> t.getDayOfWeek() == DayOfWeek.MONDAY && t.getSlotIndex() == 2)
                .findFirst().orElseThrow();

        StudentGroup ap1 = group("AP1-GR1", "AP", 1, StudyProgram.LICENSE);
        StudentGroup sp1 = group("SP1-GR1", "SP", 1, StudyProgram.LICENSE);

        // a common course shared by two specializations of license year 1
        ScheduledActivity common = activity(10L, "Sisteme administrative", "AP101",
                "Draganescu", ActivityType.COURSE, mon2, "Paris 01", Set.of(ap1, sp1));

        when(activityRepo.findAll()).thenReturn(List.of(common));
        when(timeSlotRepo.findAllByOrderByDayOfWeekAscSlotIndexAsc()).thenReturn(slots);

        byte[] bytes = service.export();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet s = wb.getSheet("Orar");
            assertThat(s).as("FSGC sheet exists").isNotNull();

            // super-header (row 0) carries the an-de-studiu label
            assertThat(cell(s, 0, 2)).isEqualTo("LICENȚĂ/ANUL I");
            // specialization sub-headers (row 1), sorted: AP before SP
            assertThat(cell(s, 1, 2)).isEqualTo("AP");
            assertThat(cell(s, 1, 3)).isEqualTo("SP");
            // day label + interval labels in the first two columns
            assertThat(cell(s, 2, 0)).isEqualTo("LUNI");
            assertThat(cell(s, 4, 1)).isEqualTo("9:40-11:10");
            assertThat(cell(s, 5, 1)).isEqualTo("Sala");
            // activity text on the module row, room on the row below, for the AP column
            assertThat(cell(s, 4, 2)).isEqualTo("Sisteme administrative, Draganescu, c");
            assertThat(cell(s, 5, 2)).isEqualTo("Paris 01");
            // the common course is one merged cell spanning AP (col 2) and SP (col 3)
            boolean mergedAcrossSpecs = s.getMergedRegions().stream().anyMatch(r ->
                    r.getFirstRow() == 4 && r.getFirstColumn() == 2 && r.getLastColumn() == 3);
            assertThat(mergedAcrossSpecs).as("common course merged across specializations").isTrue();
        }
    }

    private static String cell(Sheet s, int row, int col) {
        var r = s.getRow(row);
        if (r == null) return null;
        var c = r.getCell(col);
        return c == null ? null : c.getStringCellValue();
    }

    private static final LocalTime[][] MODULE_TIMES = {
            {LocalTime.of(8, 0), LocalTime.of(9, 30)},
            {LocalTime.of(9, 40), LocalTime.of(11, 10)},
            {LocalTime.of(11, 20), LocalTime.of(12, 50)},
            {LocalTime.of(13, 0), LocalTime.of(14, 30)},
            {LocalTime.of(14, 40), LocalTime.of(16, 10)},
            {LocalTime.of(16, 20), LocalTime.of(17, 50)},
            {LocalTime.of(18, 0), LocalTime.of(19, 30)},
            {LocalTime.of(19, 40), LocalTime.of(21, 10)},
    };

    private static List<TimeSlot> buildWeek() {
        java.util.List<TimeSlot> all = new java.util.ArrayList<>();
        long id = 1;
        for (DayOfWeek d : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            for (int m = 1; m <= 8; m++) {
                all.add(slot(id++, d, m, MODULE_TIMES[m - 1][0], MODULE_TIMES[m - 1][1]));
            }
        }
        return all;
    }

    private static TimeSlot slot(long id, DayOfWeek d, int idx, LocalTime from, LocalTime to) {
        TimeSlot t = new TimeSlot();
        t.setId(id);
        t.setDayOfWeek(d);
        t.setSlotIndex(idx);
        t.setStartTime(from);
        t.setEndTime(to);
        return t;
    }

    private static StudentGroup group(String name, String spec, int year, StudyProgram p) {
        StudentGroup g = new StudentGroup();
        g.setName(name);
        g.setSpecialization(spec);
        g.setYear(year);
        g.setStudyProgram(p);
        g.setStudentCount(25);
        return g;
    }

    private static ScheduledActivity activity(long id, String subjectName, String code, String prof,
            ActivityType type, TimeSlot ts, String room, Set<StudentGroup> groups) {
        Subject subj = new Subject();
        subj.setName(subjectName);
        subj.setCode(code);
        Professor p = new Professor();
        p.setName(prof);
        Room r = new Room();
        r.setName(room);
        ScheduledActivity a = new ScheduledActivity();
        a.setId(id);
        a.setSubject(subj);
        a.setProfessor(p);
        a.setActivityType(type);
        a.setTimeSlot(ts);
        a.setRoom(r);
        a.setStudentGroups(groups);
        return a;
    }
}
