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

public class GetKeyResponse {
    private String key;
    private Boolean isSuccess;
}
