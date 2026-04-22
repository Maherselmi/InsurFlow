package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.ClaimDocument;
import tn.esprit.insureflow_back.Repository.ClaimDocumentRepository;
import tn.esprit.insureflow_back.Repository.ClaimRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class ClaimDocumentService {

    private final ClaimDocumentRepository documentRepository;
    private final ClaimRepository claimRepository;
    private final String UPLOAD_DIR = "uploads/";  // ✅ OrchestratorService supprimé

    public ClaimDocument uploadFile(Long claimId, MultipartFile file) throws IOException {

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new RuntimeException("Claim not found"));

        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) dir.mkdirs();

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(UPLOAD_DIR + fileName);
        Files.write(filePath, file.getBytes());

        ClaimDocument doc = new ClaimDocument();
        doc.setFileName(fileName);
        doc.setFileType(file.getContentType());
        doc.setFilePath(filePath.toString());
        doc.setClaim(claim);

        // ✅ Sauvegarder uniquement — orchestrateur supprimé
        return documentRepository.save(doc);
    }
}
