package com.example.Release_Assistant_Backend.repo;


import com.example.Release_Assistant_Backend.entity.Release;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReleaseRepo extends JpaRepository<Release, Long> {
    boolean existsByVersion(String version);
    List<Release> findAllByOrderByIdDesc();
    Optional<Release> findFirstByIdLessThanOrderByIdDesc(Long id);
}