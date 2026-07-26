package com.familier.ai.service;

import com.familier.ai.dto.FamilyMembersDto;
import com.familier.ai.dto.TargetProfileWithRelation;
import com.familier.ai.entity.UserContext;
import com.familier.ai.repository.UserContextRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.ai.chat.model.ToolContext;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FamilyAiTools — all 4 Spring AI tool methods.
 *
 * All external dependencies are mocked. @Value fields (internalSecret, coreServiceUrl)
 * are injected via ReflectionTestUtils since Spring context is not loaded.
 *
 * Strictness.LENIENT is used because the WebClient chain stubs must be set up
 * broadly and some stubs are intentionally reused across test methods.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FamilyAiToolsTest {

    @Mock private RelationMappingService  relationMappingService;
    @Mock private TargetProfileService    targetProfileService;
    @Mock private UserProfileService      userProfileService;
    @Mock private UserContextRepository   userContextRepository;
    @Mock private SuggestionService       suggestionService;
    @Mock private WebClient.Builder       webClientBuilder;

    // WebClient chain mocks — shared across GET and POST stubs
    @Mock private WebClient                        webClient;
    @Mock private WebClient.RequestBodyUriSpec     requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec        requestBodySpec;
    @Mock private WebClient.RequestHeadersSpec     requestHeadersSpec;
    @Mock private WebClient.RequestHeadersUriSpec  requestHeadersUriSpec;
    @Mock private WebClient.ResponseSpec           responseSpec;

    private FamilyAiTools tools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String USER_EMAIL   = "john.doe@example.com";
    private static final String SPOUSE_EMAIL = "jane.doe@example.com";
    private static final String CORE_URL     = "http://localhost:8081";
    private static final String SECRET       = "test-secret";

    @BeforeEach
    void setUp() {
        when(webClientBuilder.build()).thenReturn(webClient);

        tools = new FamilyAiTools(
                relationMappingService,
                targetProfileService,
                userProfileService,
                userContextRepository,
                suggestionService,
                webClientBuilder,
                objectMapper
        );

        // Inject @Value fields — not available without Spring context
        ReflectionTestUtils.setField(tools, "coreServiceUrl", CORE_URL);
        ReflectionTestUtils.setField(tools, "internalSecret",  SECRET);

        tools.setCurrentUserEmail(USER_EMAIL);

        // Stub WebClient POST chain (used by broadcastFamilySuggestion)
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        // Stub WebClient GET chain (used by createTaskForFamily)
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        // header() returns itself — handles multiple .header() calls in chain
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool 1: getUserProfile
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tool 1 — getUserProfile")
    class GetUserProfileTests {

        @Test
        @DisplayName("Returns full profile when user exists with facts")
        void returnsFullProfile() throws Exception {
            Map<String, Object> profile = Map.of(
                    "fullName", "John Doe",
                    "birthday", "1990-01-01",
                    "gender",   "MALE",
                    "hobbies",  "[\"reading\",\"hiking\"]"
            );
            UserContext ctx = UserContext.builder()
                    .email(USER_EMAIL)
                    .facts(List.of(
                            UserContext.Fact.builder().key("mood").value("happy").confidence(0.9).build(),
                            UserContext.Fact.builder().key("allergy").value("shrimp").confidence(0.8).build()
                    ))
                    .build();

            when(userProfileService.getUserProfile(USER_EMAIL)).thenReturn(Mono.just(profile));
            when(userContextRepository.findByEmail(USER_EMAIL)).thenReturn(Mono.just(ctx));

            String result = tools.getUserProfile(null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isTrue();
            assertThat(json.get("fullName").asText()).isEqualTo("John Doe");
            assertThat(json.get("gender").asText()).isEqualTo("MALE");
            assertThat(json.get("hobbies").asText()).contains("reading");
            assertThat(json.get("facts").asText()).contains("mood");
            assertThat(json.get("facts").asText()).contains("allergy");
        }

        @Test
        @DisplayName("Fields default to N/A when profile map is empty")
        void defaultsToNaWhenProfileEmpty() throws Exception {
            when(userProfileService.getUserProfile(USER_EMAIL)).thenReturn(Mono.just(Map.of()));
            when(userContextRepository.findByEmail(USER_EMAIL)).thenReturn(Mono.empty());

            String result = tools.getUserProfile(null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isTrue();
            // all fields should fall back to N/A when map is empty
            assertThat(json.get("fullName").asText()).isEqualTo("N/A");
        }

        @Test
        @DisplayName("Returns found=false when service throws exception")
        void returnsErrorJsonOnException() throws Exception {
            when(userProfileService.getUserProfile(USER_EMAIL))
                    .thenReturn(Mono.error(new RuntimeException("DB unavailable")));

            String result = tools.getUserProfile(null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("success").asBoolean()).isFalse();
            assertThat(json.get("errorCode").asText()).isEqualTo("TOOL_EXECUTION_ERROR");
        }

        @Test
        @DisplayName("Facts with confidence < 0.7 are filtered out of result")
        void filtersLowConfidenceFacts() throws Exception {
            Map<String, Object> profile = Map.of("fullName", "John Doe");
            UserContext ctx = UserContext.builder()
                    .email(USER_EMAIL)
                    .facts(List.of(
                            UserContext.Fact.builder().key("highConf").value("yes").confidence(0.9).build(),
                            UserContext.Fact.builder().key("lowConf").value("no").confidence(0.5).build()
                    ))
                    .build();

            when(userProfileService.getUserProfile(USER_EMAIL)).thenReturn(Mono.just(profile));
            when(userContextRepository.findByEmail(USER_EMAIL)).thenReturn(Mono.just(ctx));

            String result = tools.getUserProfile(null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isTrue();
            assertThat(json.get("facts").asText()).contains("highConf");
            assertThat(json.get("facts").asText()).doesNotContain("lowConf");
        }

        @Test
        @DisplayName("Empty facts list returns empty JSON array")
        void emptyFactsReturnsEmptyArray() throws Exception {
            Map<String, Object> profile = Map.of("fullName", "John Doe");
            UserContext ctx = UserContext.builder()
                    .email(USER_EMAIL)
                    .facts(List.of())
                    .build();

            when(userProfileService.getUserProfile(USER_EMAIL)).thenReturn(Mono.just(profile));
            when(userContextRepository.findByEmail(USER_EMAIL)).thenReturn(Mono.just(ctx));

            String result = tools.getUserProfile(null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isTrue();
            assertThat(json.get("facts").asText()).isEqualTo("[]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool 2: getFamilyMemberProfile
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tool 2 — getFamilyMemberProfile")
    class GetFamilyMemberProfileTests {

        @Test
        @DisplayName("Returns spouse profile when relation is SPOUSE")
        void returnsSpouseProfile() throws Exception {
            TargetProfileWithRelation profile = TargetProfileWithRelation.builder()
                    .email(SPOUSE_EMAIL)
                    .fullName("Jane Doe")
                    .birthday("1992-05-15")
                    .gender("FEMALE")
                    .hobbies("[\"cooking\",\"yoga\"]")
                    .relationType("SPOUSE")
                    .build();

            UserContext ctx = UserContext.builder()
                    .email(SPOUSE_EMAIL)
                    .facts(List.of(
                            UserContext.Fact.builder().key("pet").value("cat").confidence(0.85).build()
                    ))
                    .build();

            when(relationMappingService.mapRelationToEmail(USER_EMAIL, "SPOUSE"))
                    .thenReturn(Mono.just(SPOUSE_EMAIL));
            when(targetProfileService.getTargetProfile(USER_EMAIL, SPOUSE_EMAIL))
                    .thenReturn(Mono.just(profile));
            when(userContextRepository.findByEmail(SPOUSE_EMAIL))
                    .thenReturn(Mono.just(ctx));

            String result = tools.getFamilyMemberProfile("SPOUSE", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isTrue();
            assertThat(json.get("fullName").asText()).isEqualTo("Jane Doe");
            assertThat(json.get("gender").asText()).isEqualTo("FEMALE");
            assertThat(json.get("hobbies").asText()).contains("cooking");
            assertThat(json.get("facts").asText()).contains("pet");
        }

        @Test
        @DisplayName("Returns found=false when no relation mapping exists (Mono.empty)")
        void returnsNotFoundWhenNoRelationMapping() throws Exception {
            when(relationMappingService.mapRelationToEmail(USER_EMAIL, "BROTHER"))
                    .thenReturn(Mono.empty());

            String result = tools.getFamilyMemberProfile("BROTHER", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isFalse();
            assertThat(json.get("relation").asText()).isEqualTo("BROTHER");
        }

        @Test
        @DisplayName("Returns found=false when mapping returns empty string")
        void returnsNotFoundForEmptyEmail() throws Exception {
            when(relationMappingService.mapRelationToEmail(USER_EMAIL, "SISTER"))
                    .thenReturn(Mono.just(""));

            String result = tools.getFamilyMemberProfile("SISTER", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Returns found=false when targetProfileService returns empty Mono")
        void returnsNotFoundWhenProfileEmpty() throws Exception {
            when(relationMappingService.mapRelationToEmail(USER_EMAIL, "FATHER"))
                    .thenReturn(Mono.just("father@example.com"));
            when(targetProfileService.getTargetProfile(USER_EMAIL, "father@example.com"))
                    .thenReturn(Mono.empty());
            when(userContextRepository.findByEmail("father@example.com"))
                    .thenReturn(Mono.empty());

            String result = tools.getFamilyMemberProfile("FATHER", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Returns error JSON when relation mapping throws")
        void returnsErrorJsonOnException() throws Exception {
            when(relationMappingService.mapRelationToEmail(USER_EMAIL, "MOTHER"))
                    .thenReturn(Mono.error(new RuntimeException("Redis timeout")));

            String result = tools.getFamilyMemberProfile("MOTHER", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("success").asBoolean()).isFalse();
            assertThat(json.get("errorCode").asText()).isEqualTo("TOOL_EXECUTION_ERROR");
        }

        @Test
        @DisplayName("Profile fields default to N/A when null")
        void profileFieldsDefaultToNaWhenNull() throws Exception {
            TargetProfileWithRelation emptyProfile = TargetProfileWithRelation.builder()
                    .email(SPOUSE_EMAIL)
                    .build(); // all name/birthday/gender/hobbies null

            when(relationMappingService.mapRelationToEmail(USER_EMAIL, "SPOUSE"))
                    .thenReturn(Mono.just(SPOUSE_EMAIL));
            when(targetProfileService.getTargetProfile(USER_EMAIL, SPOUSE_EMAIL))
                    .thenReturn(Mono.just(emptyProfile));
            when(userContextRepository.findByEmail(SPOUSE_EMAIL)).thenReturn(Mono.empty());

            String result = tools.getFamilyMemberProfile("SPOUSE", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isTrue();
            // All nullable fields should fall back to N/A or []
            assertThat(result).contains("N/A");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool 3: broadcastFamilySuggestion
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tool 3 — broadcastFamilySuggestion")
    class BroadcastFamilySuggestionTests {

        @Test
        @DisplayName("Returns broadcasted=true for EMOTIONAL_SUPPORT")
        void broadcastsEmotionalSupport() {
            String result = tools.broadcastFamilySuggestion("EMOTIONAL_SUPPORT", "Mình mệt lắm", null);
            assertThat(result).isEqualTo("{\"broadcasted\": true}");
        }

        @Test
        @DisplayName("Returns broadcasted=true for SOCIAL_ISOLATION")
        void broadcastsSocialIsolation() {
            String result = tools.broadcastFamilySuggestion("SOCIAL_ISOLATION", "Cô đơn quá", null);
            assertThat(result).isEqualTo("{\"broadcasted\": true}");
        }

        @Test
        @DisplayName("Returns broadcasted=true for POSITIVE_MILESTONE")
        void broadcastsPositiveMilestone() {
            String result = tools.broadcastFamilySuggestion("POSITIVE_MILESTONE", "Mình thăng chức rồi!", null);
            assertThat(result).isEqualTo("{\"broadcasted\": true}");
        }

        @Test
        @DisplayName("Returns broadcasted=true for STRONG_NEGATIVE_EMOTION")
        void broadcastsStrongNegativeEmotion() {
            String result = tools.broadcastFamilySuggestion("STRONG_NEGATIVE_EMOTION", "Tức quá", null);
            assertThat(result).isEqualTo("{\"broadcasted\": true}");
        }

        @Test
        @DisplayName("Returns broadcasted=true for unknown subType (default case)")
        void broadcastsUnknownSubType() {
            String result = tools.broadcastFamilySuggestion("UNKNOWN_TYPE", "context", null);
            assertThat(result).isEqualTo("{\"broadcasted\": true}");
        }

        @Test
        @DisplayName("Context longer than 50 chars is truncated — tool still returns success")
        void longContextTruncated() {
            String result = tools.broadcastFamilySuggestion("EMOTIONAL_SUPPORT", "A".repeat(60), null);
            assertThat(result).isEqualTo("{\"broadcasted\": true}");
        }

        @Test
        @DisplayName("Null context handled — tool returns success")
        void nullContextHandled() {
            String result = tools.broadcastFamilySuggestion("EMOTIONAL_SUPPORT", null, null);
            assertThat(result).isEqualTo("{\"broadcasted\": true}");
        }

        @Test
        @DisplayName("Returns broadcasted=true even when WebClient POST fails (fire-and-forget)")
        void returnsTrueEvenWhenWebClientFails() {
            // Override POST stub to fail — tool must still return immediately (fire-and-forget)
            when(responseSpec.bodyToMono(Void.class))
                    .thenReturn(Mono.error(new RuntimeException("Core service down")));

            String result = tools.broadcastFamilySuggestion("SOCIAL_ISOLATION", "alone", null);

            assertThat(result).isEqualTo("{\"broadcasted\": true}");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool 4: createTaskForFamily
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tool 4 — createTaskForFamily")
    class CreateTaskForFamilyTests {

        private void stubMembersResponse(FamilyMembersDto dto) {
            when(responseSpec.bodyToMono(FamilyMembersDto.class)).thenReturn(Mono.just(dto));
        }

        @Test
        @DisplayName("Creates task for each member excluding the sender (self)")
        void createsTasksExcludingSelf() throws Exception {
            FamilyMembersDto dto = FamilyMembersDto.builder()
                    .members(List.of(
                            FamilyMembersDto.MemberInfo.builder()
                                    .email(USER_EMAIL).fullName("John Doe").build(),   // self — excluded
                            FamilyMembersDto.MemberInfo.builder()
                                    .email(SPOUSE_EMAIL).fullName("Jane Doe").build()  // included
                    ))
                    .build();

            stubMembersResponse(dto);
            when(suggestionService.createSuggestion(eq(SPOUSE_EMAIL), any(), any(), anyString()))
                    .thenReturn(Mono.just("suggestion-id-123"));

            String result = tools.createTaskForFamily("Buy flowers", "For Jane", "SPOUSE", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("tasksCreated").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("Returns tasksCreated=0 when member list is empty")
        void returnsZeroForEmptyMemberList() throws Exception {
            stubMembersResponse(FamilyMembersDto.builder().members(List.of()).build());

            String result = tools.createTaskForFamily("Buy flowers", "desc", "SPOUSE", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("tasksCreated").asInt()).isEqualTo(0);
        }

        @Test
        @DisplayName("Returns tasksCreated=0 when WebClient returns empty Mono")
        void returnsZeroWhenWebClientReturnsEmpty() throws Exception {
            when(responseSpec.bodyToMono(FamilyMembersDto.class)).thenReturn(Mono.empty());

            String result = tools.createTaskForFamily("Task", "desc", "SPOUSE", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("tasksCreated").asInt()).isEqualTo(0);
        }

        @Test
        @DisplayName("Returns error JSON when WebClient throws")
        void returnsErrorJsonOnException() throws Exception {
            when(responseSpec.bodyToMono(FamilyMembersDto.class))
                    .thenReturn(Mono.error(new RuntimeException("Timeout")));

            String result = tools.createTaskForFamily("Task", "desc", "SPOUSE", null);
            System.out.println("DEBUG result: " + result);

            var json = objectMapper.readTree(result);
            assertThat(json.get("success").asBoolean()).isFalse();
            assertThat(json.get("errorCode").asText()).isEqualTo("TOOL_EXECUTION_ERROR");
        }

        @Test
        @DisplayName("Counts only successful tasks when some members fail")
        void countsOnlySuccessfulTasks() throws Exception {
            String member1 = "m1@example.com";
            String member2 = "m2@example.com";

            stubMembersResponse(FamilyMembersDto.builder()
                    .members(List.of(
                            FamilyMembersDto.MemberInfo.builder().email(member1).build(),
                            FamilyMembersDto.MemberInfo.builder().email(member2).build()
                    ))
                    .build());

            when(suggestionService.createSuggestion(eq(member1), any(), any(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Mongo write failed")));
            when(suggestionService.createSuggestion(eq(member2), any(), any(), anyString()))
                    .thenReturn(Mono.just("ok-id"));

            String result = tools.createTaskForFamily("Reminder", "desc", "BROTHER", null);

            // member1 fails → skipped; member2 succeeds → count = 1
            var json = objectMapper.readTree(result);
            assertThat(json.get("tasksCreated").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("Returns tasksCreated=2 when all non-self members succeed")
        void createsMultipleTasks() throws Exception {
            String m1 = "m1@example.com";
            String m2 = "m2@example.com";

            stubMembersResponse(FamilyMembersDto.builder()
                    .members(List.of(
                            FamilyMembersDto.MemberInfo.builder().email(m1).build(),
                            FamilyMembersDto.MemberInfo.builder().email(m2).build()
                    ))
                    .build());

            when(suggestionService.createSuggestion(eq(m1), any(), any(), anyString()))
                    .thenReturn(Mono.just("id-1"));
            when(suggestionService.createSuggestion(eq(m2), any(), any(), anyString()))
                    .thenReturn(Mono.just("id-2"));

            String result = tools.createTaskForFamily("Reminder", "desc", "FAMILY", null);

            var json = objectMapper.readTree(result);
            assertThat(json.get("tasksCreated").asInt()).isEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ThreadLocal email lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ThreadLocal email lifecycle")
    class ThreadLocalTests {

        @Test
        @DisplayName("setCurrentUserEmail changes the active user for tool calls")
        void setEmailChangesActiveUser() {
            tools.setCurrentUserEmail("other@example.com");

            when(userProfileService.getUserProfile("other@example.com"))
                    .thenReturn(Mono.just(Map.of("fullName", "Other User")));
            when(userContextRepository.findByEmail("other@example.com"))
                    .thenReturn(Mono.empty());

            String result = tools.getUserProfile(null);
            assertThat(result).contains("Other User");

            tools.clearCurrentUserEmail();
        }

        @Test
        @DisplayName("clearCurrentUserEmail does not throw")
        void clearEmailDoesNotThrow() {
            tools.clearCurrentUserEmail(); // should silently remove from ThreadLocal
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ToolContext Propagation Validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ToolContext propagation validation")
    class ToolContextPropagationTests {

        @Test
        @DisplayName("Resolves email from ToolContext context map when ThreadLocal is empty")
        void resolvesEmailFromToolContext() throws Exception {
            // 1. Clear ThreadLocal to simulate cross-thread/reactive loss of ThreadLocal
            tools.clearCurrentUserEmail();

            String contextEmail = "context-user@example.com";
            ToolContext toolCtx = new ToolContext(Map.of("currentUserEmail", contextEmail));

            when(userProfileService.getUserProfile(contextEmail))
                    .thenReturn(Mono.just(Map.of("fullName", "Context User")));
            when(userContextRepository.findByEmail(contextEmail))
                    .thenReturn(Mono.empty());

            // 2. Call tool with ToolContext
            String result = tools.getUserProfile(toolCtx);

            // 3. Verify it resolves correct profile
            var json = objectMapper.readTree(result);
            assertThat(json.get("found").asBoolean()).isTrue();
            assertThat(json.get("fullName").asText()).isEqualTo("Context User");
        }

        @Test
        @DisplayName("Returns structured JSON error when email is missing from both context and ThreadLocal")
        void returnsJsonErrorWhenEmailMissing() throws Exception {
            tools.clearCurrentUserEmail();
            ToolContext emptyCtx = new ToolContext(Map.of());

            String result = tools.getUserProfile(emptyCtx);

            var json = objectMapper.readTree(result);
            assertThat(json.get("success").asBoolean()).isFalse();
            assertThat(json.get("errorCode").asText()).isEqualTo("MISSING_USER_CONTEXT");
        }
    }
}
