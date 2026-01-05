//package com.nevis.search.service;
//
//import com.nevis.search.TestDataUtils;
//import com.nevis.search.config.LangChainConfig;
//import com.nevis.search.model.DocumentChunk;
//import com.nevis.search.model.DocumentTaskStatus;
//import com.nevis.search.repository.DocumentChunkRepository;
//import com.nevis.search.repository.DocumentRepository;
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
//import org.mockito.ArgumentCaptor;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.*;
//
//@SpringBootTest(
//    classes = {EmbeddingServiceImpl.class, LangChainConfig.class},
//    properties = "app.gemini.api-key=${GEMINI_API_KEY}")
//@Tag("live-integration")
//@EnabledIfEnvironmentVariable(named = "APP_GEMINI_API_KEY", matches = ".+")
//class EmbeddingServiceLiveTest {
//
//    @Autowired
//    private EmbeddingModel embeddingModel;
//
//    @Autowired
//    private ChatModel chatModel;
//
//    @MockitoBean
//    private DocumentRepository repository;
//
//    @MockitoBean
//    private DocumentChunkRepository chunkRepository;
//
//    @MockitoBean
//    private DocumentService documentService;
//
//    @Autowired
//    private EmbeddingService embeddingService;
//
//    @BeforeEach
//    void setUp() {
//        embeddingService = new EmbeddingServiceImpl(documentService, chunkRepository, embeddingModel, chatModel);
//    }
//
//
////    @Test
////    void test() {
////        embeddingService.embedQuery("");
////    }
//
//    @Test
//    @DisplayName("Real API Call: Should receive vectors from Gemini")
//    void shouldGetRealVectorsFromGemini() {
//        UUID docId = UUID.randomUUID();
//        UUID chunkId = UUID.randomUUID();
//        String content = """
//METRO UTILITIES GROUP - ANNUAL SERVICE STATEMENT 2026
//
//UTILITY BILL
//
//Customer ID: 99887766 | Account: 123-456-789 | Date: Jan 3, 2026
//--- SERVICE ADDRESS DETAILS ---
//Primary Residence: 742 Evergreen Terrace, Springfield, IL 62704, United States
//Mailing Address: P.O. Box 555, Springfield North Postal Hub, IL 62701
//Meter Location: External North Wall, Unit A-12
//
//Billing Period Phase 1 Details for 742 Evergreen Terrace:
//- Electricity Usage: 450 kWh at $0.15/kWh. Total: $67.50
//- Water Usage: 15 m3 at $2.10/m3. Total: $31.50
//- Sewage Maintenance Fee: Fixed rate. Total: $12.00
//- Greenhouse Gas Offset Tax: Applied per local regulation 404-B. Total: $4.20
//
//Billing Period Phase 2 Details for 742 Evergreen Terrace:
//- Electricity Usage: 450 kWh at $0.15/kWh. Total: $67.50
//- Water Usage: 15 m3 at $2.10/m3. Total: $31.50
//- Sewage Maintenance Fee: Fixed rate. Total: $12.00
//- Greenhouse Gas Offset Tax: Applied per local regulation 404-B. Total: $4.20
//
//Billing Period Phase 3 Details for 742 Evergreen Terrace:
//- Electricity Usage: 450 kWh at $0.15/kWh. Total: $67.50
//- Water Usage: 15 m3 at $2.10/m3. Total: $31.50
//- Sewage Maintenance Fee: Fixed rate. Total: $12.00
//- Greenhouse Gas Offset Tax: Applied per local regulation 404-B. Total: $4.20
//
//--- CONSUMPTION HISTORY (2024-2025) ---
//Data Log Month 1: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 2: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 3: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 4: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 5: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 6: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 7: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 8: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 9: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 10: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 11: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 12: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 13: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 14: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 15: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 16: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 17: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 18: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 19: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 20: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 21: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 22: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 23: Avg Temp 22C, Usage 400kWh, Status: PAID.
//Data Log Month 24: Avg Temp 22C, Usage 400kWh, Status: PAID.
//
//            """;
//
//        DocumentChunk mockChunk = new DocumentChunk(chunkId, docId, content, null,
//            DocumentTaskStatus.PENDING, null, 0, null, null);
//
//        when(chunkRepository.findByStatus(docId))
//            .thenReturn(List.of(mockChunk));
//
//        embeddingService.generateForDocument(docId);
//
//        ArgumentCaptor<Map<UUID, float[]>> captor = ArgumentCaptor.forClass(Map.class);
//        verify(documentService).saveEmbeddings(eq(docId), captor.capture());
//
//        Map<UUID, float[]> result = captor.getValue();
//
//        assertThat(result).containsKey(chunkId);
//        float[] vector = result.get(chunkId);
//
//        assertThat(vector).isNotEmpty();
//        System.out.println("Dimension of Gemini vector: " + Arrays.toString(vector));
//        assertThat(vector.length).isGreaterThan(0);
//    }
//}