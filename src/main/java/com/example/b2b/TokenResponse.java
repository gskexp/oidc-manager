package com.example.b2b;

import com.fasterxml.jackson.databind.JsonNode;

/** Token endpoint result, including the original response fields. */
public record TokenResponse(String accessToken, String tokenType, Long expiresIn, JsonNode response) { }
