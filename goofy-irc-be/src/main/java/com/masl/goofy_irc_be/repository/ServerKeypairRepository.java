package com.masl.goofy_irc_be.repository;

import com.masl.goofy_irc_be.entity.ServerKeypair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServerKeypairRepository extends JpaRepository<ServerKeypair, String> {
}
