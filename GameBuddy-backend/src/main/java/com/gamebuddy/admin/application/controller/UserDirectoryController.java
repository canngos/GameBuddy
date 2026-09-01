package com.gamebuddy.admin.application.controller;

import com.gamebuddy.admin.domain.service.DirectoryFilter;
import com.gamebuddy.admin.domain.service.UserDirectoryService;
import com.gamebuddy.admin.interfaces.response.UserDirectoryResponse;
import com.gamebuddy.admin.interfaces.response.UserIdsResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Looking players up, so a code can be addressed to them.
 *
 * <p>The console deliberately never had this. {@code AccountsScreen} lists only banned
 * accounts and says why in its own comment: browsing everybody would make the console a
 * directory of the user base, searchable and screenshot-able, and moderating never needed
 * it. Sending somebody a gift does — you cannot address a code to a person you cannot
 * find — so the compromise is that this exists, is behind ADMIN twice over, is paged, and
 * carries only what it takes to recognise an account: name, address, picture, when they
 * joined, when they were last seen, and whether they are already a member.
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "User moderation and catalogue management")
public class UserDirectoryController {

    private final UserDirectoryService directory;

    @GetMapping
    public ResponseEntity<UserDirectoryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") DirectoryFilter filter,
            @PageableDefault(size = 30) Pageable pageable) {

        UserDirectoryResponse response = new UserDirectoryResponse();
        response.setBody(new BaseBody<>(directory.search(q, filter, pageable)));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }

    /** Everybody matching, as ids — what the "select all" control resolves to. */
    @GetMapping("/ids")
    public ResponseEntity<UserIdsResponse> ids(
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "ALL") DirectoryFilter filter) {

        UserIdsResponse response = new UserIdsResponse();
        response.setBody(new BaseBody<>(directory.ids(q, filter)));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return ResponseEntity.ok(response);
    }
}
