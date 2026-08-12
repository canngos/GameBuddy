package com.gamebuddy.match.infrastructure.repository;

import com.gamebuddy.match.infrastructure.entity.UnlockedAdmirer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UnlockedAdmirerRepository extends JpaRepository<UnlockedAdmirer, UnlockedAdmirer.Key> {

    /**
     * Everyone this gamer has paid to see.
     *
     * <p>Ids rather than entities: the admirers screen needs to know which of the people it
     * is already holding are revealed, not to load them a second time.
     */
    @Query("select u.admirerId from UnlockedAdmirer u where u.userId = :userId")
    List<String> findAdmirerIds(@Param("userId") String userId);

    boolean existsByUserIdAndAdmirerId(String userId, String admirerId);
}
