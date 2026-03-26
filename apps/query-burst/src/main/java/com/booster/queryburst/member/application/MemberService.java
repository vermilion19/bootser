package com.booster.queryburst.member.application;

import com.booster.queryburst.member.application.dto.MemberCreateCommand;
import com.booster.queryburst.member.application.dto.MemberUpdateCommand;
import com.booster.queryburst.member.domain.Member;
import com.booster.queryburst.member.domain.MemberQueryRepository;
import com.booster.queryburst.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberQueryRepository memberQueryRepository;

    @Transactional(readOnly = true)
    public Page<Member> getMembers(Pageable pageable) {
        return memberRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Slice<Member> getMembersSlice(Pageable pageable) {
        return memberRepository.findSliceBy(pageable);
    }

    @Transactional(readOnly = true)
    public List<Member> getMembersByCursor(Long cursorId, int size) {
        return memberQueryRepository.findByCursor(cursorId, size);
    }

    public Long createMember(MemberCreateCommand command) {
        if (memberRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        Member member = Member.create(command);
        return memberRepository.save(member).getId();
    }

    public void deleteMember(Long memberId) {
        memberRepository.deleteById(memberId);
    }

    public void updateMember(Long memberId, MemberUpdateCommand command) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + memberId));
        member.update(command);
    }
}
