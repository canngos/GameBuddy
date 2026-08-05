package com.gamebuddy.community.infrastructure.repository;

import com.gamebuddy.community.infrastructure.entity.Comment;
import com.gamebuddy.community.infrastructure.entity.Post;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findAllByPostOrderByCreatedDateAsc(Post post);
}
