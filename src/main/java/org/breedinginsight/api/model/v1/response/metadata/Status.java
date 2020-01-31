package org.breedinginsight.api.model.v1.response.metadata;

import lombok.*;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Status {
    private StatusCode messageType;
    private String message;
}