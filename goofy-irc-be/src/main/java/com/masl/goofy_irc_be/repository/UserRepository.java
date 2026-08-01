package com.masl.goofy_irc_be.repository;

import com.masl.goofy_irc_be.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Transactional
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    User findByHandle(String handle);
    void deleteByHandle(String handle);
}
