package com.mangateca.api.identity.web;

import com.mangateca.api.identity.domain.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

  UserResponse toResponse(User user);
}
