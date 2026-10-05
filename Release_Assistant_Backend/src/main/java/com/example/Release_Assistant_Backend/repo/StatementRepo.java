package com.example.Release_Assistant_Backend.repo;

import com.example.Release_Assistant_Backend.entity.BriefStatement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementRepo extends JpaRepository<BriefStatement, Long> {}