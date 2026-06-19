package ro.uvt.fsgc.orar.dto;

/** Body of POST /api/timetable/generate. terminationSeconds is the per-request time budget. */
public class GenerateRequest {
    private Integer terminationSeconds;

    public Integer getTerminationSeconds() {
        return terminationSeconds;
    }

    public void setTerminationSeconds(Integer terminationSeconds) {
        this.terminationSeconds = terminationSeconds;
    }
}
