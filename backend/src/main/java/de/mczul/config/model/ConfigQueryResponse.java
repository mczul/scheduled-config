package de.mczul.config.model;


import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder
@With
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"key", "referenceTime"})
public class ConfigQueryResponse {
    private ZonedDateTime referenceTime;
    private String key;
    private String value;
}
