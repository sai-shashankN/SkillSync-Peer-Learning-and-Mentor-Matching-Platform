package com.skillsync.mentor.service;

import com.skillsync.common.exception.BadRequestException;
import com.skillsync.common.exception.ConflictException;
import com.skillsync.mentor.dto.WaitlistResponse;
import com.skillsync.mentor.mapper.MentorMapper;
import com.skillsync.mentor.model.Mentor;
import com.skillsync.mentor.model.Waitlist;
import com.skillsync.mentor.model.enums.MentorStatus;
import com.skillsync.mentor.model.enums.WaitlistStatus;
import com.skillsync.mentor.repository.WaitlistRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final MentorService mentorService;
    private final WaitlistRepository waitlistRepository;
    private final MentorMapper mentorMapper;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public WaitlistResponse joinWaitlist(Long mentorId, Long learnerId) {
        Mentor mentor = mentorService.getRequiredMentor(mentorId);
        if (mentor.getStatus() != MentorStatus.APPROVED) {
            throw new BadRequestException("Waitlist is only available for approved mentors");
        }
        if (waitlistRepository.existsByMentorIdAndLearnerId(mentorId, learnerId)) {
            throw new ConflictException("Learner is already on the waitlist for this mentor");
        }

        Waitlist waitlist = Waitlist.builder()
                .mentorId(mentorId)
                .learnerId(learnerId)
                .status(WaitlistStatus.ACTIVE)
                .build();
        return mentorMapper.toWaitlistResponse(waitlistRepository.save(waitlist));
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> getWaitlistEntries(Long mentorId) {
        mentorService.getRequiredMentor(mentorId);
        return waitlistRepository.findByMentorIdAndStatus(mentorId, WaitlistStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(Waitlist::getCreatedAt))
                .map(mentorMapper::toWaitlistResponse)
                .toList();
    }

    @Transactional
    public void notifyNextWaitlistedLearner(Long mentorId) {
        List<Waitlist> entries = waitlistRepository.findByMentorIdAndStatus(mentorId, WaitlistStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(Waitlist::getCreatedAt))
                .toList();
        if (entries.isEmpty()) {
            return;
        }

        Waitlist waitlist = entries.get(0);
        waitlist.setStatus(WaitlistStatus.NOTIFIED);
        waitlist.setNotifiedAt(Instant.now());
        waitlist.setNotificationAttempts(waitlist.getNotificationAttempts() + 1);
        waitlistRepository.save(waitlist);
        eventPublisherService.publishWaitlistSlotOpen(mentorId, waitlist.getLearnerId());
    }

    @Transactional
    @Scheduled(fixedRate = 3600000)
    public void expireOldEntries() {
        Instant now = Instant.now();
        List<Waitlist> expiredEntries = waitlistRepository.findAll().stream()
                .filter(entry -> entry.getStatus() == WaitlistStatus.ACTIVE)
                .filter(entry -> entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(now))
                .toList();

        expiredEntries.forEach(entry -> entry.setStatus(WaitlistStatus.EXPIRED));
        if (!expiredEntries.isEmpty()) {
            waitlistRepository.saveAll(expiredEntries);
        }
    }
}
