package ro.uvt.fsgc.orar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.WeekParity;
import ro.uvt.fsgc.orar.service.ActivityTypeParser.ActivitySpec;

class ActivityTypeParserTest {

    // ------------------------------------------------------------------ null / empty

    @Test
    void parse_null_returnsEmptyList() {
        assertThat(ActivityTypeParser.parse(null)).isEmpty();
    }

    @Test
    void parse_emptyString_returnsEmptyList() {
        assertThat(ActivityTypeParser.parse("")).isEmpty();
    }

    @Test
    void parse_blankString_returnsEmptyList() {
        assertThat(ActivityTypeParser.parse("   ")).isEmpty();
    }

    // ------------------------------------------------------------------ DCT

    @Test
    void parse_DCT_returnsSeminarEveryWeekDctCategory() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("DCT");

        assertThat(specs).hasSize(1);
        ActivitySpec spec = specs.get(0);
        assertThat(spec.type()).isEqualTo(ActivityType.SEMINAR);
        assertThat(spec.parity()).isEqualTo(WeekParity.EVERY_WEEK);
        assertThat(spec.requiresAmphitheater()).isFalse();
        assertThat(spec.category()).isEqualTo(SpecialCategory.DCT);
    }

    // ------------------------------------------------------------------ Curs

    @Test
    void parse_Curs_returnsCourseEveryWeekNormal() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Curs");

        assertThat(specs).hasSize(1);
        ActivitySpec spec = specs.get(0);
        assertThat(spec.type()).isEqualTo(ActivityType.COURSE);
        assertThat(spec.parity()).isEqualTo(WeekParity.EVERY_WEEK);
        assertThat(spec.requiresAmphitheater()).isFalse();
        assertThat(spec.category()).isEqualTo(SpecialCategory.NORMAL);
    }

    @Test
    void parse_CursAmfiteatru_requiresAmphitheater() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Curs Amfiteatru");

        assertThat(specs).hasSize(1);
        ActivitySpec spec = specs.get(0);
        assertThat(spec.type()).isEqualTo(ActivityType.COURSE);
        assertThat(spec.parity()).isEqualTo(WeekParity.EVERY_WEEK);
        assertThat(spec.requiresAmphitheater()).isTrue();
        assertThat(spec.category()).isEqualTo(SpecialCategory.NORMAL);
    }

    // ------------------------------------------------------------------ Laborator

    @Test
    void parse_Laborator_returnsLabEveryWeek() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Laborator");

        assertThat(specs).hasSize(1);
        ActivitySpec spec = specs.get(0);
        assertThat(spec.type()).isEqualTo(ActivityType.LAB);
        assertThat(spec.parity()).isEqualTo(WeekParity.EVERY_WEEK);
        assertThat(spec.requiresAmphitheater()).isFalse();
        assertThat(spec.category()).isEqualTo(SpecialCategory.NORMAL);
    }

    // ------------------------------------------------------------------ Seminar

    @Test
    void parse_Seminar_returnsSeminarEveryWeek() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar");

        assertThat(specs).hasSize(1);
        ActivitySpec spec = specs.get(0);
        assertThat(spec.type()).isEqualTo(ActivityType.SEMINAR);
        assertThat(spec.parity()).isEqualTo(WeekParity.EVERY_WEEK);
        assertThat(spec.requiresAmphitheater()).isFalse();
        assertThat(spec.category()).isEqualTo(SpecialCategory.NORMAL);
    }

    // ------------------------------------------------------------------ Combined without explicit parity

    @Test
    void parse_CursSlashSeminar_noParen_returnsCourseOddAndSeminarEven() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Curs/Seminar");

        assertThat(specs).hasSize(2);

        ActivitySpec first = specs.get(0);
        assertThat(first.type()).isEqualTo(ActivityType.COURSE);
        assertThat(first.parity()).isEqualTo(WeekParity.ODD_WEEKS);
        assertThat(first.category()).isEqualTo(SpecialCategory.NORMAL);

        ActivitySpec second = specs.get(1);
        assertThat(second.type()).isEqualTo(ActivityType.SEMINAR);
        assertThat(second.parity()).isEqualTo(WeekParity.EVEN_WEEKS);
        assertThat(second.category()).isEqualTo(SpecialCategory.NORMAL);
    }

    // ------------------------------------------------------------------ Combined with explicit parity markers

    @Test
    void parse_CursSISlashSeminarSP_returnsCourseOddAndSeminarEven() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Curs(SI)/Seminar(SP)");

        assertThat(specs).hasSize(2);

        ActivitySpec first = specs.get(0);
        assertThat(first.type()).isEqualTo(ActivityType.COURSE);
        assertThat(first.parity()).isEqualTo(WeekParity.ODD_WEEKS);

        ActivitySpec second = specs.get(1);
        assertThat(second.type()).isEqualTo(ActivityType.SEMINAR);
        assertThat(second.parity()).isEqualTo(WeekParity.EVEN_WEEKS);
    }

    @Test
    void parse_SeminarSPSlashCursSI_returnsSeminarEvenAndCourseOdd() {
        List<ActivitySpec> specs = ActivityTypeParser.parse("Seminar(SP)/Curs(SI)");

        assertThat(specs).hasSize(2);

        ActivitySpec first = specs.get(0);
        assertThat(first.type()).isEqualTo(ActivityType.SEMINAR);
        assertThat(first.parity()).isEqualTo(WeekParity.EVEN_WEEKS);

        ActivitySpec second = specs.get(1);
        assertThat(second.type()).isEqualTo(ActivityType.COURSE);
        assertThat(second.parity()).isEqualTo(WeekParity.ODD_WEEKS);
    }
}
