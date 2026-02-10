package com.booster.ddayservice.member.application;

import com.booster.core.web.event.MemberEvent;
import com.booster.core.web.event.MemberEvent.EventType;
import com.booster.ddayservice.member.domain.MemberReference;
import com.booster.ddayservice.member.domain.MemberReferenceRepository;
import com.booster.ddayservice.member.domain.MemberStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberSyncServiceTest {

    @InjectMocks
    private MemberSyncService memberSyncService;

    @Mock
    private MemberReferenceRepository memberReferenceRepository;

    @Nested
    @DisplayName("handleSignup")
    class HandleSignup {

        @Test
        @DisplayName("SIGNUP 이벤트 수신 시 MemberReference를 저장한다")
        void should_saveMemberReference_when_signupEvent() {
            MemberEvent event = MemberEvent.of(1L, "testUser", EventType.SIGNUP);
            given(memberReferenceRepository.findByMemberId(1L)).willReturn(Optional.empty());

            memberSyncService.process(event);

            ArgumentCaptor<MemberReference> captor = ArgumentCaptor.forClass(MemberReference.class);
            verify(memberReferenceRepository).save(captor.capture());

            MemberReference saved = captor.getValue();
            assertThat(saved.getMemberId()).isEqualTo(1L);
            assertThat(saved.getNickname()).isEqualTo("testUser");
            assertThat(saved.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("이미 존재하는 memberId로 SIGNUP 이벤트 수신 시 저장하지 않는다 (멱등성)")
        void should_notSave_when_memberAlreadyExists() {
            MemberEvent event = MemberEvent.of(1L, "testUser", EventType.SIGNUP);
            MemberReference existing = MemberReference.builder()
                    .memberId(1L)
                    .nickname("testUser")
                    .status(MemberStatus.ACTIVE)
                    .build();
            given(memberReferenceRepository.findByMemberId(1L)).willReturn(Optional.of(existing));

            memberSyncService.process(event);

            verify(memberReferenceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("handleUpdate")
    class HandleUpdate {

        @Test
        @DisplayName("UPDATE 이벤트 수신 시 닉네임을 변경한다")
        void should_updateNickname_when_updateEvent() {
            MemberReference existing = MemberReference.builder()
                    .memberId(1L)
                    .nickname("oldNickname")
                    .status(MemberStatus.ACTIVE)
                    .build();
            MemberEvent event = MemberEvent.of(1L, "newNickname", EventType.UPDATE);
            given(memberReferenceRepository.findByMemberId(1L)).willReturn(Optional.of(existing));

            memberSyncService.process(event);

            assertThat(existing.getNickname()).isEqualTo("newNickname");
        }

        @Test
        @DisplayName("존재하지 않는 회원의 UPDATE 이벤트는 무시한다")
        void should_ignore_when_memberNotFound() {
            MemberEvent event = MemberEvent.of(999L, "newNickname", EventType.UPDATE);
            given(memberReferenceRepository.findByMemberId(999L)).willReturn(Optional.empty());

            memberSyncService.process(event);

            verify(memberReferenceRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("handleDelete")
    class HandleDelete {

        @Test
        @DisplayName("DELETE 이벤트 수신 시 상태를 DELETED로 변경한다")
        void should_markDeleted_when_deleteEvent() {
            MemberReference existing = MemberReference.builder()
                    .memberId(1L)
                    .nickname("testUser")
                    .status(MemberStatus.ACTIVE)
                    .build();
            MemberEvent event = MemberEvent.of(1L, "testUser", EventType.DELETE);
            given(memberReferenceRepository.findByMemberId(1L)).willReturn(Optional.of(existing));

            memberSyncService.process(event);

            assertThat(existing.getStatus()).isEqualTo(MemberStatus.DELETED);
        }

        @Test
        @DisplayName("존재하지 않는 회원의 DELETE 이벤트는 에러 없이 무시한다")
        void should_ignore_when_memberNotFoundOnDelete() {
            MemberEvent event = MemberEvent.of(999L, "testUser", EventType.DELETE);
            given(memberReferenceRepository.findByMemberId(999L)).willReturn(Optional.empty());

            memberSyncService.process(event);

            verify(memberReferenceRepository, never()).save(any());
            verify(memberReferenceRepository, never()).delete(any(MemberReference.class));
        }
    }

    @Nested
    @DisplayName("unhandled event type")
    class UnhandledEventType {

        @Test
        @DisplayName("SIGNIN 이벤트는 아무 동작도 하지 않는다")
        void should_doNothing_when_signinEvent() {
            MemberEvent event = MemberEvent.of(1L, "testUser", EventType.SIGNIN);

            memberSyncService.process(event);

            verify(memberReferenceRepository, never()).findByMemberId(any());
            verify(memberReferenceRepository, never()).save(any());
        }
    }
}
