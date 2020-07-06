package de.mczul.configuration.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Slf4j
@Validated
@Service
@RequiredArgsConstructor
public class ScheduledConfigService {
    private final ScheduledConfigRepository entryRepository;

    @Transactional
    public ScheduledConfigEntry set(ScheduledConfigEntry entry) {
        return entryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Optional<ScheduledConfigEntry> get(String key) {
        return entryRepository.findCurrentByKey(key);
    }

    @Transactional
    @Scheduled(fixedRate = 5000)
    void cleanup() {
        List<ScheduledConfigEntry> obsolete = entryRepository.findOutdated();
        LOG.info("Found {} entries that are obsolete", obsolete.size());
    }
}
