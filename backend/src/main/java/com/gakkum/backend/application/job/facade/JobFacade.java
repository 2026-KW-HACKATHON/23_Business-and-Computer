package com.gakkum.backend.application.job.facade;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobFacade {

    private final UserService userService;
    private final OwnerService ownerService;
    private final JobService jobService;
    private final SpecialtyCategoryService specialtyCategoryService;
    private final StudentService studentService;

    @Transactional
    public void createJob(String username, JobCreateRequest request) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());

        jobService.createJob(request.toCommand(owner.getId()));
    }

    @Transactional(readOnly = true)
    public OpenJobListResult getOpenJobs(String username) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        List<OpenJobData> jobs = jobService.getOpenJobs(GetOpenJobsCommand.of(owner.getId()));

        // 보낸 의뢰가 없으면 그냥 반환
        if (jobs.isEmpty()) {
            return OpenJobListResult.of(List.of());
        }

        // 특기 목록 조회
        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return OpenJobListResult.of(jobs.stream()
                .map(job -> OpenJobResult.of(job, groupSpecialties(job.getSpecialtyIds(), specialtiesById)))
                .toList());
    }

    @Transactional(readOnly = true)
    public MatchedJobListResult getMatchedJobs(String username) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        List<MatchedJobData> jobs = jobService.getMatchedJobs(GetMatchedJobsCommand.of(owner.getId()));

        if (jobs.isEmpty()) {
            return MatchedJobListResult.of(List.of());
        }

        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(jobs.stream()
                .map(job -> job.getJob().getSelectedStudentProfileId())
                .distinct()
                .toList());
        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return MatchedJobListResult.of(jobs.stream()
                .map(job -> MatchedJobResult.of(
                        job,
                        studentsById.get(job.getJob().getSelectedStudentProfileId()),
                        groupSpecialties(job.getSpecialtyIds(), specialtiesById)))
                .toList());
    }

    @Transactional(readOnly = true)
    public ClosedJobListResult getClosedJobs(String username) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        List<ClosedJobData> jobs = jobService.getClosedJobs(GetClosedJobsCommand.of(owner.getId()));

        if (jobs.isEmpty()) {
            return ClosedJobListResult.of(List.of());
        }

        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(jobs.stream()
                .map(job -> job.getJob().getSelectedStudentProfileId())
                .distinct()
                .toList());
        Map<String, User> workersById = userService.getUsersByIds(studentsById.values().stream()
                .map(Student::getUserId)
                .distinct()
                .toList());
        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return ClosedJobListResult.of(jobs.stream()
                .map(job -> {
                    Student student = studentsById.get(job.getJob().getSelectedStudentProfileId());
                    return ClosedJobResult.of(
                            job,
                            student,
                            workersById.get(student.getUserId()),
                            groupSpecialties(job.getSpecialtyIds(), specialtiesById));
                })
                .toList());
    }

    private List<SpecialtyCategoryResult> groupSpecialties(
            List<Long> specialtyIds, Map<Long, SpecialtyDetail> specialtiesById) {
        Map<Long, List<SpecialtyDetail>> byCategory = specialtyIds.stream()
                .map(id -> {
                    SpecialtyDetail detail = specialtiesById.get(id);
                    if (detail == null) {
                        throw new IllegalStateException("Specialty not found: " + id);
                    }
                    return detail;
                })
                .sorted(Comparator.comparing(SpecialtyDetail::getId))
                .collect(Collectors.groupingBy(
                        SpecialtyDetail::getCategoryId,
                        TreeMap::new,
                        Collectors.toList()));

        return byCategory.entrySet().stream()
                .map(entry -> SpecialtyCategoryResult.of(
                        entry.getKey(),
                        entry.getValue().get(0).getCategoryName(),
                        entry.getValue().stream()
                                .map(detail -> SpecialtyResult.of(detail.getId(), detail.getName()))
                                .toList()))
                .toList();
    }
}
