package com.masl.goofy_irc_be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

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

    // Puts the user in a read only mode temporarily, for example if the user is being reported or investigated
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean restricted;

    // Used in the Redirect Endpoints
    @Column
    private String customFrontendUrl;

//    @OneToMany(mappedBy="createdBy", orphanRemoval = true, cascade = CascadeType.REMOVE)
//    private Set<IdentityStorageEntry> identityStorageEntries;
//
//    @OneToMany(mappedBy="createdBy", orphanRemoval = true, cascade = CascadeType.REMOVE)
//    private Set<LoginStorageEntry> loginStorageEntries;
//
//    @OneToMany(mappedBy="createdBy", orphanRemoval = true, cascade = CascadeType.REMOVE)
//    private Set<ServiceEntry> serviceEntries;

    // TODO: On delete clear cache probably

    @Override
    public String toString() {
        return "User{" +
                "handle='" + handle + '\'' +
                ", admin=" + admin +
                ", restricted=" + restricted +
                '}';
    }
}
