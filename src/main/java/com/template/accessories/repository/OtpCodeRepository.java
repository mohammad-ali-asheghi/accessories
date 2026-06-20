package com.template.accessories.repository;

import com.template.accessories.entity.OtpCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCodeEntity, Long> {

    Optional<OtpCodeEntity> findTopByMobileAndUsedFalseOrderByIdDesc(String mobile);
}
