package ro.uvt.fsgc.orar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.Room;
import ro.uvt.fsgc.orar.domain.RoomTypology;
import ro.uvt.fsgc.orar.domain.ScheduledActivity;
import ro.uvt.fsgc.orar.domain.StudentGroup;
import ro.uvt.fsgc.orar.domain.StudyProgram;
import ro.uvt.fsgc.orar.domain.Subject;

/**
 * "No room is big enough" is only a blocker for an hour that needs a room. A 176-student course
 * held online needs none, and telling the user it can never be placed sends them chasing a room
 * that is not required.
 */
class StructuralBlockersTest {

    private static ScheduledActivity course(String name, int students, boolean online) {
        ScheduledActivity a = new ScheduledActivity();
        a.setId(1L);
        Subject s = new Subject();
        s.setName(name);
        s.setCode("X1");
        a.setSubject(s);
        a.setActivityType(ActivityType.COURSE);
        a.setOnline(online);
        StudentGroup g = new StudentGroup();
        g.setId(1L);
        g.setName("G");
        g.setStudentCount(students);
        g.setStudyProgram(StudyProgram.LICENSE);
        a.setStudentGroups(Set.of(g));
        return a;
    }

    private static final List<Room> ROOMS = List.of(room(136));

    private static Room room(int capacity) {
        Room r = new Room();
        r.setId(1L);
        r.setName("A03");
        r.setCapacity(capacity);
        r.setTypology(RoomTypology.AMPHITHEATER);
        return r;
    }

    @Test
    void anOnSiteCourseBiggerThanEveryRoomIsABlocker() {
        List<String> blockers = GenerationAdvisor.structuralBlockers(
                List.of(course("Deontologie", 176, false)), ROOMS, 40, 15, 1);
        assertEquals(1, blockers.size());
        assertTrue(blockers.get(0).contains("176"), blockers.get(0));
    }

    @Test
    void theSameCourseOnlineIsNot() {
        assertEquals(List.of(), GenerationAdvisor.structuralBlockers(
                List.of(course("Deontologie", 176, true)), ROOMS, 40, 15, 1));
    }
}
