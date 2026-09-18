package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.Ebook;
import com.lordsai.lsi.entity.enums.EbookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EbookRepository extends JpaRepository<Ebook, Long> {

    Optional<Ebook> findByEbookCodeIgnoreCase(String ebookCode);

    boolean existsByEbookCodeIgnoreCase(String ebookCode);

    boolean existsByTitleIgnoreCase(String title);

    boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);

    List<Ebook> findByStatusOrderByDisplayOrderAscTitleAsc(EbookStatus status);

    List<Ebook> findAllByOrderByDisplayOrderAscTitleAsc();

    long countByStatus(EbookStatus status);
}
