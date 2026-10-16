package com.balancify.backend.api.points.dto;

/** After a menu is opened: whether it paid now, and how many menus paid today against the cap. */
public record PointMenuVisitResponse(
    boolean earned,
    int menusToday,
    int dailyCap
) {
}
