package com.gakkum.backend.application.job.facade;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobFacade {

    private final UserService userService;
    private final OwnerService ownerService;
    private final JobService jobService;

    @Transactional
    public void createJob(String username, JobCreateRequest request) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());

        jobService.createJob(request.toCommand(owner.getId()));
    }
}
