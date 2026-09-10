package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByUid(String uid);
    List<Person> findByUidIn(Collection<String> uids);
    List<Person> findByActiveTrue();
    List<Person> findAllByOrderByDisplayNameAsc();
}
