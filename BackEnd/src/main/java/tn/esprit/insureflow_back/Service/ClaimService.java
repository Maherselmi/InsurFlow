package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.insureflow_back.DTO.ClaimConversationDraft;
import tn.esprit.insureflow_back.DTO.DraftDocument;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimStatus;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.ClaimDocument;
import tn.esprit.insureflow_back.Domain.Entities.Client;
import tn.esprit.insureflow_back.Domain.Entities.Policy;
import tn.esprit.insureflow_back.Repository.ClaimDocumentRepository;
import tn.esprit.insureflow_back.Repository.ClaimRepository;
import tn.esprit.insureflow_back.Repository.ClientRepository;
import tn.esprit.insureflow_back.Repository.PolicyRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final PolicyRepository policyRepository;
    private final ClientRepository clientRepository;
    private final ClaimDocumentRepository claimDocumentRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Transactional
    public Claim createClaim(Claim claim) {

        if (claim == null) {
            throw new RuntimeException("Claim is required");
        }

        if (claim.getPolicy() != null && claim.getPolicy().getId() != null) {
            Policy existingPolicy = policyRepository.findById(claim.getPolicy().getId())
                    .orElseThrow(() -> new RuntimeException("Policy not found"));

            claim.setPolicy(existingPolicy);

            if (existingPolicy.getClient() != null) {
                claim.setClient(existingPolicy.getClient());
            }
        }

        claim.setStatus(ClaimStatus.PENDING_VALIDATION);

        return claimRepository.save(claim);
    }

    public List<Claim> getAllClaims() {
        return claimRepository.findAllWithClient();
    }

    public Claim getClaimById(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Claim not found with id: " + id));
    }

    public List<Claim> getClaimsByClientId(Long clientId) {
        if (clientId == null) {
            throw new RuntimeException("Client id is required");
        }

        clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return claimRepository.findClaimsByClientId(clientId);
    }

    @Transactional
    public Claim createClaimFromConversation(ClaimConversationDraft draft) {

        validateDraft(draft);

        Client client = clientRepository.findById(draft.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found"));

        Policy policy = policyRepository.findById(draft.getPolicyId())
                .orElseThrow(() -> new RuntimeException("Policy not found"));

        if (policy.getClient() == null || !policy.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Cette police n'appartient pas au client connecté");
        }

        Claim claim = new Claim();
        claim.setClient(client);
        claim.setPolicy(policy);
        claim.setIncidentDate(draft.getIncidentDate());
        claim.setDescription(draft.getDescription());
        claim.setStatus(ClaimStatus.PENDING_VALIDATION);

        Claim savedClaim = claimRepository.save(claim);

        saveDraftDocuments(savedClaim, draft.getDocuments());

        return savedClaim;
    }

    private void validateDraft(ClaimConversationDraft draft) {
        if (draft == null) {
            throw new RuntimeException("Déclaration invalide");
        }

        if (draft.getClientId() == null) {
            throw new RuntimeException("Client manquant");
        }

        if (draft.getPolicyId() == null) {
            throw new RuntimeException("Police manquante");
        }

        if (draft.getIncidentDate() == null) {
            throw new RuntimeException("Date incident manquante");
        }

        if (draft.getDescription() == null || draft.getDescription().isBlank()) {
            throw new RuntimeException("Description manquante");
        }
    }

    private void saveDraftDocuments(
            Claim claim,
            List<DraftDocument> documents
    ) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            for (DraftDocument draftDocument : documents) {
                if (draftDocument == null
                        || draftDocument.getContent() == null
                        || draftDocument.getContent().length == 0) {
                    continue;
                }

                String originalFileName = draftDocument.getFileName() != null
                        ? draftDocument.getFileName()
                        : "document";

                String safeFileName = sanitizeFileName(originalFileName);

                String storedFileName = System.currentTimeMillis()
                        + "_"
                        + UUID.randomUUID()
                        + "_"
                        + safeFileName;

                Path targetPath = uploadPath.resolve(storedFileName).normalize();

                if (!targetPath.startsWith(uploadPath)) {
                    throw new RuntimeException("Nom de fichier invalide");
                }

                Files.write(targetPath, draftDocument.getContent());

                ClaimDocument claimDocument = new ClaimDocument();
                claimDocument.setClaim(claim);
                claimDocument.setFileName(originalFileName);
                claimDocument.setFileType(draftDocument.getContentType());
                claimDocument.setFilePath(targetPath.toString());

                claimDocumentRepository.save(claimDocument);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la sauvegarde des documents : " + e.getMessage(), e);
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName
                .replace("\\", "_")
                .replace("/", "_")
                .replace("..", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}