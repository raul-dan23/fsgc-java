package ro.uvt.fsgc.orar.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uvt.fsgc.orar.service.ExcelExportService;

@RestController
@RequestMapping("/api/export")
@CrossOrigin
public class ExportController {

    private final ExcelExportService exportService;

    public ExportController(ExcelExportService exportService) {
        this.exportService = exportService;
    }

    /** Downloads the current timetable as .xlsx (Master + Pe Grupa / Pe Sala / Pe Cadru Didactic). */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportExcel() {
        byte[] body = exportService.export();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orar.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
