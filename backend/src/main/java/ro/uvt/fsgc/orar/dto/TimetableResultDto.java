package ro.uvt.fsgc.orar.dto;

import java.util.ArrayList;
import java.util.List;

/** Full result of a generate job: status, score breakdown, conflicts, and the placed activities. */
public class TimetableResultDto {
    private String jobId;
    private String state;            // SOLVING | COMPLETED | FAILED
    private String error;
    private String score;
    private long hardScore;
    private long mediumScore;
    private long softScore;
    private int totalActivities;
    private int unassignedCount;
    private long solveMillis;
    private List<ConflictItem> conflicts = new ArrayList<>();
    private List<ActivityView> activities = new ArrayList<>();

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }
    public long getHardScore() { return hardScore; }
    public void setHardScore(long v) { this.hardScore = v; }
    public long getMediumScore() { return mediumScore; }
    public void setMediumScore(long v) { this.mediumScore = v; }
    public long getSoftScore() { return softScore; }
    public void setSoftScore(long v) { this.softScore = v; }
    public int getTotalActivities() { return totalActivities; }
    public void setTotalActivities(int v) { this.totalActivities = v; }
    public int getUnassignedCount() { return unassignedCount; }
    public void setUnassignedCount(int v) { this.unassignedCount = v; }
    public long getSolveMillis() { return solveMillis; }
    public void setSolveMillis(long v) { this.solveMillis = v; }
    public List<ConflictItem> getConflicts() { return conflicts; }
    public void setConflicts(List<ConflictItem> c) { this.conflicts = c; }
    public List<ActivityView> getActivities() { return activities; }
    public void setActivities(List<ActivityView> a) { this.activities = a; }
}
