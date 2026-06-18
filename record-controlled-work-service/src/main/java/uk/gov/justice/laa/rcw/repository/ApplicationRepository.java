package uk.gov.justice.laa.rcw.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.laa.rcw.entity.ItemEntity;

/** Repository for managing item entities. */
@Repository
public interface ApplicationRepository extends JpaRepository<ItemEntity, Long> {}
