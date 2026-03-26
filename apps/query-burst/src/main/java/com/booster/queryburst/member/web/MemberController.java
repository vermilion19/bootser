package com.booster.queryburst.member.web;

import com.booster.queryburst.member.application.MemberService;
import com.booster.queryburst.member.application.dto.MemberCreateCommand;
import com.booster.queryburst.member.application.dto.MemberUpdateCommand;
import com.booster.queryburst.member.web.dto.request.MemberCreateRequest;
import com.booster.queryburst.member.web.dto.request.MemberUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<Void> createMember(@RequestBody MemberCreateRequest request) {
        Long memberId = memberService.createMember(new MemberCreateCommand(
                request.email(),
                request.name(),
                request.grade(),
                request.region()
        ));
        return ResponseEntity.created(URI.create("/api/members/" + memberId)).build();
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long memberId) {
        memberService.deleteMember(memberId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{memberId}")
    public ResponseEntity<Void> updateMember(
            @PathVariable Long memberId,
            @RequestBody MemberUpdateRequest request
    ) {
        memberService.updateMember(memberId, new MemberUpdateCommand(
                request.name(),
                request.grade(),
                request.region()
        ));
        return ResponseEntity.noContent().build();
    }
}
