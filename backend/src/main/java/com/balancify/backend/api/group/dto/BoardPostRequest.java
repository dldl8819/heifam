package com.balancify.backend.api.group.dto;

public record BoardPostRequest(
    String title,
    String content,
    // The YouTube link of a post on the video board; ignored on the other boards.
    String videoUrl
) {
}
