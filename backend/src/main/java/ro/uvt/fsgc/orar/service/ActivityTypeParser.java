package ro.uvt.fsgc.orar.service;

import java.util.List;
import ro.uvt.fsgc.orar.domain.ActivityType;
import ro.uvt.fsgc.orar.domain.SpecialCategory;
import ro.uvt.fsgc.orar.domain.WeekParity;

/**
 * Translates the messy Excel {@code activitate} values into one or more concrete
 * activity specs. See the project rules:
 * <ul>
 *   <li>any {@code Curs*} -> COURSE (Curs Amfiteatru also sets requiresAmphitheater)</li>
 *   <li>any {@code Seminar*} -> SEMINAR</li>
 *   <li>{@code DCT} -> SEMINAR with category DCT (transversal discipline)</li>
 *   <li>SI = saptamani impare = ODD_WEEKS, SP = saptamani pare = EVEN_WEEKS</li>
 *   <li>combined values like {@code Curs(SI)/Seminar(SP)} split into two specs with
 *       different parity; {@code Curs/Seminar} -> COURSE odd + SEMINAR even</li>
 * </ul>
 */
public final class ActivityTypeParser {

    /** One concrete activity to materialize from a (possibly combined) raw value. */
    public record ActivitySpec(ActivityType type, WeekParity parity,
                               boolean requiresAmphitheater, SpecialCategory category) {
    }

    private ActivityTypeParser() {
    }

    /** Returns the list of activities to create for one Discipline row, or empty if unparseable. */
    public static List<ActivitySpec> parse(String raw) {
        if (raw == null) {
            return List.of();
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return List.of();
        }
        String lower = v.toLowerCase();

        // DCT transversal discipline
        if (lower.equals("dct") || lower.startsWith("dct")) {
            return List.of(new ActivitySpec(ActivityType.SEMINAR, WeekParity.EVERY_WEEK, false, SpecialCategory.DCT));
        }

        // Combined / parity-encoded values contain a slash
        if (v.contains("/")) {
            return parseCombined(v);
        }

        return List.of(parseSingle(v, WeekParity.EVERY_WEEK));
    }

    private static List<ActivitySpec> parseCombined(String v) {
        String[] segments = v.split("/");
        // If segments carry explicit (SI)/(SP) parity, honor it; otherwise default odd/even.
        boolean hasExplicitParity = v.contains("(") && (v.toUpperCase().contains("SI") || v.toUpperCase().contains("SP"));
        java.util.List<ActivitySpec> result = new java.util.ArrayList<>();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i].trim();
            WeekParity parity;
            if (hasExplicitParity) {
                String up = seg.toUpperCase();
                if (up.contains("(SI)") || up.contains("SI)")) {
                    parity = WeekParity.ODD_WEEKS;
                } else if (up.contains("(SP)") || up.contains("SP)")) {
                    parity = WeekParity.EVEN_WEEKS;
                } else {
                    parity = (i == 0) ? WeekParity.ODD_WEEKS : WeekParity.EVEN_WEEKS;
                }
            } else {
                // e.g. "Curs/Seminar": first segment odd, second even
                parity = (i == 0) ? WeekParity.ODD_WEEKS : WeekParity.EVEN_WEEKS;
            }
            result.add(parseSingle(seg, parity));
        }
        return result;
    }

    private static ActivitySpec parseSingle(String seg, WeekParity parity) {
        String lower = seg.toLowerCase();
        ActivityType type = lower.startsWith("curs") ? ActivityType.COURSE
                : lower.startsWith("lab") ? ActivityType.LAB
                : ActivityType.SEMINAR;
        boolean amphitheater = lower.contains("amfi") || lower.contains("amph");
        return new ActivitySpec(type, parity, amphitheater, SpecialCategory.NORMAL);
    }
}
