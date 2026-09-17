package ro.uvt.fsgc.orar.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uvt.fsgc.orar.domain.ConstraintWeights;
import ro.uvt.fsgc.orar.repository.ConstraintWeightsRepository;

/** Get/update the tunable soft-constraint weights (the single configuration row). */
@RestController
@RequestMapping("/api/weights")
@CrossOrigin
public class WeightsController {

    private final ConstraintWeightsRepository repo;

    public WeightsController(ConstraintWeightsRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ConstraintWeights get() {
        return repo.findById(ConstraintWeights.SINGLETON_ID).orElseGet(() -> {
            ConstraintWeights w = new ConstraintWeights();
            return repo.save(w);
        });
    }

    @PutMapping
    public ResponseEntity<ConstraintWeights> update(@RequestBody ConstraintWeights incoming) {
        ConstraintWeights w = repo.findById(ConstraintWeights.SINGLETON_ID).orElseGet(ConstraintWeights::new);
        w.setId(ConstraintWeights.SINGLETON_ID);
        w.setDailyLoadBalance(incoming.getDailyLoadBalance());
        w.setGroupGap(incoming.getGroupGap());
        w.setLateHoursLicense(incoming.getLateHoursLicense());
        w.setCompactness(incoming.getCompactness());
        w.setGlobalWeeklyBalance(incoming.getGlobalWeeklyBalance());
        w.setProfessorPreference(incoming.getProfessorPreference());
        w.setParityPairTogether(incoming.getParityPairTogether());
        return ResponseEntity.ok(repo.save(w));
    }
}
