// src/main/java/com/asmsi/admin_system/repository/MessageRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
           SELECT m FROM Message m
           WHERE (m.sender = :u1 AND m.recipient = :u2)
              OR (m.sender = :u2 AND m.recipient = :u1)
           ORDER BY m.timestamp ASC
           """)
    List<Message> findConversation(@Param("u1") String user1,
                                   @Param("u2") String user2);

    long countByRecipientAndSeenFalse(String recipient);

    @Modifying
    @Query("UPDATE Message m SET m.seen = true " +
           "WHERE m.sender = :sender AND m.recipient = :recipient AND m.seen = false")
    void markAsSeen(@Param("sender") String sender,
                    @Param("recipient") String recipient);


                    @Query("""
       select count(m)
       from Message m
       where m.recipient = :username
         and m.seen = false
       """)
long countUnreadForUser(@Param("username") String username);


                    
}