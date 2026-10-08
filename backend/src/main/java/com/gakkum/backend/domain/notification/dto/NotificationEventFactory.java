package com.gakkum.backend.domain.notification.dto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import com.gakkum.backend.domain.notification.entity.NotificationTargetType;
import com.gakkum.backend.domain.notification.entity.NotificationType;

/**
 * 업무 사건별 알림 이벤트의 식별자·제목·본문·대상을 조립한다. DB 조회나 발행은 하지 않고 넘겨받은 값만 쓴다.
 * 이름·제목·날짜·금액은 발행 당시 값을 본문에 그대로 담는다.
 * eventId는 사건 종류와 원인 ID로 정해지므로 같은 사건을 다시 발행해도 수신자별로 한 번만 저장된다.
 */
public final class NotificationEventFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일");

    private NotificationEventFactory() {
    }

    public static NotificationEvent jobApplicationReceived(String ownerUserId, Long applicationId, Long jobId,
            String jobTitle, String studentName) {
        return jobEvent(NotificationType.JOB_APPLICATION_RECEIVED, applicationId, ownerUserId, jobId,
                "새로운 지원자가 있어요",
                studentName + " 학생이 '" + jobTitle + "' 의뢰에 지원했어요.");
    }

    public static NotificationEvent jobApplicationSelected(String studentUserId, Long paymentId, String chatRoomId,
            String jobTitle, String storeName, LocalDate finalDeadline) {
        return event(NotificationType.JOB_APPLICATION_SELECTED, paymentId, studentUserId,
                "의뢰에 선정됐어요",
                storeName + "의 '" + jobTitle + "' 의뢰에 선정됐어요. 최종 마감일은 " + date(finalDeadline)
                        + "이에요. 채팅방에서 사장님과 작업을 시작해 보세요.",
                NotificationTargetType.CHAT_ROOM, chatRoomId);
    }

    public static NotificationEvent jobApplicationRejected(String studentUserId, Long paymentId, Long jobId,
            String jobTitle, String storeName) {
        return jobEvent(NotificationType.JOB_APPLICATION_REJECTED, paymentId, studentUserId, jobId,
                "아쉽지만 선정되지 않았어요",
                storeName + "의 '" + jobTitle + "' 의뢰에 다른 지원자가 선정됐어요.");
    }

    public static NotificationEvent proposalReceived(String ownerUserId, Long proposalId, String proposalTitle,
            String studentName) {
        return proposalEvent(NotificationType.PROPOSAL_RECEIVED, proposalId, ownerUserId, proposalId,
                "새로운 제안이 도착했어요",
                studentName + " 학생이 '" + proposalTitle + "' 제안을 보냈어요.");
    }

    /** 제안을 받은 사장님에게 보내는 공감 기준 도달 알림. 같은 제안·기준의 재도달은 같은 eventId가 된다. */
    public static NotificationEvent proposalLikeMilestoneReachedForOwner(String ownerUserId, Long proposalId,
            String proposalTitle, int milestone) {
        return proposalEvent(NotificationType.PROPOSAL_LIKE_MILESTONE_REACHED, proposalId + ":" + milestone,
                ownerUserId, proposalId,
                "받은 제안에 " + milestone + "명이 공감했어요",
                "학생 손님 " + milestone + "명이 '" + proposalTitle + "' 제안에 공감했어요.");
    }

    /** 제안한 학생에게 보내는 공감 기준 도달 알림. 같은 제안·기준의 재도달은 같은 eventId가 된다. */
    public static NotificationEvent proposalLikeMilestoneReachedForStudent(String studentUserId, Long proposalId,
            String proposalTitle, int milestone) {
        return proposalEvent(NotificationType.PROPOSAL_LIKE_MILESTONE_REACHED, proposalId + ":" + milestone,
                studentUserId, proposalId,
                "내 제안에 " + milestone + "명이 공감했어요",
                "'" + proposalTitle + "' 제안에 학생 " + milestone + "명이 공감했어요.");
    }

    public static NotificationEvent proposalRejected(String studentUserId, Long proposalId, String proposalTitle,
            String storeName) {
        return proposalEvent(NotificationType.PROPOSAL_REJECTED, proposalId, studentUserId, proposalId,
                "제안이 거절됐어요",
                storeName + " 사장님이 '" + proposalTitle + "' 제안을 거절했어요.");
    }

    public static NotificationEvent proposalCancelled(String ownerUserId, Long proposalId, String proposalTitle,
            String studentName) {
        return proposalEvent(NotificationType.PROPOSAL_CANCELLED, proposalId, ownerUserId, proposalId,
                "받은 제안이 취소됐어요",
                studentName + " 학생이 '" + proposalTitle + "' 제안을 취소했어요.");
    }

    /** 사장님의 제안 결제 승인 알림. 대상은 결제로 만들어진 의뢰다. */
    public static NotificationEvent proposalAccepted(String studentUserId, Long paymentId, Long jobId,
            String jobTitle, String storeName, LocalDate finalDeadline) {
        return jobEvent(NotificationType.PROPOSAL_ACCEPTED, paymentId, studentUserId, jobId,
                "제안이 수락됐어요",
                storeName + " 사장님이 '" + jobTitle + "' 제안을 수락했어요. 최종 마감일은 " + date(finalDeadline)
                        + "이에요. 의뢰서를 확인하고 작업을 시작해 주세요.");
    }

    public static NotificationEvent jobStarted(String ownerUserId, Long jobId, String chatRoomId, String jobTitle,
            String studentName) {
        return event(NotificationType.JOB_STARTED, jobId, ownerUserId,
                "작업이 시작됐어요",
                studentName + " 학생이 '" + jobTitle + "' 작업을 시작했어요. 채팅방에서 이야기를 나눠 보세요.",
                NotificationTargetType.CHAT_ROOM, chatRoomId);
    }

    public static NotificationEvent jobDraftSubmitted(String ownerUserId, Long submissionId, Long jobId,
            String jobTitle, String studentName) {
        return jobEvent(NotificationType.JOB_DRAFT_SUBMITTED, submissionId, ownerUserId, jobId,
                "초안이 도착했어요",
                studentName + " 학생이 '" + jobTitle + "' 초안을 제출했어요. 확인해 주세요.");
    }

    public static NotificationEvent jobRevisionRequested(String studentUserId, Long submissionId, Long jobId,
            String jobTitle, String storeName) {
        return jobEvent(NotificationType.JOB_REVISION_REQUESTED, submissionId, studentUserId, jobId,
                "수정 요청이 도착했어요",
                storeName + " 사장님이 '" + jobTitle + "' 작업물의 수정을 요청했어요.");
    }

    public static NotificationEvent jobRevisionSubmitted(String ownerUserId, Long submissionId, Long jobId,
            String jobTitle, String studentName) {
        return jobEvent(NotificationType.JOB_REVISION_SUBMITTED, submissionId, ownerUserId, jobId,
                "수정안이 도착했어요",
                studentName + " 학생이 '" + jobTitle + "' 수정안을 제출했어요. 확인해 주세요.");
    }

    public static NotificationEvent jobReviewRequested(String ownerUserId, Long jobId, String jobTitle,
            String studentName) {
        return jobEvent(NotificationType.JOB_REVIEW_REQUESTED, jobId, ownerUserId, jobId,
                "후기를 남겨 주세요",
                "'" + jobTitle + "' 작업이 완료됐어요. " + studentName + " 학생에게 후기를 남겨 주세요.");
    }

    /** 완료 확인한 작업비의 정산 기록 알림. 실제 지급 완료를 단정하지 않는다. */
    public static NotificationEvent paymentSettled(String studentUserId, Long paymentId, Long jobId, String jobTitle,
            Long amount) {
        return jobEvent(NotificationType.PAYMENT_SETTLED, paymentId, studentUserId, jobId,
                "정산 내역이 반영됐어요",
                "완료 확인된 '" + jobTitle + "' 작업비 " + won(amount) + "이 정산 내역에 반영됐어요.");
    }

    public static NotificationEvent jobReviewReceived(String studentUserId, Long reviewId, Long jobId,
            String jobTitle, String storeName) {
        return jobEvent(NotificationType.JOB_REVIEW_RECEIVED, reviewId, studentUserId, jobId,
                "후기가 도착했어요",
                storeName + " 사장님이 '" + jobTitle + "' 작업에 후기를 남겼어요.");
    }

    public static NotificationEvent jobRecruitmentCancelled(String studentUserId, Long jobId, String jobTitle,
            String storeName) {
        return jobEvent(NotificationType.JOB_RECRUITMENT_CANCELLED, jobId, studentUserId, jobId,
                "지원한 의뢰의 모집이 취소됐어요",
                storeName + "의 '" + jobTitle + "' 의뢰 모집이 취소됐어요.");
    }

    public static NotificationEvent jobCancelledByOwner(String studentUserId, Long jobId, String chatRoomId,
            String jobTitle, String storeName) {
        return event(NotificationType.JOB_CANCELLED_BY_OWNER, jobId, studentUserId,
                "진행 중인 작업이 취소됐어요",
                storeName + " 사장님이 '" + jobTitle + "' 작업을 취소했어요.",
                NotificationTargetType.CHAT_ROOM, chatRoomId);
    }

    /** 기록된 환불 금액의 알림. 실제 환불 완료를 단정하지 않는다. */
    public static NotificationEvent paymentRefunded(String ownerUserId, Long paymentId, String jobTitle,
            Long refundAmount) {
        return event(NotificationType.PAYMENT_REFUNDED, paymentId, ownerUserId,
                "환불 내역이 반영됐어요",
                "'" + jobTitle + "' 결제 금액 중 " + won(refundAmount) + "이 환불 내역에 반영됐어요.",
                NotificationTargetType.PAYMENT, paymentId.toString());
    }

    private static NotificationEvent jobEvent(NotificationType type, Object sourceId, String recipientUserId,
            Long jobId, String title, String body) {
        return event(type, sourceId, recipientUserId, title, body, NotificationTargetType.JOB, jobId.toString());
    }

    private static NotificationEvent proposalEvent(NotificationType type, Object sourceId, String recipientUserId,
            Long proposalId, String title, String body) {
        return event(type, sourceId, recipientUserId, title, body, NotificationTargetType.PROPOSAL,
                proposalId.toString());
    }

    private static NotificationEvent event(NotificationType type, Object sourceId, String recipientUserId,
            String title, String body, NotificationTargetType targetType, String targetId) {
        return new NotificationEvent(eventId(type, sourceId), recipientUserId, type, title, body, targetType,
                targetId);
    }

    private static UUID eventId(NotificationType type, Object sourceId) {
        return UUID.nameUUIDFromBytes(
                ("notification:v1:" + type.name() + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
    }

    private static String date(LocalDate date) {
        return date.format(DATE_FORMAT);
    }

    private static String won(Long amount) {
        return String.format(Locale.KOREA, "%,d원", amount);
    }
}
