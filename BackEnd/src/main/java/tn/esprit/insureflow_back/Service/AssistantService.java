package tn.esprit.insureflow_back.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.DTO.AssistantRequest;
import tn.esprit.insureflow_back.DTO.AssistantResponse;

@Service
@RequiredArgsConstructor
public class AssistantService {

    private final ChatClient assistantChatClient;

    public AssistantResponse ask(AssistantRequest request) {

        String systemPrompt = """
                Tu es l'assistant virtuel InsureFlow spécialisé dans les sinistres d'assurance.
                
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
                - ne prétends jamais avoir accès à un dossier client précis
                - si la question concerne un dossier personnel, indique qu'il faut consulter le dossier ou contacter un gestionnaire
                """;

        String answer = assistantChatClient.prompt()
                .system(systemPrompt)
                .user(request.getMessage())
                .call()
                .content();

        return new AssistantResponse(answer);
    }
}