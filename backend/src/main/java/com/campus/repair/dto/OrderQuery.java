package com.campus.repair.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@lombok.Getter
@lombok.Setter
public class OrderQuery {
    @Min(1) private long page = 1;
    @Min(1) @Max(100) private long size = 12;
    private String status;
    private String phase;
    private Boolean overdue;
    @Positive private Long typeId;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate from;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate to;
}
