package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.StudentDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentDeviceRepository extends JpaRepository<StudentDevice, Long> {

    Optional<StudentDevice> findByUserId(Long userId);

    Optional<StudentDevice> findByDeviceId(String deviceId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}

