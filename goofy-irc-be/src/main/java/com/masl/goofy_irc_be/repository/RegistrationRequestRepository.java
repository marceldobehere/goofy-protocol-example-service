package com.masl.goofy_irc_be.repository;

import com.masl.goofy_irc_be.entity.RegistrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {
    List<RegistrationRequest> findAllByResolvedAtIsNull();
}
