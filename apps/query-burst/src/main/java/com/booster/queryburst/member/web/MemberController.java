package com.booster.queryburst.member.web;

import com.booster.queryburst.member.application.MemberService;
import com.booster.queryburst.member.application.dto.MemberCreateCommand;
import com.booster.queryburst.member.application.dto.MemberUpdateCommand;
import com.booster.queryburst.member.web.dto.request.MemberCreateRequest;
import com.booster.queryburst.member.web.dto.request.MemberUpdateRequest;
import com.booster.queryburst.member.web.dto.response.CursorPageResponse;
import com.booster.queryburst.member.web.dto.response.MemberSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<Page<MemberSummaryResponse>> getMembers(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<MemberSummaryResponse> result = memberService.getMembers(pageable)
                .map(MemberSummaryResponse::from);
        return ResponseEntity.ok(result);
    }

    // v2: COUNT 쿼리 없음, OFFSET 방식 유지
    @GetMapping("/v2")
    public ResponseEntity<Slice<MemberSummaryResponse>> getMembersSlice(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Slice<MemberSummaryResponse> result = memberService.getMembersSlice(pageable)
                .map(MemberSummaryResponse::from);
        return ResponseEntity.ok(result);
    }

    // v3: COUNT 쿼리 없음, OFFSET 없음 (커서 기반)
    @GetMapping("/v3")
    public ResponseEntity<CursorPageResponse<MemberSummaryResponse>> getMembersByCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<MemberSummaryResponse> fetched = memberService.getMembersByCursor(cursor, size)
                .stream()
                .map(MemberSummaryResponse::from)
                .toList();

        boolean hasNext = fetched.size() > size;
        List<MemberSummaryResponse> content = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? content.getLast().id() : null;

        return ResponseEntity.ok(CursorPageResponse.of(content, hasNext, nextCursor));
    }

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
