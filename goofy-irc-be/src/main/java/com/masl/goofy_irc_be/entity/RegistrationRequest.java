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
public class RegistrationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, length = FieldSize.NORMAL_TEXT_LEN)
    private String messsage;

    @Column(nullable = false, length = FieldSize.SHORT_TEXT_LEN)
    private String generalContact;

    @Column(length = FieldSize.SHORT_TEXT_LEN)
    private String optEmail;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = FieldSize.HANDLE_LEN)
    private String createdByHandle;

    @Column
    private Instant resolvedAt; // Also acts as boolean
}
