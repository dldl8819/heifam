package com.balancify.backend.api.group;

import com.balancify.backend.api.group.dto.NoticeResponse;
import com.balancify.backend.service.NoticeService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
public class GroupNoticeController {

    private final NoticeService noticeService;

    public GroupNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/{groupId}/notices")
    public List<NoticeResponse> getNotices(@PathVariable Long groupId) {
        return noticeService.getNotices(groupId);
    }

    @GetMapping("/{groupId}/notices/{noticeId}")
    public NoticeResponse getNotice(@PathVariable Long groupId, @PathVariable Long noticeId) {
        try {
            return noticeService.getNotice(groupId, noticeId);
        } catch (NoSuchElementException noSuchElementException) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                noSuchElementException.getMessage(),
                noSuchElementException
            );
        }
    }
}
