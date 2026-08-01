package com.masl.goofy_irc_be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class ServerKeypair {
    @Id
    @Column(nullable = false, length = FieldSize.HANDLE_LEN)
    private String handle;

    @Column(nullable = false, length = FieldSize.PUB_KEY_LEN)
    private String pubSplitKey;

    @Column(nullable = false, length = FieldSize.PRIV_KEY_LEN)
    private String privSplitKey;
}
