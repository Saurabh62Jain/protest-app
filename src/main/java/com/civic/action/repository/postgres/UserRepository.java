package com.civic.action.repository.postgres;

import com.civic.action.model.postgres.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE u.mobileNumber = :username OR u.email = :username")
    Optional<User> findByMobileNumber(@Param("username") String username);
    Optional<User> findByVoterId(String voterId);
    boolean existsByVoterId(String voterId);
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
}
