package ro.uvt.fsgc.orar.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of an Excel import. {@code errors} block the transactional save (none = success);
 * {@code warnings} are non-fatal (e.g. an unparseable activity row that was skipped).
 */
public class ImportResult {

    /** A single problem tied to a sheet + 1-based Excel row number. */
    public record Issue(String sheet, int row, String message) {
    }

    private boolean success;
    private final List<Issue> errors = new ArrayList<>();
    private final List<Issue> warnings = new ArrayList<>();

    private int departments;
    private int studentGroups;
    private int professors;
    private int rooms;
    private int roomAvailabilities;
    private int subjects;
    private int activities;

    public void addError(String sheet, int row, String message) {
        errors.add(new Issue(sheet, row, message));
    }

    public void addWarning(String sheet, int row, String message) {
        warnings.add(new Issue(sheet, row, message));
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<Issue> getErrors() {
        return errors;
    }

    public List<Issue> getWarnings() {
        return warnings;
    }

    public int getDepartments() {
        return departments;
    }

    public void setDepartments(int departments) {
        this.departments = departments;
    }

    public int getStudentGroups() {
        return studentGroups;
    }

    public void setStudentGroups(int studentGroups) {
        this.studentGroups = studentGroups;
    }

    public int getProfessors() {
        return professors;
    }

    public void setProfessors(int professors) {
        this.professors = professors;
    }

    public int getRooms() {
        return rooms;
    }

    public void setRooms(int rooms) {
        this.rooms = rooms;
    }

    public int getRoomAvailabilities() {
        return roomAvailabilities;
    }

    public void setRoomAvailabilities(int roomAvailabilities) {
        this.roomAvailabilities = roomAvailabilities;
    }

    public int getSubjects() {
        return subjects;
    }

    public void setSubjects(int subjects) {
        this.subjects = subjects;
    }

    public int getActivities() {
        return activities;
    }

    public void setActivities(int activities) {
        this.activities = activities;
    }
}
