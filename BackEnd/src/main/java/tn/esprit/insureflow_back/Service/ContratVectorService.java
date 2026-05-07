package tn.esprit.insureflow_back.Service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprit.insureflow_back.Domain.Entities.ContratDocument;
import tn.esprit.insureflow_back.Domain.Entities.ContratVectorFile;
import tn.esprit.insureflow_back.Repository.ContratVectorFileRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContratVectorService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ContratVectorFileRepository contratVectorFileRepository;

    public void saveToVectorDB(List<ContratDocument> docs) {

        if (docs == null || docs.isEmpty()) {
            throw new IllegalArgumentException("Aucun document à injecter.");
        }

        for (ContratDocument doc : docs) {
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("typeContrat", doc.getTypeContrat());
            metadataMap.put("file", doc.getFileName());
            metadataMap.put("pageNumber", doc.getPageNumber());
            metadataMap.put("source", doc.getSource());

            TextSegment segment = TextSegment.from(
                    doc.getContent(),
                    Metadata.from(metadataMap)
            );

            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        }

        ContratDocument first = docs.get(0);

        int pagesCount = docs.stream()
                .map(ContratDocument::getPageNumber)
                .filter(p -> p != null)
                .collect(java.util.stream.Collectors.toSet())
                .size();

        ContratVectorFile fileRecord = ContratVectorFile.builder()
                .fileName(first.getFileName())
                .typeContrat(first.getTypeContrat())
                .source(first.getSource())
                .pagesCount(pagesCount)
                .chunksCount(docs.size())
                .uploadedAt(java.time.LocalDateTime.now())
                .build();

        contratVectorFileRepository.save(fileRecord);

        log.info("PDF injecté dans Milvus et enregistré en SQL: {}", first.getFileName());
    }
}