package com.tourismgov.report.client;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tourismgov.report.dto.SiteDTO;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SiteClientFallback implements SiteClient {

    @Override
    public List<SiteDTO> getAllSites() {
        log.warn("FALLBACK: SITE-SERVICE unavailable. Returning empty site list.");
        return Collections.emptyList();
    }
}
