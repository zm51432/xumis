package com.shine.xumis.repository;

import com.shine.xumis.model.CustomIdea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomIdeaRepository extends JpaRepository<CustomIdea, Long> {
}
