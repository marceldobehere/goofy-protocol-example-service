package com.masl.goofy_irc_be.rest;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.fis.table.ServiceTableQueryResultDto;
import com.masl.goofy_irc_be.dto.fis.table.TableBasicQueryDto;
import com.masl.goofy_irc_be.dto.fis.table.TableSelectDto;
import com.masl.goofy_irc_be.dto.fis.table.TWherePart;
import com.masl.goofy_irc_be.dto.ws.WsNewDm;
import com.masl.goofy_irc_be.dto.ws.WsNewFriendReq;
import com.masl.goofy_irc_be.entity.ChatRoom;
import com.masl.goofy_irc_be.entity.User;
import com.masl.goofy_irc_be.exception.base.swagger.IrcEndpoint;
import com.masl.goofy_irc_be.exception.client.priv.*;
import com.masl.goofy_irc_be.exception.server.FisRequestError;
import com.masl.goofy_irc_be.exception.server.FisRequestValidationError;
import com.masl.goofy_irc_be.repository.CachedKeyHandleRepository;
import com.masl.goofy_irc_be.repository.ChatRoomRepository;
import com.masl.goofy_irc_be.repository.UserRepository;
import com.masl.goofy_irc_be.service.FisReqService;
import com.masl.goofy_irc_be.service.WsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// TODO: Write tests
@RestController
@RequestMapping("/api/priv")
@Tag(name = "DMs & Friends", description = "Endpoints relating to DMs and Friend Requests")
public class FriendEndpoint {
    private static final Logger log = LoggerFactory.getLogger(FriendEndpoint.class);

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final WsService wsService;
    private final FisReqService fisReqService;
    private final CachedKeyHandleRepository cachedKeyHandleRepository;

    public FriendEndpoint(UserRepository userRepository, ChatRoomRepository chatRoomRepository, WsService wsService, FisReqService fisReqService, CachedKeyHandleRepository cachedKeyHandleRepository) {
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.wsService = wsService;
        this.fisReqService = fisReqService;
        this.cachedKeyHandleRepository = cachedKeyHandleRepository;
    }

    // Send Friend Request
    @PostMapping("/friend-request/{handle}")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Send a friend request.")
    public void sendFriendRequest(@PathVariable String handle, @AuthenticationPrincipal GoofyAuthUser auth) throws UserNotFound, FriendRequestsNotAllowed, UserFisLookupFailed, FisRequestError, AlreadyFriends, FriendRequestAlreadySent, FisRequestValidationError {
        log.debug("sendFriendRequest called for handle: {} by auth: {}", handle, auth.getHandle());
        // Find User
        User user = userRepository.findByHandle(handle);
        if (user == null)
            throw new UserNotFound(handle);

        // Check if Friend Requests Denied
        if (user.getFriendRequestSetting() == User.FriendRequestSetting.DENY)
            throw new FriendRequestsNotAllowed();

        // Check if already friends
        if (isFriends(auth.getHandle(), user))
            throw new AlreadyFriends();

        // Check if already sent friend req
        if (hasSentFriendRequest(auth.getHandle(), user))
            throw new FriendRequestAlreadySent();

        // Check Friend Request Settings
        if (user.getFriendRequestSetting() == User.FriendRequestSetting.ALLOW_SAME_SERVER)
            if (userRepository.findByHandle(auth.getHandle()) == null)
                throw new FriendRequestsNotAllowed();
        if (user.getFriendRequestSetting() == User.FriendRequestSetting.ALLOW_ANY_ROOM_MEMBER) {
            // Get Rooms that User is in
            Set<String> userRooms = chatRoomRepository.findAllByCreatedBy_Handle_OrMembersContaining(user.getHandle(), user.getCustomFrontendUrl())
                    .stream().map(ChatRoom::getName).collect(Collectors.toSet());

            // Check rooms of the external person and see if there is any overlap
            boolean shareRooms = chatRoomRepository.findAllByCreatedBy_Handle_OrMembersContaining(auth.getHandle(), auth.getHandle())
                    .stream().anyMatch(room -> userRooms.contains(room.getName()));

            // TODO: Check if the user has the handle in the sent friend request table!!!

            if (!shareRooms)
                throw new FriendRequestsNotAllowed();
        }

        // Insert Friend Request
        insertFisTable(Map.of("handle", auth.getHandle(), "requested_at", Instant.now().toEpochMilli()), user, user.getFriendRequestTablePath());

        // Notify User
        wsService.trySendMessage(user.getHandle(), new WsNewFriendReq());
        wsService.trySendMessage(auth.getHandle(), new WsNewFriendReq());
    }

    // Am Friends?
    @GetMapping("/is-friend/{handle}")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Checks if you are friends with the person.")
    public boolean isFriends(@PathVariable String handle, @AuthenticationPrincipal GoofyAuthUser auth) throws UserNotFound, UserFisLookupFailed, FisRequestError, FisRequestValidationError {
        // Find User
        User user = userRepository.findByHandle(handle);
        if (user == null)
            throw new UserNotFound(handle);

        return isFriends(auth.getHandle(), user);
    }

    // Send DM
    @PostMapping("/dm/{handle}")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Sends a friend a DM")
    public void sendDm(@PathVariable String handle, @RequestBody String msgObj, @AuthenticationPrincipal GoofyAuthUser auth) throws NotFriends, UserNotFound, UserFisLookupFailed, FisRequestError, FisRequestValidationError {
        log.debug("sendDm called for handle: {} by auth: {}", handle, auth.getHandle());
        // Find User
        User user = userRepository.findByHandle(handle);
        if (user == null)
            throw new UserNotFound(handle);

        // Check if not friends
        if (!isFriends(auth.getHandle(), user))
            throw new NotFriends(handle);

        // Insert DM
        insertFisTable(Map.of("msg_json", msgObj, "uuid", UUID.randomUUID().toString(), "timestamp", Instant.now().toEpochMilli(), "sender_handle", auth.getHandle()), user, user.getReceivedDmsTablePath());

        // Notify User
        wsService.trySendMessage(user.getHandle(), new WsNewDm(auth.getHandle()));
        wsService.trySendMessage(auth.getHandle(), new WsNewDm(user.getHandle()));
    }

    // Update Friends List (e.g. bc accepted)
    @PostMapping("/update-friends/{handle}")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Notifies a Friend / Yourself to update the Friend List")
    public void updateFriends(@PathVariable String handle, @AuthenticationPrincipal GoofyAuthUser auth) throws NotFriends, UserNotFound, UserFisLookupFailed, FisRequestError, FisRequestValidationError {
        // Find User
        User user = userRepository.findByHandle(handle);
        if (user == null)
            throw new UserNotFound(handle);

        // Check if not friends
        if (!auth.getHandle().equals(user.getHandle()) && !isFriends(auth.getHandle(), user))
            throw new NotFriends(handle);

        // Notify User
        wsService.trySendMessage(user.getHandle(), new WsNewFriendReq());
        if (!auth.getHandle().equals(user.getHandle()))
            wsService.trySendMessage(auth.getHandle(), new WsNewFriendReq());
    }

    // Unfriend
    @PostMapping("/unfriend/{handle}")
    @PreAuthorize("hasRole('ROLE_OUTSIDE_ENTITY')")
    @IrcEndpoint(summary = "Unfriends a handle")
    public void unfriend(@PathVariable String handle, @AuthenticationPrincipal GoofyAuthUser auth) throws NotFriends, UserNotFound, UserFisLookupFailed, FisRequestError, FisRequestValidationError {
        log.debug("unfriend called for handle: {} by auth: {}", handle, auth.getHandle());
        // Find User
        User user = userRepository.findByHandle(handle);
        if (user == null)
            throw new UserNotFound(handle);

        // Check if not friends
        if (!isFriends(auth.getHandle(), user))
            throw new NotFriends(handle);

        // TODO: Implement custom WS message and i guess extra table?

        // Notify User
        wsService.trySendMessage(user.getHandle(), new WsNewFriendReq());
        wsService.trySendMessage(auth.getHandle(), new WsNewFriendReq());
    }

    private boolean hasSentFriendRequest(String extHandle, User user) throws UserFisLookupFailed, FisRequestError, FisRequestValidationError {
        var where = TWherePart.eq(TWherePart.col("handle"), TWherePart.valStr(extHandle));
        var select = new TableSelectDto(new String[] {"handle"}, TableBasicQueryDto.builder().where(where).build());
        var friendListQuery = queryFisTable(select, user, user.getFriendRequestTablePath());
        return friendListQuery.getRows().length > 0;
    }

    private boolean isFriends(String extHandle, User user) throws UserFisLookupFailed, FisRequestError, FisRequestValidationError {
        var where = TWherePart.eq(TWherePart.col("handle"), TWherePart.valStr(extHandle));
        var select = new TableSelectDto(new String[] {"handle"}, TableBasicQueryDto.builder().where(where).build());
        var friendListQuery = queryFisTable(select, user, user.getFriendListTablePath());
        return friendListQuery.getRows().length > 0;
    }

    private ServiceTableQueryResultDto queryFisTable(TableSelectDto selectDto, User user, String tablePath) throws UserFisLookupFailed, FisRequestError, FisRequestValidationError {
        if (selectDto == null || tablePath == null || tablePath.isBlank())
            throw new UserFisLookupFailed(user.getHandle());

        String[] parts = tablePath.split(Pattern.quote("@"));
        if (parts.length != 3)
            throw new UserFisLookupFailed(user.getHandle());

        var cachedEntry = cachedKeyHandleRepository.findByHandle(user.getHandle());
        if (cachedEntry == null || cachedEntry.getHandleDomain() == null || cachedEntry.getHandleDomain().isBlank())
            throw new UserFisLookupFailed(user.getHandle());

        String url = "/fis-api/service-table/" + parts[0] + "/" + parts[1] + "/entry/" + parts[2] + "/query";
        return fisReqService.performSignedRequest(ServiceTableQueryResultDto.class, HttpMethod.POST, cachedEntry.getHandleDomain(), url, selectDto);
    }

    private void insertFisTable(Map<String, Object> insertObj, User user, String tablePath) throws UserFisLookupFailed, FisRequestError, FisRequestValidationError {
        if (insertObj == null || tablePath == null || tablePath.isBlank())
            throw new UserFisLookupFailed(user.getHandle());

        String[] parts = tablePath.split(Pattern.quote("@"));
        if (parts.length != 3)
            throw new UserFisLookupFailed(user.getHandle());

        var cachedEntry = cachedKeyHandleRepository.findByHandle(user.getHandle());
        if (cachedEntry == null || cachedEntry.getHandleDomain() == null || cachedEntry.getHandleDomain().isBlank())
            throw new UserFisLookupFailed(user.getHandle());

        String url = "/fis-api/service-table/" + parts[0] + "/" + parts[1] + "/entry/" + parts[2] + "/rows";
        fisReqService.performSignedRequest(void.class, HttpMethod.POST, cachedEntry.getHandleDomain(), url, insertObj);
    }
}
