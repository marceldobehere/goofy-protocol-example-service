package com.masl.goofy_irc_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.Set;

@Entity
@Table(name = "irc_user")
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class User {
    @Id
    @Column(nullable = false, length = FieldSize.HANDLE_LEN)
    private String handle;

    @Column(nullable = false, length = FieldSize.PUB_KEY_LEN)
    private String pubSplitKey;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean admin;

    // TODO: Probably add some form of restricted flag? Blocks sending messages and or creating rooms

    // Used in the Redirect Endpoints
    @Column
    private String customFrontendUrl;

    // TODO: Add Property "usedRegisterCode", that specifies if the user used a register code to register.

    // TODO: Add regular checks if the attached domain of a handle is valid (Only for Users that haven't registered with a register code)
    // If the domain is NOT valid, start a "countdown"
    // after 3 days of it not being valid, respond to signed requests with a Exception/Error "InvalidDomain" or "DomainLookupFailed"
    // -> maybe allow data export
    // after 15-30 days just wipe the data
    // -> avoid data loss

    @Column(length = FieldSize.NORMAL_TEXT_LEN)
    private String friendRequestTablePath; // identityHandle@serviceUuid@tableUuid

    @Column(length = FieldSize.NORMAL_TEXT_LEN)
    private String receivedDmsTablePath; // identityHandle@serviceUuid}@tableUuid

    @OneToMany(mappedBy="createdBy", orphanRemoval = true, cascade = CascadeType.REMOVE)
    private Set<ChatRoom> createdChatRooms;

    // TODO: On delete clear cache probably

    @Override
    public String toString() {
        return "User{" +
                "handle='" + handle + '\'' +
                ", admin=" + admin +
                '}';
    }
}
