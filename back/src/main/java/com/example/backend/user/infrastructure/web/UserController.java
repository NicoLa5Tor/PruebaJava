package com.example.backend.user.infrastructure.web;

import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.infrastructure.security.AuthenticatedUser;
import com.example.backend.shared.infrastructure.web.PageResponse;
import com.example.backend.user.application.port.in.UserCommandUseCase;
import com.example.backend.user.application.port.in.UserQueryUseCase;
import com.example.backend.user.infrastructure.web.dto.UserDtos.ChangePasswordRequest;
import com.example.backend.user.infrastructure.web.dto.UserDtos.CreateUserRequest;
import com.example.backend.user.infrastructure.web.dto.UserDtos.UpdateProfileRequest;
import com.example.backend.user.infrastructure.web.dto.UserDtos.UpdateUserRequest;
import com.example.backend.user.infrastructure.web.dto.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserQueryUseCase userQuery;
    private final UserCommandUseCase userCommands;

    public UserController(UserQueryUseCase userQuery, UserCommandUseCase userCommands) {
        this.userQuery = userQuery;
        this.userCommands = userCommands;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(userQuery.findById(principal.userId()));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(userCommands.updateProfile(principal.userId(), request.toCommand()));
    }

    @GetMapping
    public PageResponse<UserResponse> findAll(@RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(userQuery.search(search, PageQuery.of(page, size)), UserResponse::from);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable UUID id) {
        return UserResponse.from(userQuery.findById(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = UserResponse.from(userCommands.create(request.toCommand()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.id())
                .toUri();
        return ResponseEntity.created(location).body(user);
    }

    @PutMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
                               @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(userCommands.update(id, request.toCommand(), principal.userId()));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(@PathVariable UUID id, @Valid @RequestBody ChangePasswordRequest request) {
        userCommands.changePassword(id, request.password());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        userCommands.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
