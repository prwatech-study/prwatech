package com.prwatech.skillama.model.outreach;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outreach_daily_rollups")
@CompoundIndex(name = "date_wave", def = "{'date': 1, 'wave': 1}", unique = true)
public class OutreachDailyRollup {

    @Id
    private String id;

    private LocalDate date;
    private int wave;

    @Builder.Default
    private int attempted = 0;
    @Builder.Default
    private int submitted = 0;
    @Builder.Default
    private int failed = 0;
    @Builder.Default
    private int uniqueOpens = 0;
    @Builder.Default
    private int uniqueClicks = 0;
    @Builder.Default
    private int registrations = 0;
    @Builder.Default
    private int bonusesGranted = 0;
    @Builder.Default
    private int unsubscribes = 0;
}