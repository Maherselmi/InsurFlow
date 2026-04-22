package tn.esprit.insureflow_back.Service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.ClaimDocument;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
public class ClaimPdfExtractorService {

    public String extractTextFromClaim(Claim claim) {

        StringBuilder fullText = new StringBuilder();

        // Parcourir tous les documents du dossier sinistre
        for (ClaimDocument doc : claim.getDocuments()) {

            String filePath = doc.getFilePath();

            // Traiter uniquement les PDF
            if (filePath != null && filePath.toLowerCase().endsWith(".pdf")) {

                log.info("📄 Extraction PDF: {}", doc.getFileName());

                try {
                    File pdfFile = new File(filePath);

                    if (!pdfFile.exists()) {
                        log.warn("⚠️ Fichier introuvable: {}", filePath);
                        continue;
                    }

                    PDDocument pdDocument = PDDocument.load(pdfFile);
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(pdDocument);
                    pdDocument.close();

                    fullText.append("=== Fichier: ").append(doc.getFileName()).append(" ===\n");
                    fullText.append(text).append("\n\n");

                    log.info("✅ Texte extrait: {} caractères", text.length());

                } catch (IOException e) {
                    log.error("❌ Erreur extraction PDF {}: {}", doc.getFileName(), e.getMessage());
                }
            }
        }

        return fullText.toString();
    }
}