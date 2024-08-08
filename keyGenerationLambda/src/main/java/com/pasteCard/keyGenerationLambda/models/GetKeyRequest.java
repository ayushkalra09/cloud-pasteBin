package com.pasteCard.keyGenerationLambda.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class GetKeyRequest {
    private String ipAddress;
    private Long timestamp;
    private Long TTL;
    private Boolean isAlias;
    private String alias;
}
