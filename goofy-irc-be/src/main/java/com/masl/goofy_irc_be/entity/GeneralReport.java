package com.masl.goofy_irc_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class GeneralReport {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, length = FieldSize.TITLE_LEN)
    private String title;

    @Column(nullable = false, length = FieldSize.NORMAL_TEXT_LEN)
    private String description;

    @Column(nullable = false, length = FieldSize.SHORT_TEXT_LEN)
    private String contact;

    @Column(length = FieldSize.HANDLE_LEN)
    private String optionalHandle;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant resolvedAt; // Also acts as boolean
}
