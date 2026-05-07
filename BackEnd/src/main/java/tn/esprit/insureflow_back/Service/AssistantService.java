package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.insureflow_back.DTO.AssistantRequest;
import tn.esprit.insureflow_back.DTO.AssistantResponse;
import tn.esprit.insureflow_back.DTO.ClaimConversationDraft;
import tn.esprit.insureflow_back.DTO.DraftDocument;
import tn.esprit.insureflow_back.Domain.ENUMS.ClaimConversationStep;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.Policy;
import tn.esprit.insureflow_back.Orchestrator.OrchestratorService;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AssistantService {

    private final ChatClient assistantChatClient;
    private final PolicyService policyService;
    private final ClaimService claimService;
    private final OrchestratorService orchestratorService;

    private final Map<Long, ClaimConversationDraft> declarationDrafts = new ConcurrentHashMap<>();

    public AssistantResponse ask(AssistantRequest request) {

        String userMessage = request.getMessage() != null
                ? request.getMessage().trim()
                : "";

        String normalizedMessage = userMessage.toLowerCase(Locale.ROOT);

        Long clientId = request.getClientId();

        if (clientId == null) {
            return new AssistantResponse(
                    "Veuillez vous connecter pour utiliser l’assistant InsurFlow."
            );
        }

        ClaimConversationDraft existingDraft = declarationDrafts.get(clientId);

        if (existingDraft != null && existingDraft.getStep() != ClaimConversationStep.NONE) {
            return continueClaimDeclaration(clientId, userMessage, existingDraft);
        }

        if (isStartClaimDeclaration(normalizedMessage)) {
            return startClaimDeclaration(clientId);
        }

        if (isPolicyRequest(normalizedMessage)) {
            return getClientPoliciesResponse(clientId);
        }

        if (isClaimRequest(normalizedMessage)) {
            return getClientClaimsResponse(clientId);
        }

        return generalInsuranceAnswer(userMessage);
    }

    public AssistantResponse uploadClaimDocuments(
            Long clientId,
            List<MultipartFile> documents
    ) {
        if (clientId == null) {
            return new AssistantResponse(
                    "Veuillez vous connecter pour joindre les documents."
            );
        }

        ClaimConversationDraft draft = declarationDrafts.get(clientId);

        if (draft == null || draft.getStep() != ClaimConversationStep.DOCUMENTS) {
            return new AssistantResponse(
                    "Aucune déclaration de sinistre n’est actuellement en attente de documents."
            );
        }

        if (documents == null || documents.isEmpty()) {
            return new AssistantResponse(
                    "Aucun document reçu. Veuillez joindre au moins un fichier ou écrire : continuer sans document.",
                    true,
                    true
            );
        }

        try {
            for (MultipartFile file : documents) {
                if (file != null && !file.isEmpty()) {
                    draft.getDocuments().add(
                            new DraftDocument(
                                    file.getOriginalFilename(),
                                    file.getContentType(),
                                    file.getBytes()
                            )
                    );
                }
            }

            draft.setStep(ClaimConversationStep.CONFIRMATION);

            return buildConfirmationMessage(draft);

        } catch (Exception e) {
            return new AssistantResponse(
                    "Erreur lors de la réception des documents. Veuillez réessayer.",
                    true,
                    true
            );
        }
    }

    private boolean isStartClaimDeclaration(String message) {
        return message.contains("déclarer un sinistre")
                || message.contains("declarer un sinistre")
                || message.contains("nouveau sinistre")
                || message.contains("je veux déclarer")
                || message.contains("je veux declarer")
                || message.contains("déclaration de sinistre")
                || message.contains("declaration de sinistre")
                || message.contains("j'ai eu un accident")
                || message.contains("j ai eu un accident")
                || message.contains("j'ai subi")
                || message.contains("j ai subi")
                || message.contains("dégât")
                || message.contains("degat")
                || message.contains("accident de voiture")
                || message.contains("accident auto");
    }

    private AssistantResponse startClaimDeclaration(Long clientId) {

        ClaimConversationDraft draft = new ClaimConversationDraft();
        draft.setClientId(clientId);
        draft.setStep(ClaimConversationStep.CHOOSE_CLAIM_TYPE);

        declarationDrafts.put(clientId, draft);

        String response = """
                Très bien, je vais vous aider à déclarer un sinistre.

                Quel type de sinistre voulez-vous déclarer ?

                - AUTO
                - SANTE
                - HABITATION
                - VOYAGE
                - VIE

                Exemple de réponse : AUTO
                """;

        return new AssistantResponse(response, true, false);
    }

    private AssistantResponse continueClaimDeclaration(
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT).trim();

        if (normalizedMessage.equals("annuler")
                || normalizedMessage.equals("cancel")) {

            declarationDrafts.remove(clientId);

            return new AssistantResponse(
                    "D’accord, la déclaration du sinistre a été annulée."
            );
        }

        return switch (draft.getStep()) {
            case CHOOSE_CLAIM_TYPE -> handleClaimTypeChoice(clientId, message, draft);
            case CHOOSE_POLICY -> handlePolicyChoice(clientId, message, draft);
            case INCIDENT_DATE -> handleIncidentDate(message, draft);
            case DESCRIPTION -> handleDescription(message, draft);
            case DOCUMENTS -> handleDocumentsStep(message, draft);
            case CONFIRMATION -> handleConfirmation(clientId, message, draft);
            default -> {
                declarationDrafts.remove(clientId);
                yield new AssistantResponse(
                        "La déclaration a été réinitialisée. Vous pouvez recommencer en écrivant : je veux déclarer un sinistre."
                );
            }
        };
    }

    private AssistantResponse handleClaimTypeChoice(
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String claimType = normalizeClaimType(message);

        if (claimType == null) {
            return new AssistantResponse(
                    "Je n’ai pas compris le type de sinistre.\n\n" +
                            "Veuillez choisir parmi : AUTO, SANTE, HABITATION, VOYAGE ou VIE.",
                    true,
                    false
            );
        }

        List<Policy> matchingPolicies = policyService.getPoliciesByClientId(clientId)
                .stream()
                .filter(this::isPolicyActive)
                .filter(policy -> isSamePolicyType(policy.getType(), claimType))
                .toList();

        if (matchingPolicies.isEmpty()) {
            return new AssistantResponse(
                    "Je ne trouve aucune police active de type " + claimType + " associée à votre compte.\n\n" +
                            "Vous pouvez choisir un autre type : AUTO, SANTE, HABITATION, VOYAGE ou VIE.",
                    true,
                    false
            );
        }

        draft.setClaimType(claimType);
        draft.setStep(ClaimConversationStep.CHOOSE_POLICY);

        StringBuilder response = new StringBuilder();

        response.append("Très bien. Vous avez choisi un sinistre de type ")
                .append(claimType)
                .append(".\n\n");

        response.append("Voici vos polices actives correspondant à ce type :\n\n");

        for (Policy policy : matchingPolicies) {
            response.append("- ID ")
                    .append(policy.getId())
                    .append(" : ")
                    .append(policy.getPolicyNumber())
                    .append(" — ")
                    .append(policy.getType());

            if (policy.getFormule() != null) {
                response.append(" — Formule : ").append(policy.getFormule());
            }

            if (policy.getEndDate() != null) {
                response.append(" — Fin : ").append(policy.getEndDate());
            }

            response.append("\n");
        }

        response.append("\nQuelle police concerne ce sinistre ? Répondez avec l’ID de la police.");

        return new AssistantResponse(response.toString(), true, false);
    }

    private AssistantResponse handlePolicyChoice(
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        try {
            Long policyId = Long.parseLong(message.trim());

            Policy policy = policyService.getPolicyById(policyId);

            if (policy.getClient() == null || !policy.getClient().getId().equals(clientId)) {
                return new AssistantResponse(
                        "Cette police ne semble pas appartenir à votre compte. Veuillez choisir une police de votre liste.",
                        true,
                        false
                );
            }

            if (!isPolicyActive(policy)) {
                return new AssistantResponse(
                        "Cette police n’est pas active. Veuillez choisir une police active.",
                        true,
                        false
                );
            }

            if (!isSamePolicyType(policy.getType(), draft.getClaimType())) {
                return new AssistantResponse(
                        "Cette police ne correspond pas au type de sinistre choisi : " + draft.getClaimType() + ".\n" +
                                "Veuillez choisir une police affichée dans la liste.",
                        true,
                        false
                );
            }

            draft.setPolicyId(policy.getId());
            draft.setPolicyNumber(policy.getPolicyNumber());
            draft.setPolicyType(policy.getType());
            draft.setStep(ClaimConversationStep.INCIDENT_DATE);

            return new AssistantResponse(
                    "Parfait. Quelle est la date de l’incident ?\n\n" +
                            "Veuillez écrire la date au format : AAAA-MM-JJ\n" +
                            "Exemple : 2026-05-06",
                    true,
                    false
            );

        } catch (NumberFormatException e) {
            return new AssistantResponse(
                    "Je n’ai pas compris votre choix. Veuillez envoyer uniquement l’ID de la police.\n\n" +
                            "Exemple : 1",
                    true,
                    false
            );

        } catch (Exception e) {
            return new AssistantResponse(
                    "Impossible de trouver cette police. Veuillez choisir un ID de police valide dans la liste.",
                    true,
                    false
            );
        }
    }

    private AssistantResponse handleIncidentDate(
            String message,
            ClaimConversationDraft draft
    ) {
        try {
            LocalDate incidentDate = LocalDate.parse(message.trim());

            if (incidentDate.isAfter(LocalDate.now())) {
                return new AssistantResponse(
                        "La date de l’incident ne peut pas être dans le futur. Veuillez saisir une date correcte.",
                        true,
                        false
                );
            }

            draft.setIncidentDate(incidentDate);
            draft.setStep(ClaimConversationStep.DESCRIPTION);

            return new AssistantResponse(
                    "Merci. Maintenant, décrivez en détail ce qui s’est passé.\n\n" +
                            "Vous pouvez préciser :\n" +
                            "- le lieu de l’incident ;\n" +
                            "- les circonstances ;\n" +
                            "- les dommages constatés ;\n" +
                            "- les personnes ou véhicules impliqués ;\n" +
                            "- toute information utile pour l’analyse.",
                    true,
                    false
            );

        } catch (Exception e) {
            return new AssistantResponse(
                    "Format de date invalide. Veuillez écrire la date sous ce format : AAAA-MM-JJ\n\n" +
                            "Exemple : 2026-05-06",
                    true,
                    false
            );
        }
    }

    private AssistantResponse handleDescription(
            String message,
            ClaimConversationDraft draft
    ) {
        if (message == null || message.trim().length() < 20) {
            return new AssistantResponse(
                    "Veuillez donner une description plus détaillée du sinistre pour permettre l’analyse.",
                    true,
                    false
            );
        }

        draft.setDescription(message.trim());
        draft.setStep(ClaimConversationStep.DOCUMENTS);

        String requiredDocuments = getRequiredDocuments(draft.getClaimType());

        return new AssistantResponse(
                "Merci pour ces informations.\n\n" +
                        "Documents nécessaires pour ce type de sinistre :\n\n" +
                        requiredDocuments +
                        "\n\nVeuillez maintenant joindre les documents disponibles dans le chatbot.\n" +
                        "Si vous n’avez aucun document pour le moment, écrivez : continuer sans document.",
                true,
                true
        );
    }

    private AssistantResponse handleDocumentsStep(
            String message,
            ClaimConversationDraft draft
    ) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT).trim();

        if (normalizedMessage.contains("continuer sans document")
                || normalizedMessage.contains("sans document")
                || normalizedMessage.contains("je n'ai pas")
                || normalizedMessage.contains("je nai pas")) {

            draft.setStep(ClaimConversationStep.CONFIRMATION);

            return buildConfirmationMessage(draft);
        }

        return new AssistantResponse(
                "Veuillez joindre les documents avec le bouton de pièce jointe dans le chatbot.\n\n" +
                        "Si vous n’avez aucun document pour le moment, écrivez : continuer sans document.",
                true,
                true
        );
    }

    private AssistantResponse handleConfirmation(
            Long clientId,
            String message,
            ClaimConversationDraft draft
    ) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT).trim();

        if (normalizedMessage.equals("non")
                || normalizedMessage.equals("annuler")) {

            declarationDrafts.remove(clientId);

            return new AssistantResponse(
                    "D’accord, la déclaration a été annulée."
            );
        }

        if (!normalizedMessage.equals("oui")) {
            return new AssistantResponse(
                    "Veuillez répondre par OUI pour valider la déclaration ou NON pour l’annuler.",
                    true,
                    false
            );
        }

        try {
            Claim savedClaim = claimService.createClaimFromConversation(draft);

            declarationDrafts.remove(clientId);

            orchestratorService.processClaim(savedClaim);

            AssistantResponse response = new AssistantResponse();

            response.setAnswer(
                    "Votre sinistre a été déclaré avec succès.\n\n" +
                            "- Numéro dossier : #" + savedClaim.getId() + "\n" +
                            "- Statut initial : " + savedClaim.getStatus() + "\n\n" +
                            "Les agents d’analyse ont été lancés.\n" +
                            "Votre dossier est maintenant en cours de traitement."
            );

            response.setClaimDeclarationMode(false);
            response.setNeedsFileUpload(false);
            response.setDeclarationCompleted(true);
            response.setClaimId(savedClaim.getId());
            response.setStatus(savedClaim.getStatus().name());

            return response;

        } catch (Exception e) {
            return new AssistantResponse(
                    "Une erreur est survenue lors de la création du sinistre : " +
                            e.getMessage()
            );
        }
    }

    private AssistantResponse buildConfirmationMessage(ClaimConversationDraft draft) {
        StringBuilder response = new StringBuilder();

        response.append("Voici le résumé de votre déclaration :\n\n");

        response.append("- Type de sinistre : ")
                .append(draft.getClaimType())
                .append("\n");

        response.append("- Police : ")
                .append(draft.getPolicyNumber())
                .append(" — ")
                .append(draft.getPolicyType())
                .append("\n");

        response.append("- Date de l’incident : ")
                .append(draft.getIncidentDate())
                .append("\n");

        response.append("- Description : ")
                .append(draft.getDescription())
                .append("\n");

        response.append("- Documents joints : ")
                .append(draft.getDocuments() != null ? draft.getDocuments().size() : 0)
                .append("\n\n");

        response.append("Voulez-vous valider cette déclaration ?\n");
        response.append("Répondez par OUI pour valider ou NON pour annuler.");

        return new AssistantResponse(response.toString(), true, false);
    }

    private String getRequiredDocuments(String claimType) {
        String type = claimType == null
                ? ""
                : claimType.toUpperCase(Locale.ROOT);

        if (type.contains("AUTO")) {
            return """
                    - Photos des dégâts du véhicule ;
                    - Constat amiable si disponible ;
                    - Carte grise ;
                    - Permis de conduire ;
                    - Devis ou facture de réparation si disponible.
                    """;
        }

        if (type.contains("HABITATION")) {
            return """
                    - Photos des dommages ;
                    - Factures des biens endommagés si disponibles ;
                    - Devis de réparation ;
                    - Rapport ou attestation si disponible ;
                    - Tout document prouvant les circonstances du sinistre.
                    """;
        }

        if (type.contains("SANTE") || type.contains("SANTÉ")) {
            return """
                    - Factures médicales ;
                    - Ordonnance si disponible ;
                    - Compte rendu médical si disponible ;
                    - Reçus de paiement.
                    """;
        }

        if (type.contains("VOYAGE")) {
            return """
                    - Billets ou justificatifs de voyage ;
                    - Factures ;
                    - Attestation de retard, annulation ou perte si disponible ;
                    - Photos ou preuves complémentaires.
                    """;
        }

        if (type.contains("VIE")) {
            return """
                    - Certificat ou document officiel lié à l’événement ;
                    - Pièce d’identité du bénéficiaire si nécessaire ;
                    - Contrat ou police concernée ;
                    - Tout justificatif demandé par l’assureur.
                    """;
        }

        return """
                - Photos ou preuves du sinistre ;
                - Factures ou devis ;
                - Tout document justificatif disponible ;
                - Rapport ou attestation si disponible.
                """;
    }

    private String normalizeClaimType(String message) {
        if (message == null) {
            return null;
        }

        String value = message.trim().toUpperCase(Locale.ROOT);

        if (value.contains("AUTO")
                || value.contains("VOITURE")
                || value.contains("VEHICULE")
                || value.contains("VÉHICULE")
                || value.contains("ACCIDENT")) {
            return "AUTO";
        }

        if (value.contains("SANTE")
                || value.contains("SANTÉ")
                || value.contains("HEALTH")
                || value.contains("MEDICAL")
                || value.contains("MÉDICAL")) {
            return "SANTE";
        }

        if (value.contains("HABITATION")
                || value.contains("MAISON")
                || value.contains("HOME")
                || value.contains("LOGEMENT")) {
            return "HABITATION";
        }

        if (value.contains("VOYAGE")
                || value.contains("TRAVEL")) {
            return "VOYAGE";
        }

        if (value.contains("VIE")
                || value.contains("LIFE")) {
            return "VIE";
        }

        return null;
    }

    private boolean isSamePolicyType(String policyType, String claimType) {
        if (policyType == null || claimType == null) {
            return false;
        }

        String normalizedPolicyType = normalizeClaimType(policyType);

        return claimType.equals(normalizedPolicyType);
    }

    private boolean isPolicyActive(Policy policy) {
        if (policy == null) {
            return false;
        }

        if (policy.getEndDate() == null) {
            return true;
        }

        return !policy.getEndDate().isBefore(LocalDate.now());
    }

    private boolean isPolicyRequest(String message) {
        return message.contains("police")
                || message.contains("polices")
                || message.contains("contrat")
                || message.contains("contrats")
                || message.contains("garantie")
                || message.contains("garanties");
    }

    private boolean isClaimRequest(String message) {
        return message.contains("mes sinistres")
                || message.contains("mes dossiers")
                || message.contains("dossiers de sinistre")
                || message.contains("réclamation")
                || message.contains("reclamation");
    }

    private AssistantResponse getClientPoliciesResponse(Long clientId) {
        if (clientId == null) {
            return new AssistantResponse("Veuillez vous connecter pour consulter vos polices d’assurance.");
        }

        List<Policy> policies = policyService.getPoliciesByClientId(clientId)
                .stream()
                .filter(this::isPolicyActive)
                .toList();

        if (policies.isEmpty()) {
            return new AssistantResponse("Vous n’avez aucune police d’assurance active actuellement.");
        }

        StringBuilder response = new StringBuilder();
        response.append("Voici vos polices d’assurance actives :\n\n");

        for (Policy policy : policies) {
            response.append("- ID ")
                    .append(policy.getId())
                    .append(" : ")
                    .append(policy.getPolicyNumber())
                    .append(" — Type : ")
                    .append(policy.getType());

            if (policy.getFormule() != null) {
                response.append(" — Formule : ").append(policy.getFormule());
            }

            if (policy.getEndDate() != null) {
                response.append(" — Fin : ").append(policy.getEndDate());
            }

            response.append("\n");
        }

        return new AssistantResponse(response.toString());
    }

    private AssistantResponse getClientClaimsResponse(Long clientId) {
        if (clientId == null) {
            return new AssistantResponse("Veuillez vous connecter pour consulter vos dossiers de sinistre.");
        }

        List<Claim> claims = claimService.getClaimsByClientId(clientId);

        if (claims.isEmpty()) {
            return new AssistantResponse("Vous n’avez aucun dossier de sinistre enregistré actuellement.");
        }

        StringBuilder response = new StringBuilder();
        response.append("Voici vos dossiers de sinistre :\n\n");

        for (Claim claim : claims) {
            response.append("- Dossier #")
                    .append(claim.getId())
                    .append(" — Statut : ")
                    .append(claim.getStatus());

            if (claim.getIncidentDate() != null) {
                response.append(" — Date incident : ").append(claim.getIncidentDate());
            }

            if (claim.getPolicy() != null && claim.getPolicy().getPolicyNumber() != null) {
                response.append(" — Police : ").append(claim.getPolicy().getPolicyNumber());
            }

            response.append("\n");
        }

        return new AssistantResponse(response.toString());
    }

    private AssistantResponse generalInsuranceAnswer(String userMessage) {
        String systemPrompt = """
                Tu es l'assistant virtuel InsurFlow spécialisé dans les sinistres d'assurance.

                Ta mission :
                - répondre uniquement aux questions générales liées aux sinistres d'assurance
                - expliquer les étapes de déclaration
                - expliquer les documents généralement demandés
                - expliquer les statuts d'un dossier
                - expliquer les notions comme franchise, garantie, exclusion, expertise, indemnisation
                - donner des conseils simples au client pour bien préparer son dossier

                Règles strictes :
                - réponds uniquement en français
                - reste clair, pédagogique et rassurant
                - limite la réponse à 6 lignes maximum
                - si la question sort du domaine sinistre assurance, réponds :
                  "Je peux vous aider uniquement sur les questions générales liées aux sinistres d’assurance."
                - ne donne jamais de conseil médical, juridique ou financier spécialisé
                - ne promets jamais qu’un dossier sera accepté ou remboursé
                - ne prétends jamais qu’un dossier sera accepté automatiquement
                """;

        String answer = assistantChatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        return new AssistantResponse(answer);
    }
}