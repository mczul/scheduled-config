package de.mczul.config.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mczul.config.AppConstants;
import de.mczul.config.common.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({AppConstants.PROFILES_TEST})
class DefaultControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ScheduledConfigMapper scheduledConfigMapper;
    @MockBean
    private ScheduledConfigRepository scheduledConfigRepository;

    static Stream<Arguments> buildGetScheduledConfigsArgs() {
        return Stream.of(
                arguments(0, 10, List.of(
                        ScheduledConfigEntry.builder().id(1).key("KEY_A").validFrom(LocalDateTime.now()).value("23").build(),
                        ScheduledConfigEntry.builder().id(2).key("KEY_A").validFrom(LocalDateTime.now().plusDays(1)).value("42").build(),
                        ScheduledConfigEntry.builder().id(3).key("KEY_B").validFrom(LocalDateTime.now()).value("4711").build()
                        )
                ),
                arguments(1, 1, List.of())
        );
    }

    private void checkNullValueQueryResponse(String key) throws Exception {
        MvcResult result = mockMvc
                .perform(get(RestConstants.PATH_PREFIX_API + "/" + key))
                .andExpect(status().isOk())
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        ConfigQueryResponse response = objectMapper.readValue(content, ConfigQueryResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getReferenceTime()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(response.getValue()).isNull();
    }

    @Test
    void queryByKeyWithKeyNotExisting() throws Exception {
        final String key = "NOT_EXISTING";
        when(scheduledConfigRepository.findCurrentByKey(key)).thenReturn(Optional.empty());
        checkNullValueQueryResponse(key);
    }

    @Test
    void queryByKeyWithNullValueEntry() throws Exception {
        final String key = "KEY_WITH_NULL_VALUE";
        when(scheduledConfigRepository.findCurrentByKey(key)).thenReturn(
                Optional.of(ScheduledConfigEntry.builder()
                        .key(key)
                        .validFrom(LocalDateTime.now().minusDays(1))
                        .value(null)
                        .build())
        );
        checkNullValueQueryResponse(key);
    }

    @ParameterizedTest
    @MethodSource("buildGetScheduledConfigsArgs")
    void getScheduledConfigs(int pageIndex, int pageSize, List<ScheduledConfigEntry> expectedEntries) throws Exception {
        /*
         TODO: Current test might be to close to actual implementation: find a way to test relevant aspects (as e.g.
               correct interpretation of intended query and type mapping) without mirroring the implementation details
        */
        List<ScheduledConfigDto> expectedDtos = scheduledConfigMapper.fromDomainList(expectedEntries);
        when(scheduledConfigRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(expectedEntries));

        MvcResult result = mockMvc
                .perform(
                        get(RestConstants.PATH_PREFIX_API)
                                .param(RestConstants.QUERY_PARAM_PAGE_INDEX, String.valueOf(pageIndex))
                                .param(RestConstants.QUERY_PARAM_PAGE_SIZE, String.valueOf(pageSize))
                )
                .andExpect(status().isOk())
                .andReturn();

        verify(scheduledConfigRepository).findAll(PageRequest.of(pageIndex, pageSize, Sort.by("key", "validFrom")));
        byte[] content = result.getResponse().getContentAsByteArray();
        ScheduledConfigDto[] dtoArray = objectMapper.readValue(content, ScheduledConfigDto[].class);

        assertThat(dtoArray).hasSizeLessThanOrEqualTo(pageSize);
        assertThat(dtoArray).containsExactlyInAnyOrder(expectedDtos.toArray(ScheduledConfigDto[]::new));
    }

    @Test
    void postScheduledConfigWithValidDto() throws Exception {
        ScheduledConfigDto sample = ScheduledConfigDto.builder()
                .key("FOO")
                .validFrom(LocalDateTime.now().minusMinutes(1))
                .value("BAR")
                .build();

        final byte[] content = objectMapper.writeValueAsBytes(sample);
        MvcResult result = mockMvc.perform(
                post(RestConstants.PATH_PREFIX_API)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content)
        )
                .andExpect(status().isCreated())
                .andReturn();

        final String responseContent = result.getResponse().getContentAsString();
        result.getResponse();
        assertThat(responseContent).isNotBlank();

        ScheduledConfigDto actual = objectMapper.readValue(responseContent, ScheduledConfigDto.class);
        assertThat(actual.getId()).isNotNull();
        assertThat(actual.getKey()).isEqualTo(sample.getKey());
        assertThat(actual.getValidFrom()).isEqualTo(sample.getValidFrom());
        assertThat(actual.getValue()).isEqualTo(sample.getValue());
    }

    @Test
    void postScheduledConfigWithInvalidDto() throws Exception {
        ScheduledConfigDto sample = ScheduledConfigDto.builder().key("").validFrom(LocalDateTime.now()).value(null).build();

        byte[] content = objectMapper.writeValueAsBytes(sample);
        MvcResult result = mockMvc.perform(
                post(RestConstants.PATH_PREFIX_API)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content)
        )
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.TEXT_PLAIN_VALUE))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isNotBlank();
    }

}