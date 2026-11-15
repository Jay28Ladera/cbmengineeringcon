package com.asmsi.admin_system.inventory;

import com.asmsi.admin_system.entity.JobOrder;
import com.asmsi.admin_system.repository.JobOrderRepository;
import org.springframework.stereotype.Component;

import java.util.*;

/** Names for the ids stored on purchasing and inventory records (suppliers, materials, job orders). */
@Component
public class Lookups {

    public record JobRef(Long id, String number, String project, String status) {
        public String getLabel() { return number + (project == null || project.isBlank() ? "" : " · " + project); }
        public String label() { return getLabel(); }
    }

    private final JobOrderRepository jobOrders;
    private final SupplierRepository suppliers;
    private final MaterialRepository materials;
    private final MaterialCategoryRepository categories;

    public Lookups(JobOrderRepository jobOrders, SupplierRepository suppliers, MaterialRepository materials, MaterialCategoryRepository categories) {
        this.jobOrders = jobOrders; this.suppliers = suppliers; this.materials = materials; this.categories = categories;
    }

    public List<JobRef> jobOrders() {
        List<JobRef> out = new ArrayList<>();
        for (JobOrder j : jobOrders.findAllByOrderByCreatedAtDesc()) {
            String project = j.getQuotation() != null && j.getQuotation().getInquiry() != null ? j.getQuotation().getInquiry().getProjectName() : null;
            out.add(new JobRef(j.getId(), j.getJobOrderNumber() == null ? "JO #" + j.getId() : j.getJobOrderNumber(), project, j.getStatus()));
        }
        return out;
    }

    /** Job orders still being worked on (for pick lists). */
    public List<JobRef> openJobOrders() {
        return jobOrders().stream().filter(j -> !"COMPLETED".equals(j.status()) && !"CANCELLED".equals(j.status())).toList();
    }

    public Map<Long, JobRef> jobMap() { Map<Long, JobRef> m = new HashMap<>(); for (JobRef j : jobOrders()) m.put(j.id(), j); return m; }
    public Map<Long, Supplier> supplierMap() { Map<Long, Supplier> m = new HashMap<>(); for (Supplier s : suppliers.findAll()) m.put(s.id, s); return m; }
    public Map<Long, Material> materialMap() { Map<Long, Material> m = new HashMap<>(); for (Material x : materials.findAll()) m.put(x.id, x); return m; }
    public Map<Long, String> categoryMap() { Map<Long, String> m = new HashMap<>(); for (MaterialCategory c : categories.findAll()) m.put(c.id, c.name); return m; }
}
