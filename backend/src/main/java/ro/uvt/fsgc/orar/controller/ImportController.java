package ro.uvt.fsgc.orar.controller;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.uvt.fsgc.orar.dto.ImportResult;
import ro.uvt.fsgc.orar.service.ExcelImportService;

@RestController
@RequestMapping("/api/import")
@CrossOrigin
public class ImportController {

    private final ExcelImportService importService;

    public ImportController(ExcelImportService importService) {
        this.importService = importService;
    }

    /**
     * Imports the 4-sheet semester workbook. Returns 200 + counts on success, or 422 + the
     * list of validation errors (with Excel row numbers) on failure — never a bare stacktrace.
     */
    @PostMapping(value = "/excel")
    public ResponseEntity<ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            ImportResult r = new ImportResult();
            r.addError("(request)", 0, "No file uploaded");
            return ResponseEntity.badRequest().body(r);
        }
        try {
            ImportResult result = importService.importWorkbook(file.getInputStream());
            return ResponseEntity.ok(result);
        } catch (ExcelImportService.ImportFailedException e) {
            return ResponseEntity.unprocessableEntity().body(e.getResult());
        }
    }
}
