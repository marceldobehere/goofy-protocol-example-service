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
import java.util.Set;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class ChatRoom {
    @Id
    @Column(nullable = false, length = FieldSize.TITLE_LEN)
    private String name;

    @Column(nullable = false, length = FieldSize.NORMAL_TEXT_LEN)
    private String description;

    // Optional limit of users
    @Column
    private Integer userLimit;

    // Allow unregistered users to join the room?
    @Column(nullable = false)
    @ColumnDefault("false")
    private Boolean allowGuests;

    // Is joining allowed at all or is the room restricted?
    @Column(nullable = false)
    @ColumnDefault("true")
    private Boolean allowJoining;

    // If null, the room is public and can be joined by anyone
    @Column(length = FieldSize.SHA256_LEN)
    private String roomPasswordHash;

    // List of members
    @ElementCollection
    private Set<String> members;

    // List of users who are banned from the room
    @ElementCollection
    private Set<String> bannedUsers;

    // Creator of the room, also has ability to manage the settings
    @ManyToOne
    @JoinColumn
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User createdBy;

    @Column(nullable = false)
    private Instant createdAt;
}
