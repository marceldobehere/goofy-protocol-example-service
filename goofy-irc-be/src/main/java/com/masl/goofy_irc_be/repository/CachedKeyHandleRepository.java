package com.masl.goofy_irc_be.repository;

import com.masl.goofy_irc_be.entity.CachedKeyHandleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CachedKeyHandleRepository extends JpaRepository<CachedKeyHandleEntry, String> {
    CachedKeyHandleEntry findByHandle(String handle);
}
