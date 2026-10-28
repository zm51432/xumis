package com.shine.xumis.service;

import com.shine.xumis.model.Inquiry;
import com.shine.xumis.repository.InquiryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InquiryService {

    private final InquiryRepository repository;

    public InquiryService(InquiryRepository repository) {
        this.repository = repository;
    }

    public Inquiry save(Inquiry inquiry) {
        return repository.save(inquiry);
    }

    public List<Inquiry> findAll() {
        return repository.findAll();
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
