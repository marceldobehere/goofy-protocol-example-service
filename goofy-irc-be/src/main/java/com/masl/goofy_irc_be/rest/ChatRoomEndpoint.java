package com.masl.goofy_irc_be.rest;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.both.ChatRoomDto;
import com.masl.goofy_irc_be.entity.ChatRoom;
import com.masl.goofy_irc_be.entity.User;
import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.exception.client.room.*;
import com.masl.goofy_irc_be.properties.RoomProperties;
import com.masl.goofy_irc_be.repository.ChatRoomRepository;
import com.masl.goofy_irc_be.repository.UserRepository;
import com.masl.goofy_irc_be.service.WsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

// TODO: Write tests
@RestController
@RequestMapping("/api/chatroom")
@Tag(name = "Chat Rooms", description = "Endpoints relating to the actual Chat Rooms")
public class ChatRoomEndpoint {
    private static final Logger log = LoggerFactory.getLogger(ChatRoomEndpoint.class);

    private final UserRepository userRepository;
    private final RoomProperties roomProperties;
    private final ChatRoomRepository chatRoomRepository;
    private final WsService wsService;

    public ChatRoomEndpoint(UserRepository userRepository, RoomProperties roomProperties, ChatRoomRepository chatRoomRepository, WsService wsService) {
        this.userRepository = userRepository;
        this.roomProperties = roomProperties;
        this.chatRoomRepository = chatRoomRepository;
        this.wsService = wsService;
    }

    // Get all available Chat Rooms (list depends on user or outsider)
    @GetMapping("/list/available")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Lists all available rooms for the user")
    public List<ChatRoomDto> getAvailableRooms(@AuthenticationPrincipal GoofyAuthUser auth) {
        if (auth.getUser())
            return fromChatRooms(chatRoomRepository.findAll(), auth.getHandle(), auth.getAdmin());
        return fromChatRooms(chatRoomRepository.findAllByAllowGuestsIsTrue(), auth.getHandle(), false);
    }

    // Get all Rooms I have joined / own
    @GetMapping("/list/my")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Lists rooms joined/owned by the current user", description = "Returns rooms the user is a member of and rooms they own.")
    public List<ChatRoomDto> getMyRooms(@AuthenticationPrincipal GoofyAuthUser auth) {
        return fromChatRooms(chatRoomRepository.findAllByCreatedBy_Handle_OrMembersContaining(auth.getHandle(), auth.getHandle()), auth.getHandle(), auth.getAdmin());
    }

    // TODO: Get Room Creation Limit

    // Create Chat Room
    @PostMapping("/create")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Creates a chat room", description = "Creates a new chat room for the current user. <br>You can optionally set a password by setting the passwordHash property, ideally to a sha256 url encoded value of the password. <br>When others login they have to provide the same hash.")
    public ChatRoomDto createRoom(@Valid @RequestBody ChatRoomDto roomDto, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomCreationLimitReached, RoomInvalid, RoomAlreadyExists {
        // Room already exists?
        if (chatRoomRepository.existsByName(roomDto.getName()))
            throw new RoomAlreadyExists(roomDto.getName());

        // Limit reached?
        if (chatRoomRepository.countAllByCreatedBy_Handle(auth.getHandle()) >= roomProperties.getMaxRoomsPerUser())
            throw new RoomCreationLimitReached();

        // Get User
        User user = userRepository.findByHandle(auth.getHandle());

        // Validate Fields
        if (roomDto.getUserLimit() != null)
            if (roomDto.getUserLimit() < 2 || roomDto.getUserLimit() > roomProperties.getDefaultMaxUserLimit())
                throw new RoomInvalid("userLimit invalid");

        // Create Entity
        ChatRoom room = new ChatRoom();
        room.setName(roomDto.getName());
        room.setDescription(roomDto.getDescription());
        room.setUserLimit(roomDto.getUserLimit());
        room.setAllowGuests(roomDto.getAllowGuests());
        room.setAllowJoining(roomDto.getAllowJoining());
        room.setRoomPasswordHash(roomDto.getRoomPasswordHash());
        room.setMembers(new HashSet<>());
        room.setBannedUsers(new HashSet<>());
        room.setCreatedBy(user);
        room.setCreatedAt(Instant.now());
        room = chatRoomRepository.save(room);

        wsService.sendRoomListUpdate();
        log.info("User {} created new Room {}", auth.getHandle(), roomDto.getName());
        return fromChatRoom(room, auth.getHandle(), true);
    }

    // Delete Chat Room
    @DeleteMapping("/room/{roomName}/delete")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Deletes a chat room", description = "Deletes a chat room.")
    public void deleteRoom(@PathVariable String roomName, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomNotFound, RoomActionNotAllowed {
        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(roomName);
        if (room == null)
            throw new RoomNotFound(roomName);

        // Check Created By
        if (!auth.getHandle().equals(room.getCreatedBy().getHandle()))
            throw new RoomActionNotAllowed();

        chatRoomRepository.deleteByName(roomName);
        wsService.sendRoomListUpdate();
        log.info("User {} deleted room {}", auth.getHandle(), roomName);
    }

    // Get Chat Room details (should include members and if they are online + member limit + hide password hash)
    @GetMapping("/room/{roomName}")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Gets chat room details")
    public ChatRoomDto getRoomDetails(@PathVariable String roomName, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomNotFound {
        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(roomName);
        if (room == null)
            throw new RoomNotFound(roomName);

        // Check If entity can view the room
        if (!auth.getUser() && !room.getAllowGuests() && !room.getMembers().contains(auth.getHandle()))
            throw new RoomNotFound(roomName);

        return fromChatRoom(room, auth.getHandle(), auth.getAdmin());
    }

    // Update Chat Room details
    @PutMapping("/room/{roomName}")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Updates chat room details", description = "Updates settings for a chat room.")
    public ChatRoomDto updateRoom(@PathVariable String roomName, @Valid @RequestBody ChatRoomDto updateDto, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomInvalid, RoomNotFound, RoomActionNotAllowed {
        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(roomName);
        if (room == null)
            throw new RoomNotFound(roomName);

        // Check Created By
        if (!auth.getHandle().equals(room.getCreatedBy().getHandle()))
            throw new RoomActionNotAllowed();

        // Validate Fields
        if (updateDto.getUserLimit() != null)
            if (updateDto.getUserLimit() < 2 || updateDto.getUserLimit() > roomProperties.getDefaultMaxUserLimit())
                throw new RoomInvalid("userLimit invalid");

        // Create Entity
        room.setName(updateDto.getName());
        room.setDescription(updateDto.getDescription());
        room.setUserLimit(updateDto.getUserLimit());
        room.setAllowGuests(updateDto.getAllowGuests());
        room.setAllowJoining(updateDto.getAllowJoining());
        room.setRoomPasswordHash(updateDto.getRoomPasswordHash());
        room = chatRoomRepository.save(room);

        // Send Room Update
        wsService.sendRoomUpdate(room.getName(), room.getMembers(), auth.getHandle());

        return fromChatRoom(room, auth.getHandle(), true);
    }

    // Join Chat Room
    @PostMapping("/room/{roomName}/join")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Joins a chat room", description = "Joins the room as a registered user or guest (depending on room settings). Enforces bans, password, and limits.")
    public void joinRoom(@PathVariable String roomName, @RequestParam(name = "passwordHash", required = false) String passwordHash, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomNotFound, RoomMemberLimitReached, RoomMemberIsBanned, RoomActionNotAllowed, RoomJoiningNotAllowed, RoomInvalidPasswordHash {
        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(roomName);
        if (room == null)
            throw new RoomNotFound(roomName);

        // Check if entity can view the room
        if (!auth.getUser() && !room.getAllowGuests())
            throw new RoomNotFound(roomName);

        // Check allowJoin
        if (!room.getAllowJoining())
            throw new RoomJoiningNotAllowed();

        // Check if already a user
        if (room.getMembers().contains(auth.getHandle()) || room.getCreatedBy().getHandle().equals(auth.getHandle()))
            throw new RoomActionNotAllowed();

        // Check Banlist
        if (room.getBannedUsers().contains(auth.getHandle()))
            throw new RoomMemberIsBanned();

        // Check potential Password
        if (room.getRoomPasswordHash() != null) {
            if (passwordHash == null || passwordHash.isBlank())
                throw new RoomInvalidPasswordHash("Password is missing");
            if (!room.getRoomPasswordHash().equals(passwordHash))
                throw new RoomInvalidPasswordHash("Password is incorrect");
        }

        // Check Room Size against limit
        int limit = room.getUserLimit() == null ? roomProperties.getDefaultMaxUserLimit() : room.getUserLimit(); // Kind of volatile but whatever
        if (room.getMembers().size() >= limit)
            throw new RoomMemberLimitReached();

        // Join and Save
        room.getMembers().add(auth.getHandle());
        chatRoomRepository.save(room);

        // Send Room Update
        wsService.sendRoomUpdate(room.getName(), room.getMembers(), room.getCreatedBy().getHandle());
    }

    // Leave Chat Room (cant leave it yourself, need to delete it instead)
    @PostMapping("/room/{roomName}/leave")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Leaves a chat room")
    public void leaveRoom(@PathVariable String roomName, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomNotFound, RoomActionNotAllowed {
        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(roomName);
        if (room == null)
            throw new RoomNotFound(roomName);

        // Check if entity owns the room
        if (auth.getHandle().equals(room.getCreatedBy().getHandle()))
            throw new RoomActionNotAllowed();

        // Check If entity is part of the members
        if (!room.getMembers().contains(auth.getHandle()))
            throw new RoomActionNotAllowed();

        // Leave and Save
        room.getMembers().remove(auth.getHandle());
        chatRoomRepository.save(room);

        // Send Room Update
        wsService.sendRoomUpdate(room.getName(), room.getMembers(), room.getCreatedBy().getHandle(), auth.getHandle());
    }

    // Kick Member from Room (cant kick yourself)
    @PostMapping("/room/{roomName}/user/{username}/kick")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Kicks a member", description = "Kicks a member from the room.")
    public void kickMember(@PathVariable String roomName, @PathVariable String username, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomNotFound, RoomActionNotAllowed, RoomMemberNotFound {
        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(roomName);
        if (room == null)
            throw new RoomNotFound(roomName);

        // Check Created By
        if (!auth.getHandle().equals(room.getCreatedBy().getHandle()))
            throw new RoomActionNotAllowed();

        // Check if Member exists
        if (!room.getMembers().contains(username))
            throw new RoomMemberNotFound(username);

        // Kick and Save
        room.getMembers().remove(username);
        chatRoomRepository.save(room);

        // Send Room Update
        wsService.sendRoomUpdate(room.getName(), room.getMembers(), room.getCreatedBy().getHandle(), username);
    }

    // Ban Member from Room (cant ban yourself)
    @PostMapping("/room/{roomName}/user/{username}/ban")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Bans a member", description = "Bans a member from the room.")
    public void banMember(@PathVariable String roomName, @PathVariable String username, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomNotFound, RoomActionNotAllowed, RoomMemberAlreadyBanned {
        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(roomName);
        if (room == null)
            throw new RoomNotFound(roomName);

        // Check Created By
        if (!auth.getHandle().equals(room.getCreatedBy().getHandle()))
            throw new RoomActionNotAllowed();

        // Check if user bans themselves
        if (username.equals(room.getCreatedBy().getHandle()))
            throw new RoomActionNotAllowed();

        // Check Banlist
        if (room.getBannedUsers().contains(username))
            throw new RoomMemberAlreadyBanned(username);

        // Kick
        room.getMembers().remove(username);

        room.getBannedUsers().add(username);
        chatRoomRepository.save(room);

        // Send Room Update
        wsService.sendRoomUpdate(room.getName(), room.getMembers(), room.getCreatedBy().getHandle(), username);
    }

    // Unban Member from Room
    @PostMapping("/room/{roomName}/user/{username}/unban")
    @PreAuthorize("hasRole('ROLE_REGISTERED_USER')")
    @IrcEndpoint(summary = "Unbans a member", description = "Removes a ban from the room.")
    public void unbanMember(@PathVariable String roomName, @PathVariable String username, @AuthenticationPrincipal GoofyAuthUser auth) throws RoomNotFound, RoomActionNotAllowed, RoomMemberNotBanned {
        // Check if Room exists
        ChatRoom room = chatRoomRepository.findByName(roomName);
        if (room == null)
            throw new RoomNotFound(roomName);

        // Check Created By
        if (!auth.getHandle().equals(room.getCreatedBy().getHandle()))
            throw new RoomActionNotAllowed();

        // Check Banlist
        if (!room.getBannedUsers().contains(username))
            throw new RoomMemberNotBanned(username);

        room.getBannedUsers().remove(username);
        chatRoomRepository.save(room);

        // Send Room Update
        wsService.sendRoomUpdate(room.getName(), room.getMembers(), room.getCreatedBy().getHandle());
    }

    // Helper Function
    private List<ChatRoomDto> fromChatRooms(List<ChatRoom> rooms, String currHandle, boolean isAdmin) {
        return rooms.stream()
                .map((room) -> fromChatRoom(room, currHandle, isAdmin))
                .toList();
    }

    private ChatRoomDto fromChatRoom(ChatRoom room, String currHandle, boolean isAdmin) {
        return _fromChatRoom(room,
                room.getMembers().contains(currHandle) || isAdmin || currHandle.equals(room.getCreatedBy().getHandle()),
                isAdmin || currHandle.equals(room.getCreatedBy().getHandle()));
    }

    private ChatRoomDto _fromChatRoom(ChatRoom room, boolean showMembers, boolean showAllStuff) {
        // Get Members
        List<String> members = new ArrayList<>(room.getMembers());
        members.add(room.getCreatedBy().getHandle());

        // Get Member Stats
        List<ChatRoomDto.MemberStatusDto> memberStatus = new ArrayList<>();
        for (String member : members) {
            WsService.MemberStatus status = wsService.getStatus(member);
            memberStatus.add(new ChatRoomDto.MemberStatusDto(status.isOnline, status.isTyping));
        }

        // Create DTO
        ChatRoomDto roomDto = new ChatRoomDto();
        roomDto.setName(room.getName());
        roomDto.setDescription(room.getDescription());
        roomDto.setUserLimit(room.getUserLimit() == null ? roomProperties.getDefaultMaxUserLimit() : room.getUserLimit());
        roomDto.setAllowGuests(room.getAllowGuests());
        roomDto.setAllowJoining(room.getAllowJoining());
        roomDto.setCreatedByHandle(room.getCreatedBy().getHandle());
        roomDto.setCreatedAt(room.getCreatedAt());

        // Members
        roomDto.setMemberCount(members.size());
        if (showMembers) {
            roomDto.setMembers(members);
            roomDto.setMemberStatus(memberStatus);
        }

        // Banlist
        if (showAllStuff) {
            roomDto.setRoomPasswordHash(room.getRoomPasswordHash());
            roomDto.setBannedUsers(room.getBannedUsers().stream().toList());
        }

        return roomDto;
    }
}
