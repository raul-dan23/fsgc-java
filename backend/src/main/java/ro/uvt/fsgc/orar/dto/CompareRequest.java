package ro.uvt.fsgc.orar.dto;

import java.util.List;

/** Body of POST /api/timetable/compare: a list of time budgets (seconds) to try successively. */
public class CompareRequest {
    private List<Integer> budgets;

    public List<Integer> getBudgets() {
        return budgets;
    }

    public void setBudgets(List<Integer> budgets) {
        this.budgets = budgets;
    }
}
