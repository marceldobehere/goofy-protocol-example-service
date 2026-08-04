package com.masl.goofy_irc_be.repository;

import com.masl.goofy_irc_be.entity.ChatRoom;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Transactional
@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
    boolean existsByName(String name);
    void deleteByName(String name);
    ChatRoom findByName(String name);

    int countAllByCreatedBy_Handle(String createdByHandle);

    List<ChatRoom> findAllByCreatedBy_Handle_OrMembersContaining(String createdByHandle, String member);
    List<ChatRoom> findAllByAllowGuestsIsTrue();
}
