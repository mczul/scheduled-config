package de.mczul.config.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mczul.config.model.*;
import de.mczul.config.service.ScheduledConfigMapper;
import de.mczul.config.service.ScheduledConfigRepository;
import de.mczul.config.testing.IntegrationTest;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TODO: Check, whether or not integration testing scope could be reduced to security testing
 * TODO: Check if @SpringBootTest (as the implication of @IntegrationTest) could be replaced with the more lightweight @WebMvcTest(DefaultController.class)
 * TODO: Transfer implementation tests to a Mockito powered unit test like e.g.
 *
 * @ExtendWith(MockitoExtension.class) with @InjectMocks for controller + @Mock for dependencies
 */
@DisplayName("DefaultController integration tests")
@DisplayNameGeneration(ReplaceUnderscores.class)
@IntegrationTest
@AutoConfigureMockMvc
class DefaultControllerIT {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ScheduledConfigMapper scheduledConfigMapper;
    @MockBean
    private ScheduledConfigRepository scheduledConfigRepository;

    static Stream<Arguments> buildGetScheduledConfigsArgs() {
        var random = new Random();
        return Stream.of(
                arguments(0, 10,
                        SampleProvider.buildValidEntries()
                                .limit(4)
                                .map(entry -> entry.withId(random.nextInt()))
                                .collect(Collectors.toUnmodifiableList())
                ),
                arguments(1, 1, Lists.emptyList())
        );
    }

    @Nested
    @DisplayName("Value query tests")
    class ValueQueryTests {

        private void checkNullValueQueryResponse(String key) throws Exception {
            final MvcResult result = mockMvc
                    .perform(get(RestConstants.PATH_PREFIX_API + "/" + key))
                    .andExpect(status().isOk())
                    .andReturn();

            final byte[] content = result.getResponse().getContentAsByteArray();
            final ConfigQueryResponse response = objectMapper.readValue(content, ConfigQueryResponse.class);

            assertThat(response).as("Query response was NULL").isNotNull();
            assertThat(response.getReferenceTime()).as("The reference timestamp of the query response is invalid").isBeforeOrEqualTo(ZonedDateTime.now());
            assertThat(response.getValue()).as("The query response a value other than NULL").isNull();
        }

        @Test
        void handle_query_by_key_with_key_not_existing() throws Exception {
            final String key = "NOT_EXISTING";
            when(scheduledConfigRepository.findCurrentByKey(key)).thenReturn(Optional.empty());
            checkNullValueQueryResponse(key);
        }

        @Test
        void handle_query_by_key_with_null_value_entry() throws Exception {
            final String key = "KEY_WITH_NULL_VALUE";
            when(scheduledConfigRepository.findCurrentByKey(key)).thenReturn(
                    Optional.of(SampleProvider.buildValidEntries().findFirst().orElseThrow().withId(42))
            );
            checkNullValueQueryResponse(key);
        }
    }

    @Nested
    @DisplayName("Entry tests")
    class EntryTests {

        @ParameterizedTest
        @MethodSource("de.mczul.config.web.DefaultControllerIT#buildGetScheduledConfigsArgs")
        void must_translate_query_spec_to_repository_params(int pageIndex, int pageSize, List<ScheduledConfigEntry> expectedEntries) throws Exception {
        /*
         TODO: Current test might be to close to actual implementation: find a way to test relevant aspects (as e.g.
               correct interpretation of intended query and type mapping) without mirroring the implementation details
        */
            final List<ScheduledConfigDto> expectedDtos = expectedEntries.stream().map(scheduledConfigMapper::toDto).collect(Collectors.toUnmodifiableList()); //scheduledConfigMapper.fromDomainList(expectedEntries);
            when(scheduledConfigRepository.findAllLatest(any(Pageable.class))).thenReturn(new PageImpl<>(expectedEntries));
            final MvcResult result = mockMvc
                    .perform(
                            get(RestConstants.PATH_PREFIX_API)
                                    .param(RestConstants.QUERY_PARAM_PAGE_INDEX, String.valueOf(pageIndex))
                                    .param(RestConstants.QUERY_PARAM_PAGE_SIZE, String.valueOf(pageSize))
                    )
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON_VALUE))
                    .andReturn();
            verify(scheduledConfigRepository).findAllLatest(PageRequest.of(pageIndex, pageSize, Sort.by("key", "validFrom")));
            final byte[] content = result.getResponse().getContentAsByteArray();
            final ScheduledConfigDto[] dtoArray = objectMapper.readValue(content, ScheduledConfigDto[].class);

            assertThat(dtoArray).as("More records returned than page size suggests").hasSizeLessThanOrEqualTo(pageSize);
            assertThat(dtoArray).as("Returned records did not match expected list").containsExactlyInAnyOrder(expectedDtos.toArray(ScheduledConfigDto[]::new));
        }

        @Test
        void must_save_valid_samples() throws Exception {
            final ScheduledConfigDto sample = ScheduledConfigDto.builder()
                    .key("FOO")
                    .validFrom(ZonedDateTime.now().minusMinutes(1))
                    .value("BAR")
                    .created(ZonedDateTime.now().minusMinutes(1))
                    .author("john.doe")
                    .build();
            final ScheduledConfigEntry expectedEntry = scheduledConfigMapper.toEntry(sample.withId(42));

            when(scheduledConfigRepository.save(any(ScheduledConfigEntry.class))).thenReturn(expectedEntry);
            final byte[] content = objectMapper.writeValueAsBytes(sample);
            final MvcResult result = mockMvc.perform(
                    post(RestConstants.PATH_PREFIX_API)
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .content(content)
            )
                    .andExpect(status().isCreated())
                    .andReturn();

            final String responseContent = result.getResponse().getContentAsString();
            assertThat(responseContent).as("Saving valid sample must return some content").isNotBlank();

            final ScheduledConfigDto actual = objectMapper.readValue(responseContent, ScheduledConfigDto.class);
            assertAll(
                    () -> assertThat(actual.getId()).as("Entry must have a non null id after it has been saved").isNotNull(),
                    () -> assertThat(actual.getKey()).as("Entry key must be unmodified after save operation").isEqualTo(sample.getKey()),
                    () -> assertThat(actual.getValidFrom()).as("Entry valid from timestamp must be unmodified after save operation").isEqualTo(sample.getValidFrom()),
                    () -> assertThat(actual.getValue()).as("Entry value must be unmodified after save operation").isEqualTo(sample.getValue())
            );
        }

        @Test
        void must_prevent_saving_valid_entry_with_id() throws Exception {
            final ScheduledConfigDto sample = ScheduledConfigDto.builder()
                    .id(42)
                    .key("FOO")
                    .validFrom(ZonedDateTime.now().minusMinutes(1))
                    .value("BAR")
                    .created(ZonedDateTime.now().minusMinutes(1))
                    .author("john.doe")
                    .build();

            final byte[] content = objectMapper.writeValueAsBytes(sample);
            final MvcResult result = mockMvc.perform(
                    post(RestConstants.PATH_PREFIX_API)
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .content(content)
            )
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(result).isNotNull();
            assertThat(result.getResponse()).isNotNull();
            assertThat(result.getResponse().getContentAsString()).isNotEmpty();
            final String responseBody = result.getResponse().getContentAsString();
            final ValidationErrorResponse errorResponse = objectMapper.readValue(responseBody, ValidationErrorResponse.class);
            assertThat(errorResponse).isNotNull();

            assertThat(errorResponse.getViolations()).isNotNull();
            assertThat(errorResponse.getViolations()).isNotEmpty();

            for (Violation violation : errorResponse.getViolations()) {
                assertAll(
                        () -> assertThat(violation).isNotNull(),
                        () -> assertThat(violation.getFieldName()).isNotBlank(),
                        () -> assertThat(violation.getMessage()).isNotBlank()
                );
            }
        }

        @Test
        void must_handle_invalid_samples_properly() throws Exception {
            final ScheduledConfigDto sample = ScheduledConfigDto.builder()
                    .key("")
                    .validFrom(ZonedDateTime.now())
                    .value(null)
                    .build();

            final byte[] content = objectMapper.writeValueAsBytes(sample);
            final MvcResult result = mockMvc.perform(
                    post(RestConstants.PATH_PREFIX_API)
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .content(content)
            )
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).as("No content returned after posting invalid config dto").isNotBlank();
        }

    }

}
