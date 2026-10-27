package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.SuccessStory;
import com.lordsai.lsi.entity.enums.StoryCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuccessStoryRepository extends JpaRepository<SuccessStory, Long> {

    List<SuccessStory> findAllByOrderByDisplayOrderAscIdAsc();

    List<SuccessStory> findByPublishedTrueOrderByDisplayOrderAscIdAsc();

    List<SuccessStory> findByPublishedTrueAndCategoryOrderByDisplayOrderAscIdAsc(StoryCategory category);

    Optional<SuccessStory> findByIdAndPublishedTrue(Long id);

    long countByPublishedTrue();
}
