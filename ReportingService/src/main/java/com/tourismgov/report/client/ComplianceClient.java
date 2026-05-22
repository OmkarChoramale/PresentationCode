package com.tourismgov.report.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import com.tourismgov.report.dto.ComplianceDTO;
import java.util.List;

@FeignClient(name = "COMPLIANCE-SERVICE")
public interface ComplianceClient {
    
    // ✅ Restored path to root URL and matching List return type
    @GetMapping("/tourismgov/v1/compliance/records")
    List<ComplianceDTO> getAllComplianceRecords();
}