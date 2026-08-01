package com.masl.goofy_irc_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class RegistrationCode {
    @Id
    @Column(nullable = false, length = FieldSize.GENERIC_CODE_LEN)
    private String code;

    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean admin;

    @ManyToOne
    @JoinColumn
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @ManyToOne
    @JoinColumn
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User usedBy;

    @Column
    private Instant usedAt;
}
