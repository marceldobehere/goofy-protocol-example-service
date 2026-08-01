package com.masl.goofy_irc_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class CachedKeyHandleEntry {
    @Id
    @Column(nullable = false, length = FieldSize.PUB_KEY_LEN)
    private String pubSplitKey;

    @Column(nullable = false, length = FieldSize.HANDLE_LEN)
    private String handle;

    @Column
    private String handleDomain;

    @Column(nullable = false)
    private Instant createdAt;
}
