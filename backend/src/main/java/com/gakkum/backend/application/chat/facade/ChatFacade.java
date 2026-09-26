package com.gakkum.backend.application.chat.facade;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.LastMessage;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.Room;
import com.gakkum.backend.application.chat.dto.ChatMessageListResponse;
import com.gakkum.backend.application.chat.dto.DeadlineType;
import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient;
import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient.PresignedUpload;
import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient.PresignedView;
import com.gakkum.backend.domain.chat.service.ChatService;
import com.gakkum.backend.domain.chat.entity.ChatAttachmentUpload;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.MarkReadCommand;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.PrepareAttachmentUploadCommand;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.SendAttachmentMessageCommand;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.SendTextMessageCommand;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.PrepareAttachmentUploadResult;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendAttachmentMessageResult;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ChatFacade {

    private final UserService userService;
    private final OwnerRepository ownerRepository;
    private final StudentRepository studentRepository;
    private final JobRepository jobRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobSubmissionRepository jobSubmissionRepository;
    private final ChatService chatService;
    private final ChatAttachmentStorageClient chatAttachmentStorageClient;

    @Transactional(readOnly = true)
    public ChatRoomListResponse getMyChatRooms(String username) {
        // 조회 대상 결정
        User viewer = userService.getActiveUser(username);
        List<Job> jobs = findJobs(viewer);
        if (jobs.isEmpty()) {
            return ChatRoomListResponse.of(List.of());
        }

        // 실제로 만들어진 방 선택
        Map<Long, Job> jobsById = jobs.stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        List<ChatRoom> rooms = new ArrayList<>(
                chatService.findRoomsByJobIds(jobs.stream().map(Job::getId).toList()));
        if (rooms.isEmpty()) {
            return ChatRoomListResponse.of(List.of());
        }

        return ChatRoomListResponse.of(toRooms(viewer, jobsById, rooms));
    }

    @Transactional(readOnly = true)
    public Room getChatRoom(String username, String roomId) {
        User viewer = userService.getActiveUser(username);
        ChatRoom room = chatService.findRoom(roomId);
        Job job = jobRepository.findById(room.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        requireParticipant(viewer, job);
        return toRooms(viewer, Map.of(job.getId(), job), List.of(room)).get(0);
    }

    @Transactional(readOnly = true)
    public ChatMessageListResponse getMessages(String username, String roomId) {
        User viewer = userService.getActiveUser(username);
        ChatRoom room = chatService.findRoom(roomId);
        Job job = jobRepository.findById(room.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        requireParticipant(viewer, job);
        return ChatMessageListResponse.from(chatService.findMessages(roomId));
    }

    private List<Room> toRooms(User viewer, Map<Long, Job> jobsById, List<ChatRoom> rooms) {
        List<Job> roomJobs = rooms.stream().map(room -> jobsById.get(room.getJobId())).toList();
        List<Long> jobIds = roomJobs.stream().map(Job::getId).toList();
        Map<Long, Counterpart> counterparts = findCounterparts(viewer.getRole(), roomJobs);
        Map<Long, String> applicationContentByJob = jobApplicationRepository
                .findByJobIdInAndStatus(jobIds, JobApplicationStatus.ACCEPTED).stream()
                .filter(application -> application.getStudentProfileId()
                        .equals(jobsById.get(application.getJobId()).getSelectedStudentProfileId()))
                .filter(application -> application.getContent() != null)
                .collect(Collectors.toMap(JobApplication::getJobId, JobApplication::getContent));
        List<JobSubmission> submissions = jobSubmissionRepository.findByJobIdIn(jobIds);
        Map<Long, JobSubmission> latestSubmissionByJob = submissions.stream()
                .collect(Collectors.toMap(JobSubmission::getJobId, Function.identity(),
                        (left, right) -> left.getRevisionNumber() > right.getRevisionNumber() ? left : right));
        Set<Long> approvedDraftJobIds = submissions.stream()
                .filter(submission -> submission.getSubmissionType() == JobSubmissionType.DRAFT
                        && submission.getReviewStatus() == JobSubmissionReviewStatus.APPROVED)
                .map(JobSubmission::getJobId)
                .collect(Collectors.toSet());
        List<String> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<String, ChatMessage> latestByRoom = chatService.findLatestMessages(roomIds);
        Map<String, Long> unreadByRoom = chatService.countUnreadMessages(
                roomIds, viewer.getId(), viewer.getRole() == UserRole.OWNER);

        // 최근 메시지 순, 빈 방은 생성일 순
        List<ChatRoom> sortedRooms = new ArrayList<>(rooms);
        sortedRooms.sort((left, right) -> compareRooms(left, right, latestByRoom));

        return sortedRooms.stream().map(room -> {
            Job job = jobsById.get(room.getJobId());
            Counterpart counterpart = counterparts.get(job.getId());
            ChatMessage latest = latestByRoom.get(room.getId());
            JobSubmission latestSubmission = latestSubmissionByJob.get(job.getId());
            DeadlineType deadlineType = job.getStatus() == JobStatus.CLOSED ? null
                    : approvedDraftJobIds.contains(job.getId()) ? DeadlineType.FINAL : DeadlineType.DRAFT;
            return Room.of(room, job, counterpart.name, counterpart.profileImageUrl,
                    latest == null ? null : toLastMessage(latest), unreadByRoom.getOrDefault(room.getId(), 0L),
                    deadlineType,
                    deadlineType == null ? null : deadlineType == DeadlineType.DRAFT
                            ? job.getDraftDeadline() : job.getFinalDeadline(),
                    latestSubmission == null ? null : latestSubmission.getReviewStatus(),
                    applicationContentByJob.get(job.getId()));
        }).toList();
    }

    @Transactional
    public void markRead(MarkReadCommand command) {
        User viewer = userService.getActiveUser(command.getUsername());
        ChatRoom room = chatService.findLockedRoom(command.getRoomId());
        Job job = jobRepository.findById(room.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        requireParticipant(viewer, job);

        chatService.markRead(room, viewer.getRole(), command.getLastReadMessageId());
    }

    @Transactional
    public SendMessageResult sendTextMessage(SendTextMessageCommand command) {
        User sender = userService.getActiveUser(command.getUsername());
        ChatRoom room = chatService.findLockedRoom(command.getRoomId());
        Job job = jobRepository.findById(room.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        requireParticipant(sender, job);

        return chatService.sendTextMessage(room, sender.getId(), command.getClientMessageId(), command.getContent());
    }

    @Transactional
    public PrepareAttachmentUploadResult prepareAttachmentUpload(PrepareAttachmentUploadCommand command) {
        User uploader = userService.getActiveUser(command.getUsername());
        ChatRoom room = chatService.findRoom(command.getRoomId());
        Job job = jobRepository.findById(room.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));

        // 권한 확인
        requireParticipant(uploader, job);

        ChatAttachmentUpload upload = chatService.createAttachmentUpload(room, uploader.getId(), command.getType(),
                command.getFileName(), command.getContentType(), command.getSize());
        PresignedUpload presigned = chatAttachmentStorageClient.presignUpload(
                upload.getStorageKey(), upload.getContentType(), upload.getFileSize());
        return PrepareAttachmentUploadResult.of(upload.getId(), presigned.url(), presigned.headers(),
                toLocalDateTime(presigned.expiresAt()));
    }

    @Transactional
    public SendAttachmentMessageResult sendAttachmentMessage(SendAttachmentMessageCommand command) {
        User sender = userService.getActiveUser(command.getUsername());
        ChatRoom room = chatService.findLockedRoom(command.getRoomId());
        Job job = jobRepository.findById(room.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        requireParticipant(sender, job);

        SendMessageResult result = chatService.sendAttachmentMessage(room, sender.getId(),
                command.getClientMessageId(), command.getType(), command.getUploadId());
        ChatMessage message = result.getMessage();
        PresignedView view = chatAttachmentStorageClient.presignView(
                message.getAttachmentKey(), message.getType(), message.getAttachmentName());
        return SendAttachmentMessageResult.of(message, result.isCreated(), view.url(),
                toLocalDateTime(view.expiresAt()));
    }

    private List<Job> findJobs(User viewer) {
        if (viewer.getRole() == UserRole.OWNER) {
            Owner owner = ownerRepository.findByUserId(viewer.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_FORBIDDEN));
            return jobRepository.findByOwnerProfileId(owner.getId());
        }
        if (viewer.getRole() == UserRole.STUDENT) {
            Student student = studentRepository.findByUserId(viewer.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_FORBIDDEN));
            return jobRepository.findBySelectedStudentProfileId(student.getId());
        }
        throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
    }

    private Map<Long, Counterpart> findCounterparts(UserRole role, List<Job> jobs) {
        if (role == UserRole.STUDENT) {
            Map<Long, Owner> owners = ownerRepository.findAllById(jobs.stream()
                            .map(Job::getOwnerProfileId).distinct().toList()).stream()
                    .collect(Collectors.toMap(Owner::getId, Function.identity()));
            return jobs.stream().collect(Collectors.toMap(Job::getId, job -> {
                Owner owner = owners.get(job.getOwnerProfileId());
                if (owner == null) {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
                return new Counterpart(owner.getStoreName(), owner.getProfileImageUrl());
            }));
        }

        Map<Long, Student> students = studentRepository.findAllById(jobs.stream()
                        .map(Job::getSelectedStudentProfileId).distinct().toList()).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<String, User> users = userService.getUsersByIds(students.values().stream()
                .map(Student::getUserId).distinct().toList());
        return jobs.stream().collect(Collectors.toMap(Job::getId, job -> {
            Student student = students.get(job.getSelectedStudentProfileId());
            if (student == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            return new Counterpart(users.get(student.getUserId()).getName(), student.getProfileImageUrl());
        }));
    }

    /** 현재 사용자가 채팅방과 연결된 작업(Job)의 참여자인지 확인합니다. */
    private void requireParticipant(User viewer, Job job) {
        if (viewer.getRole() == UserRole.OWNER) {
            Owner owner = ownerRepository.findByUserId(viewer.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_FORBIDDEN));
            if (owner.getId().equals(job.getOwnerProfileId())) {
                return;
            }
        } else if (viewer.getRole() == UserRole.STUDENT) {
            Student student = studentRepository.findByUserId(viewer.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_FORBIDDEN));
            if (student.getId().equals(job.getSelectedStudentProfileId())) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.CHAT_FORBIDDEN);
    }

    private int compareRooms(ChatRoom left, ChatRoom right, Map<String, ChatMessage> latestByRoom) {
        ChatMessage leftMessage = latestByRoom.get(left.getId());
        ChatMessage rightMessage = latestByRoom.get(right.getId());
        if (leftMessage != null && rightMessage != null) {
            int byTime = rightMessage.getCreatedAt().compareTo(leftMessage.getCreatedAt());
            return byTime != 0 ? byTime : rightMessage.getId().compareTo(leftMessage.getId());
        }
        if (leftMessage != null) {
            return -1;
        }
        if (rightMessage != null) {
            return 1;
        }
        return Comparator.comparing(ChatRoom::getCreatedAt)
                .thenComparing(ChatRoom::getId)
                .reversed().compare(left, right);
    }

    private LastMessage toLastMessage(ChatMessage message) {
        String preview = switch (message.getType()) {
            case TEXT -> message.getContent();
            case IMAGE -> "사진";
            case FILE -> message.getAttachmentName();
        };
        return LastMessage.of(message.getType(), preview, message.getCreatedAt());
    }

    // 만료 시각은 createdAt과 같은 JVM 기본 시간대로 내린다
    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static class Counterpart {
        private final String name;
        private final String profileImageUrl;

        private Counterpart(String name, String profileImageUrl) {
            this.name = name;
            this.profileImageUrl = profileImageUrl;
        }
    }
}
