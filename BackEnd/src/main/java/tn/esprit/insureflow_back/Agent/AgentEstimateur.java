package tn.esprit.insureflow_back.Agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tn.esprit.insureflow_back.Domain.ENUMS.AgentName;
import tn.esprit.insureflow_back.Domain.Entities.AgentResult;
import tn.esprit.insureflow_back.Domain.Entities.Claim;
import tn.esprit.insureflow_back.Domain.Entities.ClaimDocument;
import tn.esprit.insureflow_back.Service.AgentLearningMemoryService;
import tn.esprit.insureflow_back.Service.AiAgentConfigService;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AgentEstimateur — version v2 (correctifs logs 26/04/2026)
 *
 * Problèmes identifiés dans les logs :
 *  A) Modèle vision répond en texte libre → "Aucun JSON valide trouvé"
 *     Fix : prompt ultra-strict JSON-only (instruction EN TÊTE) + fallback regex
 *  B) Estimateur prend ~8 min (485 300 ms)
 *     Fix : image réduite à 512px, MAX_IMAGES=1, prompt compact
 *
 * Optimisations maintenues :
 *  - Cache base64 par chemin fichier (ConcurrentHashMap)
 *  - LearningMemory + chargement images en parallèle (CompletableFuture)
 *  - Redimensionnement + compression JPEG avant envoi
 */
@Slf4j
@Component
public class AgentEstimateur {

    private static final String AGENT_NAME = "AgentEstimateur";
    private static final String CONFIG_KEY = "AGENT_ESTIMATEUR";

    private static final double DEFAULT_CONFIDENCE = 0.50;

    // CORRECTIF B : 1 seule image, résolution 512px
    // qwen2.5vl:7b local traite ~4x plus vite une image 512px qu'une 800px
    private static final int   MAX_IMAGES          = 1;
    private static final int   IMAGE_MAX_DIMENSION = 512;  // était 800
    private static final float IMAGE_JPEG_QUALITY  = 0.70f;

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    // Cache base64 par chemin fichier — évite relecture + ré-encodage
    private final Map<String, String> imageBase64Cache = new ConcurrentHashMap<>();

    // CORRECTIF A — patterns pour extraire les montants depuis texte libre
    // Cas "2500.0 à 3000.0 DT" ou "2500 - 3000 DT"
    private static final Pattern RANGE_AMOUNT_PATTERN =
            Pattern.compile("(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*(?:à|-)\\s*(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*DT",
                    Pattern.CASE_INSENSITIVE);
    // Cas "2500 DT" seul
    private static final Pattern SINGLE_AMOUNT_PATTERN =
            Pattern.compile("(\\d{3,6}(?:[.,]\\d{1,2})?)\\s*DT",
                    Pattern.CASE_INSENSITIVE);

    private final ChatLanguageModel          visionModel;
    private final ObjectMapper               objectMapper;
    private final AiAgentConfigService       aiAgentConfigService;
    private final AgentLearningMemoryService learningMemoryService;

    public AgentEstimateur(
            @Qualifier("visionLanguageModel") ChatLanguageModel visionModel,
            ObjectMapper objectMapper,
            AiAgentConfigService aiAgentConfigService,
            AgentLearningMemoryService learningMemoryService
    ) {
        this.visionModel           = visionModel;
        this.objectMapper          = objectMapper;
        this.aiAgentConfigService  = aiAgentConfigService;
        this.learningMemoryService = learningMemoryService;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Point d'entrée principal
    // ══════════════════════════════════════════════════════════════════════════

    public AgentResult estimate(Claim claim,
                                AgentResult routeResult,
                                AgentResult validationResult) {

        if (claim == null) {
            throw new IllegalArgumentException("Le claim ne doit pas être null");
        }

        log.info("{} - analyse des images du sinistre #{}", AGENT_NAME, claim.getId());

        List<ClaimDocument> images = extractValidImages(claim);

        if (images.isEmpty()) {
            log.warn("Aucune image exploitable trouvée pour le dossier #{}", claim.getId());
            return buildResult(claim, 0.0, 0.0, 0.0, DEFAULT_CONFIDENCE,
                    "Aucune image exploitable fournie", true, null);
        }

        log.info("{} image(s) retenue(s) (max {})", images.size(), MAX_IMAGES);

        String typeDetecte          = extractClaimType(routeResult);
        double humanReviewThreshold = aiAgentConfigService.getThreshold(CONFIG_KEY);
        log.info("Seuil confiance {} = {}", CONFIG_KEY, humanReviewThreshold);

        // Parallélisation : LearningMemory + chargement/encodage images
        CompletableFuture<String> learningFuture = CompletableFuture.supplyAsync(
                () -> learningMemoryService.buildMemoryBlock(AgentName.AGENT_ESTIMATEUR, claim.getId())
        );
        CompletableFuture<List<EncodedImage>> imagesFuture = CompletableFuture.supplyAsync(
                () -> loadAndEncodeImages(images)
        );

        String             learningExamples = learningFuture.join();
        List<EncodedImage> encodedImages    = imagesFuture.join();

        if (learningExamples == null || learningExamples.isBlank()) {
            learningExamples = "";
        }

        if (encodedImages.isEmpty()) {
            log.warn("Aucune image lisible pour le dossier #{}", claim.getId());
            return buildResult(claim, 0.0, 0.0, 0.0, DEFAULT_CONFIDENCE,
                    "Images illisibles ou introuvables sur disque", true, null);
        }

        try {
            String prompt = buildPrompt(claim, routeResult, validationResult,
                    humanReviewThreshold, learningExamples);

            List<dev.langchain4j.data.message.Content> contents =
                    buildVisionContents(prompt, encodedImages);

            UserMessage userMessage = UserMessage.from(contents);

            log.info("Envoi au modèle vision ({} image(s), prompt {} chars)",
                    encodedImages.size(), prompt.length());

            Response<AiMessage> response = visionModel.generate(userMessage);

            String rawResponse = response != null && response.content() != null
                    ? response.content().text() : "";

            log.info("Réponse brute vision LLM : {}", rawResponse);

            return parseResponse(rawResponse, claim, typeDetecte, humanReviewThreshold);

        } catch (Exception e) {
            log.error("Erreur AgentEstimateur dossier #{}: {}", claim.getId(), e.getMessage(), e);
            return buildResult(claim, 0.0, 0.0, 0.0, 0.0,
                    "Erreur technique : " + e.getMessage(), true, null);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Sélection images valides
    // ══════════════════════════════════════════════════════════════════════════

    private List<ClaimDocument> extractValidImages(Claim claim) {
        if (claim.getDocuments() == null || claim.getDocuments().isEmpty()) return List.of();
        return claim.getDocuments().stream()
                .filter(doc -> doc.getFileType() != null)
                .filter(doc -> SUPPORTED_IMAGE_TYPES.contains(
                        doc.getFileType().toLowerCase(Locale.ROOT)))
                .filter(doc -> doc.getFilePath() != null && !doc.getFilePath().isBlank())
                .limit(MAX_IMAGES)
                .toList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Chargement + redimensionnement + cache base64
    // ══════════════════════════════════════════════════════════════════════════

    private List<EncodedImage> loadAndEncodeImages(List<ClaimDocument> docs) {
        List<EncodedImage> result = new ArrayList<>();
        for (ClaimDocument doc : docs) {
            try {
                String cached = imageBase64Cache.get(doc.getFilePath());
                if (cached != null) {
                    log.info("Image {} servie depuis cache", doc.getFileName());
                    result.add(new EncodedImage(cached, "image/jpeg"));
                    continue;
                }

                Path path = Path.of(doc.getFilePath());
                if (!Files.exists(path)) {
                    log.warn("Fichier image introuvable : {}", doc.getFilePath());
                    continue;
                }

                byte[] rawBytes = Files.readAllBytes(path);
                String base64   = resizeAndEncodeJpeg(rawBytes);

                imageBase64Cache.put(doc.getFilePath(), base64);
                result.add(new EncodedImage(base64, "image/jpeg"));
                log.info("Image {} chargée ({}px max, q={})",
                        doc.getFileName(), IMAGE_MAX_DIMENSION, IMAGE_JPEG_QUALITY);

            } catch (Exception e) {
                log.warn("Impossible de charger {} : {}", doc.getFilePath(), e.getMessage());
            }
        }
        return result;
    }

    private String resizeAndEncodeJpeg(byte[] rawBytes) throws IOException {
        BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(rawBytes));
        if (original == null) {
            return Base64.getEncoder().encodeToString(rawBytes);
        }

        int w = original.getWidth();
        int h = original.getHeight();

        if (w > IMAGE_MAX_DIMENSION || h > IMAGE_MAX_DIMENSION) {
            double ratio = Math.min(
                    (double) IMAGE_MAX_DIMENSION / w,
                    (double) IMAGE_MAX_DIMENSION / h
            );
            int newW = (int) (w * ratio);
            int newH = (int) (h * ratio);

            BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g2d.drawImage(original, 0, 0, newW, newH, null);
            g2d.dispose();
            original = resized;

            log.info("Image redimensionnée {}x{} → {}x{}", w, h, newW, newH);
        }

        ByteArrayOutputStream baos   = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(original, "png", baos);
        } else {
            ImageWriter     writer = writers.next();
            ImageWriteParam param  = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(IMAGE_JPEG_QUALITY);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(original, null, null), param);
            } finally {
                writer.dispose();
            }
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Construction du message vision
    // ══════════════════════════════════════════════════════════════════════════

    private List<dev.langchain4j.data.message.Content> buildVisionContents(
            String prompt, List<EncodedImage> encodedImages) {

        List<dev.langchain4j.data.message.Content> contents = new ArrayList<>();
        contents.add(TextContent.from(prompt));
        for (EncodedImage img : encodedImages) {
            contents.add(ImageContent.from(
                    Image.builder()
                            .base64Data(img.base64())
                            .mimeType(img.mimeType())
                            .build()
            ));
        }
        return contents;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CORRECTIF A : Prompt JSON-only ultra-strict
    //
    //  Problème dans les logs : le modèle répondait en texte libre car
    //  l'instruction JSON était noyée au milieu du prompt.
    //  Fix : instruction JSON EN TÊTE + exemple concret + rappel EN FIN.
    // ══════════════════════════════════════════════════════════════════════════

    private String buildPrompt(Claim claim,
                               AgentResult routeResult,
                               AgentResult validationResult,
                               double humanReviewThreshold,
                               String learningExamples) {

        String typeDetecte        = extractClaimType(routeResult);
        String decisionValidateur = validationResult != null
                ? safe(validationResult.getConclusion()) : "INCONNU";
        String plafonds           = getPlafondsByType(typeDetecte);
        String reglesMetier       = getReglesMetierByType(typeDetecte);
        String memorySection      = (learningExamples == null || learningExamples.isBlank())
                ? "Aucun exemple." : truncate(learningExamples, 250);
        String description        = truncate(safe(claim.getDescription()), 150);
        String incidentDate       = claim.getIncidentDate() != null
                ? claim.getIncidentDate().toString() : "Inconnue";

        return """
                INSTRUCTION CRITIQUE : Réponds UNIQUEMENT avec du JSON valide.
                Aucun texte avant ou après le JSON. Aucune explication. Aucun markdown.

                FORMAT OBLIGATOIRE — recopie exactement cette structure avec tes valeurs :
                {"estimationMin":500.0,"estimationMax":3000.0,"estimationMoyenne":1750.0,"confidence":0.80,"analyse":"Description courte des dommages visibles","needsHumanReview":false}

                ---
                CONTEXTE
                Type sinistre : %s
                Validation : %s
                Description : %s
                Date incident : %s

                PLAFONDS (en DT)
                %s

                REGLES METIER
                %s

                EXEMPLES PASSES VALIDES
                %s

                TACHE
                Analyse l'image jointe. Estime le coût de réparation/indemnisation en dinars tunisiens (DT).
                - estimationMin < estimationMoyenne < estimationMax
                - Ne jamais dépasser le plafond max indiqué
                - Si confidence < %.2f → mettre needsHumanReview=true
                - Si image floue, insuffisante ou non exploitable → confidence basse, needsHumanReview=true

                RAPPEL FINAL : ta réponse doit commencer par { et se terminer par }
                Aucun autre texte. Uniquement le JSON.
                """.formatted(
                typeDetecte,
                decisionValidateur,
                description,
                incidentDate,
                plafonds,
                reglesMetier,
                memorySection,
                humanReviewThreshold
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Parsing — CORRECTIF A : fallback regex si réponse texte libre
    // ══════════════════════════════════════════════════════════════════════════

    private AgentResult parseResponse(String raw, Claim claim,
                                      String typeDetecte, double humanReviewThreshold) {
        if (raw == null || raw.isBlank()) {
            return buildResult(claim, 0.0, 0.0, 0.0, 0.0,
                    "Réponse vide du modèle de vision", true, raw);
        }

        // Tentative 1 : extraction JSON normale
        try {
            String jsonOnly = extractJson(raw);
            log.info("JSON extrait : {}", jsonOnly);

            JsonNode node            = objectMapper.readTree(jsonOnly);
            double estimationMin     = normalizeMoney(node.path("estimationMin").asDouble(0.0));
            double estimationMax     = normalizeMoney(node.path("estimationMax").asDouble(0.0));
            double estimationMoyenne = normalizeMoney(node.path("estimationMoyenne").asDouble(0.0));
            double confidence        = normalizeConfidence(
                    node.path("confidence").asDouble(DEFAULT_CONFIDENCE));
            String  analyse    = safe(node.path("analyse").asText("Analyse non fournie"));
            boolean needsHuman = node.path("needsHumanReview").asBoolean(false);

            return applyBusinessRules(claim, typeDetecte, humanReviewThreshold,
                    estimationMin, estimationMax, estimationMoyenne,
                    confidence, analyse, needsHuman, raw);

        } catch (Exception e) {
            log.warn("JSON absent dans réponse vision claim #{} — activation fallback regex : {}",
                    claim.getId(), e.getMessage());
        }

        // Tentative 2 : fallback regex sur réponse texte libre
        // Couvre le cas des logs : "2500.0 à 3000.0 DT, ce qui est bien en dessous..."
        return fallbackFromText(raw, claim, typeDetecte, humanReviewThreshold);
    }

    /**
     * Le modèle a répondu en texte libre (comme dans les logs).
     * On extrait les montants par regex et on construit un résultat partiel
     * avec needsHumanReview=true pour qu'un expert valide.
     */
    private AgentResult fallbackFromText(String raw, Claim claim,
                                         String typeDetecte, double humanReviewThreshold) {
        log.info("Fallback texte activé pour claim #{}", claim.getId());

        double  min     = 0.0;
        double  max     = 0.0;
        double  moyenne = 0.0;
        boolean found   = false;

        // Cherche "2500.0 à 3000.0 DT" ou "2500 - 3000 DT"
        Matcher mRange = RANGE_AMOUNT_PATTERN.matcher(raw);
        if (mRange.find()) {
            min     = parseAmount(mRange.group(1));
            max     = parseAmount(mRange.group(2));
            moyenne = (min + max) / 2.0;
            found   = true;
            log.info("Fallback range trouvé : min={} max={}", min, max);
        }

        // Cherche plusieurs montants isolés "2500 DT"
        if (!found) {
            Matcher mSingle = SINGLE_AMOUNT_PATTERN.matcher(raw);
            List<Double> amounts = new ArrayList<>();
            while (mSingle.find()) {
                double val = parseAmount(mSingle.group(1));
                if (val >= 100) amounts.add(val); // ignore les petits nombres parasites
            }
            if (!amounts.isEmpty()) {
                min     = amounts.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                max     = amounts.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                moyenne = amounts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                found   = true;
                log.info("Fallback single amounts trouvés : {} montants", amounts.size());
            }
        }

        String  analyse    = extractAnalyseFromText(raw);
        double  confidence = found ? 0.45 : 0.0;
        boolean needsHuman = !found || confidence < humanReviewThreshold;

        log.info("Fallback résultat claim #{} : min={} moy={} max={} conf={} review={}",
                claim.getId(), min, moyenne, max, confidence, needsHuman);

        return applyBusinessRules(claim, typeDetecte, humanReviewThreshold,
                min, max, moyenne, confidence, analyse, needsHuman, raw);
    }

    private AgentResult applyBusinessRules(Claim claim, String typeDetecte,
                                           double humanReviewThreshold,
                                           double estimationMin, double estimationMax,
                                           double estimationMoyenne, double confidence,
                                           String analyse, boolean needsHuman, String raw) {
        // Correction min/max inversés
        if (estimationMin > estimationMax) {
            double tmp    = estimationMin;
            estimationMin = estimationMax;
            estimationMax = tmp;
        }

        // Correction moyenne hors plage
        if (estimationMoyenne < estimationMin || estimationMoyenne > estimationMax) {
            estimationMoyenne = (estimationMin + estimationMax) / 2.0;
        }

        // Plafonnement contractuel
        double maxAllowed = getMaxAllowedByType(typeDetecte);
        if (estimationMax > maxAllowed) {
            log.warn("Estimation {} DT > plafond {} DT pour type={}", estimationMax, maxAllowed, typeDetecte);
            estimationMax     = maxAllowed;
            estimationMoyenne = Math.min(estimationMoyenne, maxAllowed);
            estimationMin     = Math.min(estimationMin, maxAllowed);
            needsHuman        = true;
            analyse          += " [Plafond appliqué: " + maxAllowed + " DT]";
        }

        if (confidence < humanReviewThreshold) needsHuman = true;
        if (analyse == null || analyse.isBlank()) {
            analyse    = "Analyse non fournie";
            needsHuman = true;
        }

        log.info("Estimation finale claim #{} | min={} moy={} max={} conf={} seuil={} review={}",
                claim.getId(), estimationMin, estimationMoyenne, estimationMax,
                confidence, humanReviewThreshold, needsHuman);

        return buildResult(claim, estimationMin, estimationMax, estimationMoyenne,
                confidence, analyse, needsHuman, raw);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Extraction JSON
    // ══════════════════════════════════════════════════════════════════════════

    private String extractJson(String raw) {
        String clean = raw
                .replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        int jsonStart = clean.indexOf("{");
        int jsonEnd   = clean.lastIndexOf("}");

        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw new IllegalArgumentException("Aucun JSON valide trouvé");
        }
        return clean.substring(jsonStart, jsonEnd + 1).trim();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Plafonds et règles métier (compactés)
    // ══════════════════════════════════════════════════════════════════════════

    private String getPlafondsByType(String type) {
        if (type == null) return "Plafond général: 50000 DT";
        String t = type.toUpperCase(Locale.ROOT);
        if (t.contains("AUTO")) return
                "Léger:300-2000 DT | Modéré:2000-8000 DT | Grave:8000-18000 DT | Perte totale:10000-25000 DT | Max:25000 DT";
        if (t.contains("SANTE")) return
                "Consultation:50-300 DT | Hospit.:500-15000 DT | Chirurgie:1500-20000 DT | Max:50000 DT";
        if (t.contains("HABITATION")) return
                "Léger:500-5000 DT | Modéré:5000-20000 DT | Grave:20000-80000 DT | Franchise dégâts eaux:200 DT | Max:100000 DT";
        if (t.contains("VOYAGE")) return
                "Retard:100-500 DT | Bagages:200-2000 DT | Annulation:500-5000 DT | Médical:1000-50000 DT";
        return "Plafond général: 50000 DT";
    }

    private String getReglesMetierByType(String type) {
        if (type == null) return "Estimation prudente, révision humaine.";
        String t = type.toUpperCase(Locale.ROOT);
        if (t.contains("AUTO")) return
                "Dommages>60% structure=perte totale(10000-25000 DT). Sinon réparation+franchise 300 DT. Max 25000 DT.";
        if (t.contains("SANTE")) return
                "Hospit. remboursée à 90%. Médicaments max 500 DT/mois. Déduire franchise.";
        if (t.contains("HABITATION")) return
                "Valeur remplacement à neuf. Franchise 200 DT dégâts eaux. Max valeur déclarée.";
        if (t.contains("VOYAGE")) return
                "Plafonds par catégorie. Soins étranger: factures originales.";
        return "Estimation prudente basée sur dommages visibles.";
    }

    private double getMaxAllowedByType(String type) {
        if (type == null) return 50000.0;
        String t = type.toUpperCase(Locale.ROOT);
        if (t.contains("AUTO"))       return 25000.0;
        if (t.contains("SANTE"))      return 50000.0;
        if (t.contains("HABITATION")) return 100000.0;
        if (t.contains("VOYAGE"))     return 50000.0;
        return 50000.0;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Extraction type sinistre
    // ══════════════════════════════════════════════════════════════════════════

    private String extractClaimType(AgentResult routeResult) {
        if (routeResult == null || routeResult.getConclusion() == null) return "INCONNU";
        String conclusion = routeResult.getConclusion().trim();
        if (conclusion.startsWith("Type de sinistre classifié :")) {
            return conclusion.replace("Type de sinistre classifié :", "").trim();
        }
        return conclusion;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private AgentResult buildResult(Claim claim,
                                    double estimationMin, double estimationMax,
                                    double estimationMoyenne, double confidence,
                                    String analyse, boolean needsHuman, String rawResponse) {
        AgentResult result = new AgentResult();
        result.setAgentName(AGENT_NAME);
        result.setClaim(claim);
        result.setConclusion(buildConclusion(estimationMin, estimationMax, estimationMoyenne));
        result.setConfidenceScore(confidence);
        result.setRawLlmResponse(rawResponse != null ? rawResponse : analyse);
        result.setNeedsHumanReview(needsHuman);
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private String buildConclusion(double min, double max, double moyenne) {
        return "Estimation min: %.2f DT | moyenne: %.2f DT | max: %.2f DT"
                .formatted(min, moyenne, max);
    }

    private double normalizeMoney(double value) { return Math.max(0.0, value); }

    private double normalizeConfidence(double confidence) {
        if (confidence > 1.0 && confidence <= 100.0) confidence /= 100.0;
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Extrait les 2 premières phrases du texte brut pour une analyse lisible. */
    private String extractAnalyseFromText(String raw) {
        if (raw == null || raw.isBlank()) return "Analyse extraite depuis réponse texte.";
        String clean     = raw.replaceAll("\\s+", " ").trim();
        String[] sentences = clean.split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, sentences.length); i++) {
            sb.append(sentences[i]).append(" ");
        }
        String result = sb.toString().trim();
        return result.length() > 300 ? result.substring(0, 300) : result;
    }

    private String truncate(String text, int max) {
        String s = safe(text);
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }

    // ── Record interne ────────────────────────────────────────────────────────
    private record EncodedImage(String base64, String mimeType) {}
}