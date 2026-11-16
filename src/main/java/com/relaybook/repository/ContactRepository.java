package com.relaybook.repository;

import com.relaybook.model.Contact;

import java.util.List;
import java.util.Optional;

/** Persistence abstraction: both the in-memory demo and MySQL implement this contract. */
public interface ContactRepository {
    List<Contact> findAll();
    Optional<Contact> findById(String id);
    void insert(Contact contact);
    void update(Contact contact);
    boolean delete(String id);
}
