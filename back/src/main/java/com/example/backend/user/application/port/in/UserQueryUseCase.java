package com.example.backend.user.application.port.in;

import com.example.backend.shared.domain.PageQuery;
import com.example.backend.shared.domain.PageResult;
import com.example.backend.user.domain.model.User;
import java.util.UUID;

public interface UserQueryUseCase {

    User findById(UUID id);

    PageResult<User> search(String search, PageQuery page);
}
