package com.booster.ddayservice.member.application;

import com.booster.core.web.event.MemberEvent;
import com.booster.ddayservice.member.domain.MemberReference;
import com.booster.ddayservice.member.domain.MemberReferenceRepository;
import com.booster.ddayservice.member.domain.MemberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MemberSyncService {

    private final MemberReferenceRepository memberReferenceRepository;

    public void process(MemberEvent event) {
        switch (event.eventType()) {
            case SIGNUP -> handleSignup(event);
            case UPDATE -> handleUpdate(event);
            case DELETE -> handleDelete(event);
            default -> log.warn("Unhandled event type: {}", event.eventType());

        }
    }

    private void handleSignup(MemberEvent event) {
        if (memberReferenceRepository.findByMemberId(event.memberId()).isPresent()) {
            log.warn("Member already exists: {}", event.memberId());
            return;
        }

        memberReferenceRepository.save(
                MemberReference.builder()
                        .memberId(event.memberId())
                        .nickname(event.nickName())
                        .status(MemberStatus.ACTIVE)
                        .build()
        );
    }

    private void handleUpdate(MemberEvent event) {
        memberReferenceRepository.findByMemberId(event.memberId())
                .ifPresent(ref -> ref.updateNickname(event.nickName()));
    }

    private void handleDelete(MemberEvent event) {
        memberReferenceRepository.findByMemberId(event.memberId())
                .ifPresent(MemberReference::delete);
    }

}
