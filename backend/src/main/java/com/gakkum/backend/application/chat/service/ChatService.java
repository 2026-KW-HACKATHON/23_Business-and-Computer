package com.gakkum.backend.application.chat.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.LastMessage;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.Room;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.MarkReadCommand;
import com.gakkum.backend.domain.chat.repository.ChatMessageRepository;
import com.gakkum.backend.domain.chat.repository.ChatRoomRepository;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.repository.JobRepository;
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

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserService userService;
    private final OwnerRepository ownerRepository;
    private final StudentRepository studentRepository;
    private final JobRepository jobRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

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
                chatRoomRepository.findByJobIdIn(jobs.stream().map(Job::getId).toList()));
        if (rooms.isEmpty()) {
            return ChatRoomListResponse.of(List.of());
        }

        // 상대방, 최근 메시지, 안 읽은 개수 조회
        Map<Long, Counterpart> counterparts = findCounterparts(viewer.getRole(), rooms.stream()
                .map(room -> jobsById.get(room.getJobId())).toList());
        List<String> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<String, ChatMessage> latestByRoom = chatMessageRepository.findLatestByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(ChatMessage::getRoomId, Function.identity()));
        Map<String, Long> unreadByRoom = chatMessageRepository.countUnreadByRoomIds(
                        roomIds, viewer.getId(), viewer.getRole() == UserRole.OWNER).stream()
                .collect(Collectors.toMap(ChatMessageRepository.UnreadCount::getRoomId,
                        ChatMessageRepository.UnreadCount::getUnreadCount));

        // 정렬
        rooms.sort((left, right) -> compareRooms(left, right, latestByRoom));

        // 응답 구성
        List<Room> items = rooms.stream().map(room -> {
            Job job = jobsById.get(room.getJobId());
            Counterpart counterpart = counterparts.get(job.getId());
            ChatMessage latest = latestByRoom.get(room.getId());
            return Room.of(room.getId(), job.getId(), job.getTitle(), counterpart.name,
                    counterpart.profileImageUrl, latest == null ? null : toLastMessage(latest),
                    unreadByRoom.getOrDefault(room.getId(), 0L));
        }).toList();
        return ChatRoomListResponse.of(items);
    }

    @Transactional
    public void markRead(MarkReadCommand command) {
        User viewer = userService.getActiveUser(command.getUsername());
        ChatRoom room = chatRoomRepository.findLockedById(command.getRoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        Job job = jobRepository.findById(room.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        requireParticipant(viewer, job);

        ChatMessage message = chatMessageRepository.findById(command.getLastReadMessageId())
                .filter(found -> command.getRoomId().equals(found.getRoomId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        room.markRead(viewer.getRole(), message.getId());
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

    private static class Counterpart {
        private final String name;
        private final String profileImageUrl;

        private Counterpart(String name, String profileImageUrl) {
            this.name = name;
            this.profileImageUrl = profileImageUrl;
        }
    }
}
