package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.DoubtReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoubtReplyRepository extends JpaRepository<DoubtReply, Long> {

    List<DoubtReply> findByDoubtIdOrderByCreatedAtAsc(Long doubtId);
}
