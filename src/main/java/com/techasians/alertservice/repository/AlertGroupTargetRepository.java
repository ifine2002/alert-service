package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.AlertGroupTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertGroupTargetRepository extends JpaRepository<AlertGroupTarget, Integer> {

    List<AlertGroupTarget> findByAlertGroupId(Integer alertGroupId);

    void deleteByAlertGroupId(Integer alertGroupId);
    
    List<AlertGroupTarget> findByAlertGroupIdIn(List<Integer> alertGroupIds);
}
