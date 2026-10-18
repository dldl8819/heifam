package com.balancify.backend.api.group.dto;

/** The way a running draft's sets are filled in from now on: HOST or CAPTAINS. */
public record CaptainDraftEntryModeRequest(
    String entryMode
) {
}
