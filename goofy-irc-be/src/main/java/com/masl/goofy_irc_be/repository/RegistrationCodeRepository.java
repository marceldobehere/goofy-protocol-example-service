package com.masl.goofy_irc_be.repository;

import com.masl.goofy_irc_be.entity.RegistrationCode;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Transactional
@Repository
public interface RegistrationCodeRepository extends JpaRepository<RegistrationCode, String> {
    RegistrationCode findByCodeAndUsedAtIsNull(String code);
    List<RegistrationCode> findAllByUsedAtIsNotNull();
    List<RegistrationCode> findAllByUsedAtIsNull();
    void deleteByCode(String code);
}
