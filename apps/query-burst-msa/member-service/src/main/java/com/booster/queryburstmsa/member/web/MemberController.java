package com.booster.queryburstmsa.member.web;

import com.booster.queryburstmsa.member.application.MemberApplicationService;
import com.booster.queryburstmsa.member.web.dto.MemberCreateRequest;
import com.booster.queryburstmsa.member.web.dto.MemberResponse;
import com.booster.queryburstmsa.member.web.dto.MemberUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberApplicationService memberApplicationService;

    public MemberController(MemberApplicationService memberApplicationService) {
        this.memberApplicationService = memberApplicationService;
    }

    @GetMapping
    public ResponseEntity<List<MemberResponse>> getMembers(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(memberApplicationService.getMembers(cursor, size));
    }

    @PostMapping
    public ResponseEntity<Void> createMember(@RequestBody MemberCreateRequest request) {
        Long memberId = memberApplicationService.createMember(request);
        return ResponseEntity.created(URI.create("/api/members/" + memberId)).build();
    }

    @PatchMapping("/{memberId}")
    public ResponseEntity<Void> updateMember(@PathVariable Long memberId, @RequestBody MemberUpdateRequest request) {
        memberApplicationService.updateMember(memberId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long memberId) {
        memberApplicationService.deleteMember(memberId);
        return ResponseEntity.noContent().build();
    }
}
