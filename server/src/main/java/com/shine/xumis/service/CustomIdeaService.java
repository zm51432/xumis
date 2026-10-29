package com.shine.xumis.service;

import com.shine.xumis.model.CustomIdea;
import com.shine.xumis.repository.CustomIdeaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomIdeaService {

    private final CustomIdeaRepository repository;

    public CustomIdeaService(CustomIdeaRepository repository) {
        this.repository = repository;
    }

    public CustomIdea save(CustomIdea idea) {
        return repository.save(idea);
    }

    public List<CustomIdea> findAll() {
        return repository.findAll();
    }

    public long countAll() {
        return repository.count();
    }

    public CustomIdea findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
