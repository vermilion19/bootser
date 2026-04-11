package com.booster.queryburstmsa.member.application;

import com.booster.queryburstmsa.member.domain.entity.MemberEntity;
import com.booster.queryburstmsa.member.domain.repository.MemberRepository;
import com.booster.queryburstmsa.member.web.dto.MemberCreateRequest;
import com.booster.queryburstmsa.member.web.dto.MemberResponse;
import com.booster.queryburstmsa.member.web.dto.MemberUpdateRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberApplicationService {

    private final MemberRepository memberRepository;

    public MemberApplicationService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public List<MemberResponse> getMembers(Long cursor, int size) {
        return memberRepository.findMembers(cursor, PageRequest.of(0, size)).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public Long createMember(MemberCreateRequest request) {
        return memberRepository.save(MemberEntity.create(request)).getId();
    }

    @Transactional
    public void updateMember(Long memberId, MemberUpdateRequest request) {
        memberRepository.findById(memberId)
                .ifPresent(member -> member.update(request));
    }

    @Transactional
    public void deleteMember(Long memberId) {
        memberRepository.deleteById(memberId);
    }
}
