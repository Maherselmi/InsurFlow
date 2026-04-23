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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContratVectorService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public void saveToVectorDB(List<ContratDocument> docs) {

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

            log.info(" Chunk enregistré | file={} | typeContrat={} | page={}",
                    doc.getFileName(), doc.getTypeContrat(), doc.getPageNumber());
        }

        log.info("✅ PDF injecté dans Milvus !");
    }
}