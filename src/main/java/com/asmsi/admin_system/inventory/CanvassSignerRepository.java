package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CanvassSignerRepository extends JpaRepository<CanvassSigner, Long> {
    List<CanvassSigner> findByStep(String step);
    List<CanvassSigner> findByUserId(Long userId);
    boolean existsByUserIdAndStep(Long userId, String step);
    void deleteByUserIdAndStep(Long userId, String step);
}
