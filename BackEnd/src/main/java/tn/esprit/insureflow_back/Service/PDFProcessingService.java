package tn.esprit.insureflow_back.Service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.Domain.Entities.ContratDocument;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PDFProcessingService {

    public List<ContratDocument> processPDF(MultipartFile file, String requestedTypeContrat) throws IOException {

        List<ContratDocument> documents = new ArrayList<>();

        try (PDDocument document = PDDocument.load(file.getInputStream())) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            String detectedType = resolveTypeContrat(
                    requestedTypeContrat,
                    file.getOriginalFilename(),
                    text
            );

            log.info("📄 PDF={} | typeContrat détecté={}", file.getOriginalFilename(), detectedType);

            List<String> chunks = splitIntoChunks(text, 500);

            for (int i = 0; i < chunks.size(); i++) {
                ContratDocument doc = ContratDocument.builder()
                        .id(file.getOriginalFilename() + "_chunk_" + i)
                        .fileName(file.getOriginalFilename())
                        .content(chunks.get(i))
                        .typeContrat(detectedType)
                        .pageNumber(String.valueOf(i + 1))
                        .source("PDF_UPLOAD")
                        .build();

                documents.add(doc);
            }
        }

        return documents;
    }

    private List<String> splitIntoChunks(String text, int size) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        for (int i = 0; i < text.length(); i += size) {
            int end = Math.min(text.length(), i + size);
            chunks.add(text.substring(i, end));
        }

        return chunks;
    }

    private String resolveTypeContrat(String requestedTypeContrat, String fileName, String fullText) {

        // 1) priorité au type envoyé explicitement depuis le frontend
        String explicitType = normalizeTypeValue(requestedTypeContrat);
        if (!explicitType.equals("INCONNU")) {
            return explicitType;
        }

        // 2) fallback sur le nom du fichier
        String normalizedFileName = normalize(fileName);

        if (containsAny(normalizedFileName,
                "assurance_sante", "contrat_sante", "conditions_generales_assurance_sante", "sante")) {
            return "SANTE";
        }

        if (containsAny(normalizedFileName,
                "assurance_auto", "contrat_auto", "conditions_generales_assurance_auto", "vehicule")) {
            return "AUTO";
        }

        if (containsAny(normalizedFileName,
                "assurance_habitation", "contrat_habitation", "conditions_generales_assurance_habitation", "habitation")) {
            return "HABITATION";
        }

        // 3) fallback sur le contenu du PDF
        String normalizedText = normalize(fullText);

        if (isSanteContract(normalizedText)) {
            return "SANTE";
        }

        if (isAutoContract(normalizedText)) {
            return "AUTO";
        }

        if (isHabitationContract(normalizedText)) {
            return "HABITATION";
        }

        return "INCONNU";
    }

    private String normalizeTypeValue(String value) {
        String normalized = normalize(value);

        if (normalized.equals("auto")) return "AUTO";
        if (normalized.equals("sante")) return "SANTE";
        if (normalized.equals("habitation")) return "HABITATION";

        return "INCONNU";
    }

    private boolean isSanteContract(String text) {
        return matches(text,
                "\\bassurance sante\\b",
                "\\bcontrat d assurance sante\\b",
                "\\bprestataire de soins\\b",
                "\\bcnam\\b",
                "\\bhospitalisation\\b",
                "\\bconsultation medecin\\b",
                "\\bmedicaments\\b",
                "\\baffection de longue duree\\b",
                "\\bmaternite\\b"
        );
    }

    private boolean isAutoContract(String text) {
        return matches(text,
                "\\bassurance auto\\b",
                "\\bvehicule assure\\b",
                "\\bimmatriculation\\b",
                "\\bresponsabilite civile\\b",
                "\\bcollision\\b",
                "\\bvol du vehicule\\b",
                "\\bincendie du vehicule\\b",
                "\\bdommages materiels\\b"
        );
    }

    private boolean isHabitationContract(String text) {
        return matches(text,
                "\\bassurance habitation\\b",
                "\\blogement\\b",
                "\\bdegat des eaux\\b",
                "\\bincendie\\b",
                "\\bvol\\b",
                "\\bresponsabilite civile habitation\\b"
        );
    }

    private boolean matches(String text, String... regexes) {
        for (String regex : regexes) {
            if (Pattern.compile(regex).matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... values) {
        if (text == null) return false;
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String noAccent = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return noAccent.toLowerCase(Locale.ROOT).trim();
    }
}