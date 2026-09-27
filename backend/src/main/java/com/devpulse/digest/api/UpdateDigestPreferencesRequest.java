package com.devpulse.digest.api;

import jakarta.validation.constraints.NotNull;

public record UpdateDigestPreferencesRequest(@NotNull Boolean weeklyDigestEnabled) { }
