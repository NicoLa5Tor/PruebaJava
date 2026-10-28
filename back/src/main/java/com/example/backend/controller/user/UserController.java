package com.example.backend.controller.user;

import com.example.backend.dto.common.PageResponse;
import com.example.backend.dto.user.CreateUserRequest;
import com.example.backend.dto.user.UpdateProfileRequest;
import com.example.backend.dto.user.UpdateUserRequest;
import com.example.backend.dto.user.UserResponse;
import com.example.backend.service.auth.LocalJwtService;
import com.example.backend.service.user.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal LocalJwtService.LocalPrincipal principal) {
        return userService.findById(principal.userId());
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse updateMe(@AuthenticationPrincipal LocalJwtService.LocalPrincipal principal,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.userId(), request);
    }

    @GetMapping
    public PageResponse<UserResponse> findAll(@RequestParam(required = false) String search,
                                              @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.findAll(search, pageable);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.id())
                .toUri();
        return ResponseEntity.created(location).body(user);
    }

    @PutMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal LocalJwtService.LocalPrincipal principal,
                               @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request, principal.userId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal LocalJwtService.LocalPrincipal principal,
                                       @PathVariable UUID id) {
        userService.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
