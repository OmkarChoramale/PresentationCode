package com.tourismgov.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourismgov.report.client.*;
import com.tourismgov.report.dto.*;
import com.tourismgov.report.enums.ReportScope;
import com.tourismgov.report.enums.Role;
import com.tourismgov.report.model.Report;
import com.tourismgov.report.repository.ReportRepository;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock
    private ReportRepository reportRepo;

    @Mock
    private UserClient userClient;

    @Mock
    private SiteClient siteClient;

    @Mock
    private EventClient eventClient;

    @Mock
    private ProgramClient programClient;

    @Mock
    private ComplianceClient complianceClient;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReportServiceImpl reportService;

    private ReportRequestDTO requestDTO;
    private UserDTO mockRequester;
    private Report mockReport;

    @BeforeEach
    void setUp() {
        requestDTO = new ReportRequestDTO();
        requestDTO.setRequesterId(1L);
        requestDTO.setScope(ReportScope.SITE);

        mockRequester = new UserDTO();
        mockRequester.setUserId(1L);
        mockRequester.setName("Omkar Admin");
        mockRequester.setRole(Role.ADMIN);

        mockReport = Report.builder()
                .reportId(100L)
                .scope(ReportScope.SITE)
                .metrics("--- OFFICIAL SITE DATA LOG ---")
                .generatedByUserId(1L)
                .generatedDate(LocalDateTime.now())
                .build();
    }

    @Test
    void testGenerateReport_Success() {
        // Stub User Client
        when(userClient.getUserById(1L)).thenReturn(mockRequester);
        // Stub Site Client (Since scope is SITE)
        when(siteClient.getAllSites()).thenReturn(Collections.emptyList());
        // Stub Repository save
        when(reportRepo.save(any(Report.class))).thenReturn(mockReport);

        // Call the service method
        ReportSummaryDTO result = reportService.generateReport(requestDTO);

        assertNotNull(result);
        assertEquals(100L, result.getReportId());
        assertEquals("Omkar Admin", result.getGeneratedByName());
        assertEquals("ADMIN", result.getGeneratedByRole());

        // Verify dependencies
        verify(userClient, times(1)).getUserById(1L);
        verify(siteClient, times(1)).getAllSites();
        verify(reportRepo, times(1)).save(any(Report.class));
    }

    @Test
    void testGenerateReport_DeniedForTourist() {
        mockRequester.setRole(Role.TOURIST);
        when(userClient.getUserById(1L)).thenReturn(mockRequester);

        assertThrows(ResponseStatusException.class, () -> {
            reportService.generateReport(requestDTO);
        });

        verify(reportRepo, never()).save(any(Report.class));
    }

    @Test
    void testDownloadReport_Success() {
        when(reportRepo.findById(100L)).thenReturn(Optional.of(mockReport));

        byte[] metrics = reportService.downloadReport(100L);

        assertNotNull(metrics);
        assertTrue(metrics.length > 0);
        verify(reportRepo, times(1)).findById(100L);
    }

    @Test
    void testDownloadReport_NotFound() {
        when(reportRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> {
            reportService.downloadReport(999L);
        });
    }
}
