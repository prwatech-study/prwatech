package com.prwatech.skillama.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class OutreachDailyRowDTO {
    private LocalDate date;
    private int wave;
    private int attempted;
    private int submitted;
    private int failed;
    private int uniqueOpens;
    private int uniqueClicks;
    private int registrations;
    private int bonusesGranted;
    private int unsubscribes;
}