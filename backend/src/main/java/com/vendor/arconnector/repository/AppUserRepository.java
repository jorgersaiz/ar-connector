package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("select u from AppUser u left join fetch u.person where u.email = :email")
    Optional<AppUser> findByEmailWithPerson(@Param("email") String email);

    @Query("select u from AppUser u left join fetch u.person order by u.displayName")
    List<AppUser> findAllWithPerson();
}
