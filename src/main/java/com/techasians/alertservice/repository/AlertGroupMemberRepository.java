package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.AlertGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertGroupMemberRepository extends JpaRepository<AlertGroupMember, Long> {

    @Modifying
    @Query("DELETE FROM AlertGroupMember a WHERE a.alertGroup.id = :alertGroupId")
    void deleteByAlertGroupId(Integer alertGroupId);

    @Query("SELECT m FROM AlertGroupMember m JOIN FETCH m.alertUser WHERE m.alertGroup.id = :alertGroupId")
    List<AlertGroupMember> findByAlertGroupId(Integer alertGroupId);
}
