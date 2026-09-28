package com.ringout.api.room.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.room.controller.docs.RoomControllerApi;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.service.RoomService;
import com.ringout.api.room.status.RoomSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @PostMapping
    public ResponseEntity<CustomResponse<RoomCreateResponse>> createRoom(
        @AuthenticationPrincipal CustomUserDetails customUserDetails, @RequestBody RoomCreateRequest request
    ) {
        RoomCreateResponse response = roomService.createRoom(customUserDetails.getUserId(), request);

        return ResponseEntity.status(RoomSuccessStatus.ROOM_CREATED.getHttpStatus())
            .body(CustomResponse.onSuccess(RoomSuccessStatus.ROOM_CREATED, response));
    }
}
