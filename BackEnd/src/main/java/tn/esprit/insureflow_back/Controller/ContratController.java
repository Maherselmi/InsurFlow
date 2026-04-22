package tn.esprit.insureflow_back.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.Domain.Entities.ContratDocument;
import tn.esprit.insureflow_back.Service.ContratVectorService;
import tn.esprit.insureflow_back.Service.PDFProcessingService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/contrats")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class ContratController {

    private final PDFProcessingService pdfProcessingService;
    private final ContratVectorService contratVectorService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadPDF(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "typeContrat", required = false) String typeContrat
    ) {
        try {
            List<ContratDocument> docs = pdfProcessingService.processPDF(file, typeContrat);
            contratVectorService.saveToVectorDB(docs);

            return ResponseEntity.ok("✅ PDF uploadé et injecté dans Milvus");

        } catch (IOException e) {
            return ResponseEntity
                    .internalServerError()
                    .body("❌ Erreur traitement PDF: " + e.getMessage());
        }
    }
}