package com.tourismgov.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceDTO {
    private Long complianceId;
    private String referenceNumber;
    private Long entityId;
    private String type;   // ✅ MUST BE STRING, NOT AN ENUM OBJECT
    private String result; // ✅ MUST BE STRING, NOT AN ENUM OBJECT
    private LocalDateTime date;
    private String notes;
}