package com.gakkum.backend.domain.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;

@DisplayName("알림 이벤트 조립 (수신자·종류·대상·문구·이벤트 ID)")
class NotificationEventFactoryTest {

    private static final String OWNER = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String CHAT_ROOM = "01K58M6PJV8VAJMXHBHJ2CHAT1";
    private static final String JOB_TITLE = "메뉴판 디자인";
    private static final String PROPOSAL_TITLE = "메뉴판 개선 제안";
    private static final String STORE = "가꿈 카페";
    private static final String STUDENT_NAME = "김학생";
    private static final LocalDate FINAL_DEADLINE = LocalDate.of(2026, 10, 15);

    @Test
    @DisplayName("새 지원은 사장님에게 의뢰를 대상으로, 지원서 ID로 식별해 알린다")
    void jobApplicationReceived() {
        assertEvent(NotificationEventFactory.jobApplicationReceived(OWNER, 123L, 42L, JOB_TITLE, STUDENT_NAME),
                "notification:v1:JOB_APPLICATION_RECEIVED:123", OWNER, NotificationType.JOB_APPLICATION_RECEIVED,
                NotificationTargetType.JOB, "42",
                "새로운 지원자가 있어요", "김학생 학생이 '메뉴판 디자인' 의뢰에 지원했어요.");
    }

    @Test
    @DisplayName("선정은 선정 학생에게 채팅방을 대상으로, 결제 ID로 식별해 마감일과 함께 알린다")
    void jobApplicationSelected() {
        assertEvent(NotificationEventFactory.jobApplicationSelected(
                        STUDENT, 91L, CHAT_ROOM, JOB_TITLE, STORE, FINAL_DEADLINE),
                "notification:v1:JOB_APPLICATION_SELECTED:91", STUDENT, NotificationType.JOB_APPLICATION_SELECTED,
                NotificationTargetType.CHAT_ROOM, CHAT_ROOM,
                "의뢰에 선정됐어요",
                "가꿈 카페의 '메뉴판 디자인' 의뢰에 선정됐어요. 최종 마감일은 10월 15일이에요. 채팅방에서 사장님과 작업을 시작해 보세요.");
    }

    @Test
    @DisplayName("미선정은 대기 지원자에게 발생 원인인 의뢰를 대상으로, 결제 ID로 식별해 알린다")
    void jobApplicationRejected() {
        assertEvent(NotificationEventFactory.jobApplicationRejected(STUDENT, 91L, 42L, JOB_TITLE, STORE),
                "notification:v1:JOB_APPLICATION_REJECTED:91", STUDENT, NotificationType.JOB_APPLICATION_REJECTED,
                NotificationTargetType.JOB, "42",
                "아쉽지만 선정되지 않았어요", "가꿈 카페의 '메뉴판 디자인' 의뢰에 다른 지원자가 선정됐어요.");
    }

    @Test
    @DisplayName("제안 도착은 받은 사장님에게 제안을 대상으로, 제안 ID로 식별해 알린다")
    void proposalReceived() {
        assertEvent(NotificationEventFactory.proposalReceived(OWNER, 31L, PROPOSAL_TITLE, STUDENT_NAME),
                "notification:v1:PROPOSAL_RECEIVED:31", OWNER, NotificationType.PROPOSAL_RECEIVED,
                NotificationTargetType.PROPOSAL, "31",
                "새로운 제안이 도착했어요", "김학생 학생이 '메뉴판 개선 제안' 제안을 보냈어요.");
    }

    @Test
    @DisplayName("공감 기준 도달은 사장님과 제안 학생에게 제안을 대상으로, 제안 ID와 기준 인원으로 식별해 각각의 문구로 알린다")
    void proposalLikeMilestoneReached() {
        assertEvent(NotificationEventFactory.proposalLikeMilestoneReachedForOwner(OWNER, 31L, PROPOSAL_TITLE, 10),
                "notification:v1:PROPOSAL_LIKE_MILESTONE_REACHED:31:10", OWNER,
                NotificationType.PROPOSAL_LIKE_MILESTONE_REACHED, NotificationTargetType.PROPOSAL, "31",
                "받은 제안에 10명이 공감했어요", "학생 손님 10명이 '메뉴판 개선 제안' 제안에 공감했어요.");
        assertEvent(NotificationEventFactory.proposalLikeMilestoneReachedForStudent(
                        STUDENT, 31L, PROPOSAL_TITLE, 30),
                "notification:v1:PROPOSAL_LIKE_MILESTONE_REACHED:31:30", STUDENT,
                NotificationType.PROPOSAL_LIKE_MILESTONE_REACHED, NotificationTargetType.PROPOSAL, "31",
                "내 제안에 30명이 공감했어요", "'메뉴판 개선 제안' 제안에 학생 30명이 공감했어요.");
    }

    @Test
    @DisplayName("공감 알림의 이벤트 ID는 같은 제안·기준이면 수신자와 무관하게 같고 기준이나 제안이 다르면 다르다")
    void proposalLikeMilestoneEventIdDependsOnProposalAndMilestone() {
        UUID ownerAtTen = NotificationEventFactory
                .proposalLikeMilestoneReachedForOwner(OWNER, 31L, PROPOSAL_TITLE, 10).eventId();

        assertThat(NotificationEventFactory.proposalLikeMilestoneReachedForStudent(
                STUDENT, 31L, PROPOSAL_TITLE, 10).eventId()).isEqualTo(ownerAtTen);
        assertThat(List.of(
                NotificationEventFactory.proposalLikeMilestoneReachedForOwner(OWNER, 31L, PROPOSAL_TITLE, 30),
                NotificationEventFactory.proposalLikeMilestoneReachedForOwner(OWNER, 31L, PROPOSAL_TITLE, 50),
                NotificationEventFactory.proposalLikeMilestoneReachedForOwner(OWNER, 32L, PROPOSAL_TITLE, 10)))
                .extracting(NotificationEvent::eventId)
                .doesNotContain(ownerAtTen)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("제안 거절은 제안 학생에게 제안을 대상으로, 제안 ID로 식별해 알린다")
    void proposalRejected() {
        assertEvent(NotificationEventFactory.proposalRejected(STUDENT, 31L, PROPOSAL_TITLE, STORE),
                "notification:v1:PROPOSAL_REJECTED:31", STUDENT, NotificationType.PROPOSAL_REJECTED,
                NotificationTargetType.PROPOSAL, "31",
                "제안이 거절됐어요", "가꿈 카페 사장님이 '메뉴판 개선 제안' 제안을 거절했어요.");
    }

    @Test
    @DisplayName("제안 취소는 받은 사장님에게 제안을 대상으로, 제안 ID로 식별해 알린다")
    void proposalCancelled() {
        assertEvent(NotificationEventFactory.proposalCancelled(OWNER, 31L, PROPOSAL_TITLE, STUDENT_NAME),
                "notification:v1:PROPOSAL_CANCELLED:31", OWNER, NotificationType.PROPOSAL_CANCELLED,
                NotificationTargetType.PROPOSAL, "31",
                "받은 제안이 취소됐어요", "김학생 학생이 '메뉴판 개선 제안' 제안을 취소했어요.");
    }

    @Test
    @DisplayName("제안 수락은 제안 학생에게 결제로 만들어진 의뢰를 대상으로, 결제 ID로 식별해 마감일과 함께 알린다")
    void proposalAccepted() {
        assertEvent(NotificationEventFactory.proposalAccepted(
                        STUDENT, 92L, 42L, PROPOSAL_TITLE, STORE, FINAL_DEADLINE),
                "notification:v1:PROPOSAL_ACCEPTED:92", STUDENT, NotificationType.PROPOSAL_ACCEPTED,
                NotificationTargetType.JOB, "42",
                "제안이 수락됐어요",
                "가꿈 카페 사장님이 '메뉴판 개선 제안' 제안을 수락했어요. 최종 마감일은 10월 15일이에요. 의뢰서를 확인하고 작업을 시작해 주세요.");
    }

    @Test
    @DisplayName("작업 시작은 사장님에게 채팅방을 대상으로, 의뢰 ID로 식별해 알린다")
    void jobStarted() {
        assertEvent(NotificationEventFactory.jobStarted(OWNER, 42L, CHAT_ROOM, JOB_TITLE, STUDENT_NAME),
                "notification:v1:JOB_STARTED:42", OWNER, NotificationType.JOB_STARTED,
                NotificationTargetType.CHAT_ROOM, CHAT_ROOM,
                "작업이 시작됐어요", "김학생 학생이 '메뉴판 디자인' 작업을 시작했어요. 채팅방에서 이야기를 나눠 보세요.");
    }

    @Test
    @DisplayName("초안 도착은 사장님에게 의뢰를 대상으로, 제출물 ID로 식별해 알린다")
    void jobDraftSubmitted() {
        assertEvent(NotificationEventFactory.jobDraftSubmitted(OWNER, 81L, 42L, JOB_TITLE, STUDENT_NAME),
                "notification:v1:JOB_DRAFT_SUBMITTED:81", OWNER, NotificationType.JOB_DRAFT_SUBMITTED,
                NotificationTargetType.JOB, "42",
                "초안이 도착했어요", "김학생 학생이 '메뉴판 디자인' 초안을 제출했어요. 확인해 주세요.");
    }

    @Test
    @DisplayName("수정 요청은 담당 학생에게 의뢰를 대상으로, 제출물 ID로 식별해 알린다")
    void jobRevisionRequested() {
        assertEvent(NotificationEventFactory.jobRevisionRequested(STUDENT, 81L, 42L, JOB_TITLE, STORE),
                "notification:v1:JOB_REVISION_REQUESTED:81", STUDENT, NotificationType.JOB_REVISION_REQUESTED,
                NotificationTargetType.JOB, "42",
                "수정 요청이 도착했어요", "가꿈 카페 사장님이 '메뉴판 디자인' 작업물의 수정을 요청했어요.");
    }

    @Test
    @DisplayName("수정안 도착은 사장님에게 의뢰를 대상으로, 제출물 ID로 식별해 알리고 자동 완료를 안내하지 않는다")
    void jobRevisionSubmitted() {
        NotificationEvent event = NotificationEventFactory.jobRevisionSubmitted(
                OWNER, 82L, 42L, JOB_TITLE, STUDENT_NAME);

        assertEvent(event, "notification:v1:JOB_REVISION_SUBMITTED:82", OWNER,
                NotificationType.JOB_REVISION_SUBMITTED, NotificationTargetType.JOB, "42",
                "수정안이 도착했어요", "김학생 학생이 '메뉴판 디자인' 수정안을 제출했어요. 확인해 주세요.");
        assertThat(event.body()).doesNotContain("자동 완료", "7일");
    }

    @Test
    @DisplayName("후기 요청은 사장님에게 의뢰를 대상으로, 의뢰 ID로 식별해 알린다")
    void jobReviewRequested() {
        assertEvent(NotificationEventFactory.jobReviewRequested(OWNER, 42L, JOB_TITLE, STUDENT_NAME),
                "notification:v1:JOB_REVIEW_REQUESTED:42", OWNER, NotificationType.JOB_REVIEW_REQUESTED,
                NotificationTargetType.JOB, "42",
                "후기를 남겨 주세요", "'메뉴판 디자인' 작업이 완료됐어요. 김학생 학생에게 후기를 남겨 주세요.");
    }

    @Test
    @DisplayName("정산은 담당 학생에게 의뢰를 대상으로, 결제 ID로 식별해 내역 반영만 알리고 지급 완료를 단정하지 않는다")
    void paymentSettled() {
        NotificationEvent event = NotificationEventFactory.paymentSettled(STUDENT, 91L, 42L, JOB_TITLE, 100_000L);

        assertEvent(event, "notification:v1:PAYMENT_SETTLED:91", STUDENT, NotificationType.PAYMENT_SETTLED,
                NotificationTargetType.JOB, "42",
                "정산 내역이 반영됐어요", "완료 확인된 '메뉴판 디자인' 작업비 100,000원이 정산 내역에 반영됐어요.");
        assertThat(event.title() + event.body()).doesNotContain("입금", "지급");
    }

    @Test
    @DisplayName("후기 도착은 담당 학생에게 의뢰를 대상으로, 후기 ID로 식별해 알린다")
    void jobReviewReceived() {
        assertEvent(NotificationEventFactory.jobReviewReceived(STUDENT, 301L, 42L, JOB_TITLE, STORE),
                "notification:v1:JOB_REVIEW_RECEIVED:301", STUDENT, NotificationType.JOB_REVIEW_RECEIVED,
                NotificationTargetType.JOB, "42",
                "후기가 도착했어요", "가꿈 카페 사장님이 '메뉴판 디자인' 작업에 후기를 남겼어요.");
    }

    @Test
    @DisplayName("모집 취소는 대기 지원자에게 의뢰를 대상으로, 의뢰 ID로 식별해 알린다")
    void jobRecruitmentCancelled() {
        assertEvent(NotificationEventFactory.jobRecruitmentCancelled(STUDENT, 42L, JOB_TITLE, STORE),
                "notification:v1:JOB_RECRUITMENT_CANCELLED:42", STUDENT, NotificationType.JOB_RECRUITMENT_CANCELLED,
                NotificationTargetType.JOB, "42",
                "지원한 의뢰의 모집이 취소됐어요", "가꿈 카페의 '메뉴판 디자인' 의뢰 모집이 취소됐어요.");
    }

    @Test
    @DisplayName("사장님의 작업 취소는 담당 학생에게 채팅방을 대상으로, 의뢰 ID로 식별해 알린다")
    void jobCancelledByOwner() {
        assertEvent(NotificationEventFactory.jobCancelledByOwner(STUDENT, 42L, CHAT_ROOM, JOB_TITLE, STORE),
                "notification:v1:JOB_CANCELLED_BY_OWNER:42", STUDENT, NotificationType.JOB_CANCELLED_BY_OWNER,
                NotificationTargetType.CHAT_ROOM, CHAT_ROOM,
                "진행 중인 작업이 취소됐어요", "가꿈 카페 사장님이 '메뉴판 디자인' 작업을 취소했어요.");
    }

    @Test
    @DisplayName("환불은 사장님에게 결제를 대상으로, 결제 ID로 식별해 기록된 환불 금액의 내역 반영만 알린다")
    void paymentRefunded() {
        NotificationEvent partial = NotificationEventFactory.paymentRefunded(OWNER, 91L, JOB_TITLE, 80_000L);

        assertEvent(partial, "notification:v1:PAYMENT_REFUNDED:91", OWNER, NotificationType.PAYMENT_REFUNDED,
                NotificationTargetType.PAYMENT, "91",
                "환불 내역이 반영됐어요", "'메뉴판 디자인' 결제 금액 중 80,000원이 환불 내역에 반영됐어요.");
        assertThat(partial.title() + partial.body()).doesNotContain("환불됐", "환불 완료", "입금");
        // 의뢰서 거절의 전액 환불도 같은 종류로, 기록된 금액 그대로 알린다
        assertThat(NotificationEventFactory.paymentRefunded(OWNER, 92L, JOB_TITLE, 1_250_000L).body())
                .isEqualTo("'메뉴판 디자인' 결제 금액 중 1,250,000원이 환불 내역에 반영됐어요.");
    }

    @Test
    @DisplayName("같은 원인 ID라도 사건 종류가 다르면 이벤트 ID가 다르고, 같은 사건을 다시 조립하면 같은 이벤트 ID가 된다")
    void eventIdIsDeterministicPerTypeAndSource() {
        NotificationEvent selected = NotificationEventFactory.jobApplicationSelected(
                STUDENT, 91L, CHAT_ROOM, JOB_TITLE, STORE, FINAL_DEADLINE);
        NotificationEvent rejected = NotificationEventFactory.jobApplicationRejected(STUDENT, 91L, 42L, JOB_TITLE, STORE);
        NotificationEvent settled = NotificationEventFactory.paymentSettled(STUDENT, 91L, 42L, JOB_TITLE, 100_000L);
        NotificationEvent refunded = NotificationEventFactory.paymentRefunded(OWNER, 91L, JOB_TITLE, 80_000L);

        assertThat(List.of(selected, rejected, settled, refunded)).extracting(NotificationEvent::eventId)
                .doesNotHaveDuplicates();
        assertThat(NotificationEventFactory.paymentRefunded(OWNER, 91L, "바뀐 제목", 1L).eventId())
                .isEqualTo(refunded.eventId());
        // 여러 수정안은 제출물마다 다른 이벤트다
        assertThat(NotificationEventFactory.jobRevisionSubmitted(OWNER, 82L, 42L, JOB_TITLE, STUDENT_NAME).eventId())
                .isNotEqualTo(NotificationEventFactory
                        .jobRevisionSubmitted(OWNER, 83L, 42L, JOB_TITLE, STUDENT_NAME).eventId());
    }

    @Test
    @DisplayName("치환한 제목이 길어도 알림 제목은 저장 한도 255자를 넘지 않는다")
    void titleStaysWithinColumnLimit() {
        String longTitle = "가".repeat(1000);

        assertThat(List.of(
                NotificationEventFactory.jobApplicationReceived(OWNER, 1L, 2L, longTitle, longTitle),
                NotificationEventFactory.proposalLikeMilestoneReachedForOwner(OWNER, 1L, longTitle, 50),
                NotificationEventFactory.paymentRefunded(OWNER, 1L, longTitle, Long.MAX_VALUE)))
                .allSatisfy(event -> assertThat(event.title()).hasSizeLessThanOrEqualTo(255));
    }

    private void assertEvent(NotificationEvent event, String eventIdSource, String recipientUserId,
            NotificationType type, NotificationTargetType targetType, String targetId, String title, String body) {
        assertThat(event.eventId())
                .isEqualTo(UUID.nameUUIDFromBytes(eventIdSource.getBytes(StandardCharsets.UTF_8)));
        assertThat(event.recipientUserId()).isEqualTo(recipientUserId);
        assertThat(event.type()).isEqualTo(type);
        assertThat(event.targetType()).isEqualTo(targetType);
        assertThat(event.targetId()).isEqualTo(targetId);
        assertThat(event.title()).isEqualTo(title);
        assertThat(event.body()).isEqualTo(body);
    }
}
