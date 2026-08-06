package com.prwatech.skillama.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One row per login (tied to the tokenVersion minted for it). Billable active time for a
 * session is (lastHeartbeatAt - startedAt), not (endedAt - startedAt) — lastHeartbeatAt only
 * advances while the tab was actually focused and heartbeating, so a browser closed without
 * ever calling /logout doesn't inflate billed time past the last moment it was genuinely open.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_sessions")
public class UserSession {
    @Id
    private String id;

    @Indexed
    private String userId;

    /** Ties this row to the JWT "tv" claim minted for this login. */
    private Integer tokenVersion;

    private LocalDateTime startedAt;
    private LocalDateTime lastHeartbeatAt;

    /** Null while the session is the account's current active one. */
    private LocalDateTime endedAt;

    /** LOGOUT | REPLACED (kicked out by a login elsewhere) — null while still open. */
    private String endReason;
}
