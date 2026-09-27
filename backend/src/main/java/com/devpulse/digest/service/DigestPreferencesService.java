package com.devpulse.digest.service;

import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.digest.api.DigestPreferences;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.persistence.DpUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigestPreferencesService {

    private final DpUserRepository userRepository;
    private final WeeklyDigestSender sender;

    public DigestPreferencesService(DpUserRepository userRepository, WeeklyDigestSender sender) {
        this.userRepository = userRepository;
        this.sender = sender;
    }

    @Transactional(readOnly = true)
    public DigestPreferences get(UserPrincipal principal) {
        return toPreferences(requireUser(principal));
    }

    @Transactional
    public DigestPreferences update(UserPrincipal principal, boolean enabled) {
        DpUser user = requireUser(principal);
        user.setWeeklyDigestEnabled(enabled);
        return toPreferences(userRepository.save(user));
    }

    private DpUser requireUser(UserPrincipal principal) {
        return userRepository.findById(principal.id()).orElseThrow(() -> new NotFoundException("User not found."));
    }

    private DigestPreferences toPreferences(DpUser user) {
        return new DigestPreferences(user.isWeeklyDigestEnabled(), user.getWeeklyDigestSentAt(), sender.canSend());
    }
}
