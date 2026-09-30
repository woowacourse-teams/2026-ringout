package com.ringout.api.room.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.room.controller.docs.RoomControllerApi;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.request.RoomKickRequest;
import com.ringout.api.room.dto.request.RoomUpdateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.dto.response.RoomDetailResponse;
import com.ringout.api.room.dto.response.RoomListResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import com.ringout.api.room.service.RoomService;
import com.ringout.api.room.status.RoomSuccessStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms")
public class RoomController implements RoomControllerApi {

    private final RoomService roomService;

    @Override
    @GetMapping
    public ResponseEntity<CustomResponse<RoomListResponse>> getRooms(
        @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        Long userId = customUserDetails == null ? null : customUserDetails.getUserId();
        RoomListResponse response = roomService.getRooms(userId);

        return ResponseEntity.status(RoomSuccessStatus.ROOM_LIST_FOUND.getHttpStatus())
            .body(CustomResponse.onSuccess(RoomSuccessStatus.ROOM_LIST_FOUND, response));
    }

    @Override
    @GetMapping("/{roomId}")
    public ResponseEntity<CustomResponse<RoomDetailResponse>> getRoom(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @PathVariable Long roomId
    ) {
        RoomDetailResponse response = roomService.getRoom(customUserDetails.getUserId(), roomId);

        return ResponseEntity.status(RoomSuccessStatus.ROOM_DETAIL_FOUND.getHttpStatus())
            .body(CustomResponse.onSuccess(RoomSuccessStatus.ROOM_DETAIL_FOUND, response));
    }

    @Override
    @PostMapping
    public ResponseEntity<CustomResponse<RoomCreateResponse>> createRoom(
        @AuthenticationPrincipal CustomUserDetails customUserDetails, @RequestBody RoomCreateRequest request
    ) {
        RoomCreateResponse response = roomService.createRoom(customUserDetails.getUserId(), request);

        return ResponseEntity.status(RoomSuccessStatus.ROOM_CREATED.getHttpStatus())
            .body(CustomResponse.onSuccess(RoomSuccessStatus.ROOM_CREATED, response));
    }

    @Override
    @PatchMapping(value = "/{roomId}", consumes = "multipart/form-data")
    public ResponseEntity<CustomResponse<RoomUpdateResponse>> updateRoom(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @PathVariable Long roomId,
        @ModelAttribute RoomUpdateRequest request
    ) {
        RoomUpdateResponse response = roomService.updateRoom(customUserDetails.getUserId(), roomId, request);

        return ResponseEntity.status(RoomSuccessStatus.ROOM_UPDATED.getHttpStatus())
            .body(CustomResponse.onSuccess(RoomSuccessStatus.ROOM_UPDATED, response));
    }

    @Override
    @DeleteMapping("/{roomId}")
    public ResponseEntity<CustomResponse<Void>> deleteRoom(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @PathVariable Long roomId
    ) {
        roomService.deleteRoom(customUserDetails.getUserId(), roomId);

        return ResponseEntity.status(RoomSuccessStatus.ROOM_DELETED.getHttpStatus())
            .body(CustomResponse.onSuccess(RoomSuccessStatus.ROOM_DELETED, null));
    }

    @Override
    @PostMapping("/{roomId}/kick")
    public ResponseEntity<CustomResponse<Void>> kickMember(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @PathVariable Long roomId,
        @Valid @RequestBody RoomKickRequest request
    ) {
        roomService.kickMember(customUserDetails.getUserId(), roomId, request);

        return ResponseEntity.status(RoomSuccessStatus.ROOM_MEMBER_KICKED.getHttpStatus())
            .body(CustomResponse.onSuccess(RoomSuccessStatus.ROOM_MEMBER_KICKED, null));
    }
}
